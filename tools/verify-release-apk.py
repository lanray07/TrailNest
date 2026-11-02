"""Inspect the actual unminified release DEX and public pins; never read signing secrets."""
import base64
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def uleb(data, offset):
    value = 0
    for shift in range(0, 35, 7):
        byte = data[offset]
        offset += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, offset
    raise ValueError('Invalid DEX integer')


def build_config(data):
    if not data.startswith(b'dex\n'):
        raise ValueError('Invalid DEX header')
    u32 = lambda offset: struct.unpack_from('<I', data, offset)[0]
    strings, types, fields = u32(60), u32(68), u32(84)

    def string(index):
        start = u32(strings + 4 * index)
        _, start = uleb(data, start)
        # Requested class/field names and billing constants are ASCII in DEX MUTF-8.
        return data[start:data.index(0, start)].decode('utf-8', errors='replace')

    for i in range(u32(96)):
        entry = u32(100) + 32 * i
        if string(u32(types + 4 * u32(entry))) != 'Lcom/trailnest/app/BuildConfig;':
            continue
        cursor = u32(entry + 24)
        counts = []
        for _ in range(4):
            count, cursor = uleb(data, cursor)
            counts.append(count)
        names, field_index = [], 0
        for _ in range(counts[0]):
            delta, cursor = uleb(data, cursor)
            _, cursor = uleb(data, cursor)
            field_index += delta
            names.append(string(u32(fields + field_index * 8 + 4)))
        cursor = u32(entry + 28)
        size, cursor = uleb(data, cursor)
        result = {}
        for name in names[:size]:
            header = data[cursor]
            cursor += 1
            kind, arg = header & 31, header >> 5
            if kind == 31:
                value = bool(arg)
            elif kind == 30:
                value = None
            else:
                raw = data[cursor:cursor + arg + 1]
                cursor += arg + 1
                value = int.from_bytes(raw, 'little', signed=kind == 4)
                if kind == 23:
                    value = string(value)
                elif kind != 4:
                    raise ValueError('Unexpected BuildConfig value type')
            result[name] = value
        return result
    return None


def verify(apk):
    billing = json.loads((ROOT / 'config/billing/production.json').read_text(encoding='utf8'))
    with zipfile.ZipFile(apk) as package:
        configs = [build_config(package.read(name)) for name in package.namelist()
                   if name.startswith('classes') and name.endswith('.dex')]
        config = next((value for value in configs if value is not None), None)
        if config is None:
            raise ValueError('BuildConfig absent; this verifier requires the current unminified build')
        expected = {'APPLICATION_ID': billing['applicationId'], 'BUILD_TYPE': 'release',
                    'DEBUG': False, 'LAT_BUILD': False, 'BILLING_ENABLED': True,
                    'BILLING_ENVIRONMENT': 'production',
                    'BILLING_PARENT_SKU': billing['parentSku'],
                    'BILLING_ANNUAL_SKU': billing['annualSku'],
                    'BILLING_VERIFICATION_URL': billing['verificationUrl']}
        for name, value in expected.items():
            if config.get(name) != value:
                raise ValueError('Release configuration mismatch: ' + name)
        pin = package.read('assets/billing-verifier-public.pem').decode('ascii')
        der = base64.b64decode(''.join(line for line in pin.splitlines() if not line.startswith('---')), validate=True)
        if hashlib.sha256(der).hexdigest() != billing['publicKeySpkiSha256']:
            raise ValueError('Production verifier SPKI pin mismatch')
        for variant in ['main', 'lat']:
            other = (ROOT / f'app/src/{variant}/assets/billing-verifier-public.pem').read_text(encoding='ascii')
            if pin.split() == other.split():
                raise ValueError('Production pin reuses a test identity')
        auth = package.read('assets/AppstoreAuthenticationKey.pem')
        if auth.split() != (ROOT / 'app/src/main/assets/AppstoreAuthenticationKey.pem').read_bytes().split():
            raise ValueError('Amazon authentication public key mismatch')
    return {'version': config['VERSION_NAME'], 'versionCode': config['VERSION_CODE'],
            'bytes': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
            'compiledProductionBindingsVerified': True, 'independentPublicPinVerified': True,
            'realProductionPurchaseVerified': False}


if __name__ == '__main__':
    try:
        print(json.dumps(verify(Path(sys.argv[1]) if len(sys.argv) > 1 else
                                ROOT / 'app/build/outputs/apk/release/app-release.apk'), indent=2))
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
