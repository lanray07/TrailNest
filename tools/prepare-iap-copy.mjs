import fs from 'node:fs/promises';
const root = new URL('../', import.meta.url);
const terms = {
  'en-US': 'Annual subscription renews automatically unless canceled through Amazon.',
  'en-GB': 'Annual subscription renews automatically unless cancelled through Amazon.',
  'en-AU': 'Annual subscription renews automatically unless cancelled through Amazon.',
  'en-IN': 'Annual subscription renews automatically unless cancelled through Amazon.',
  fr: 'L’abonnement annuel se renouvelle automatiquement, sauf résiliation via Amazon.',
  de: 'Das Jahresabonnement verlängert sich automatisch, sofern es nicht über Amazon gekündigt wird.',
  it: 'L’abbonamento annuale si rinnova automaticamente, salvo disdetta tramite Amazon.',
  es: 'La suscripción anual se renueva automáticamente, salvo que la canceles a través de Amazon.',
  'es-MX': 'La suscripción anual se renueva automáticamente, salvo que la canceles a través de Amazon.',
  'pt-BR': 'A assinatura anual é renovada automaticamente, a menos que seja cancelada pela Amazon.',
  'pt-PT': 'A subscrição anual renova-se automaticamente, salvo cancelamento através da Amazon.',
  nl: 'Het jaarabonnement wordt automatisch verlengd, tenzij je het via Amazon opzegt.',
  pl: 'Roczna subskrypcja odnawia się automatycznie, chyba że anulujesz ją w Amazon.',
  sv: 'Årsprenumerationen förnyas automatiskt om den inte sägs upp via Amazon.',
  ja: '年間サブスクリプションは、Amazonで解約しない限り自動更新されます。',
  ko: '연간 구독은 Amazon에서 취소하지 않으면 자동으로 갱신됩니다.',
  'zh-Hans': '年度订阅会自动续订，除非通过 Amazon 取消。',
  'zh-Hant': '年度訂閱會自動續訂，除非透過 Amazon 取消。',
  ar: 'يتجدد الاشتراك السنوي تلقائيًا ما لم يتم إلغاؤه عبر Amazon.',
  hi: 'वार्षिक सदस्यता अपने आप नवीनीकृत होती है, जब तक आप इसे Amazon के माध्यम से रद्द नहीं करते।',
  tr: 'Yıllık abonelik, Amazon üzerinden iptal edilmediği sürece otomatik olarak yenilenir.',
  id: 'Langganan tahunan diperpanjang otomatis kecuali dibatalkan melalui Amazon.'
};
const registry = JSON.parse(await fs.readFile(new URL('config/locales.json', root), 'utf8'));
const copy = {};
for (const locale of registry.locales) {
  const entries = JSON.parse(await fs.readFile(new URL(`localisation/${locale.tag}.json`, root), 'utf8'));
  if (!terms[locale.tag]) throw Error(`Missing annual terms: ${locale.tag}`);
  const description = `0.8.7+: ${entries.billing_benefit.text} ${terms[locale.tag]}`;
  if (description.length > 1200 || /%\d*\$?[a-z]/i.test(description)) throw Error(`Invalid IAP copy: ${locale.tag}`);
  copy[locale.tag] = { title: 'TrailNest Premium', description, provenance: 'Agent-authored localized IAP copy under existing owner translation-review waiver; not human-approved.' };
}
await fs.writeFile(new URL('marketing/iap-localized-copy.json', root), JSON.stringify(copy, null, 2) + '\n');
console.log(`Prepared ${Object.keys(copy).length} localized IAP descriptions. No upload or pricing change.`);
