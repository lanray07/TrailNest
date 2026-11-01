package com.trailnest.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MapDownloadBudgetTest {
    @Test fun sharedResourceAndByteLimitsFailClosed() {
        val budget = MapDownloadBudget(10, 2)
        assertTrue(budget.beginResource()); assertTrue(budget.consume(8))
        assertTrue(budget.beginResource()); assertTrue(budget.consume(2))
        assertFalse(budget.consume(1)); assertEquals(10, budget.bytes)
        assertFalse(budget.beginResource()); assertFalse(budget.consume(0))
    }
    @Test fun cancellationCannotRestartBudget() {
        val budget = MapDownloadBudget()
        budget.stop(); assertFalse(budget.beginResource()); assertFalse(budget.consume(1))
    }
    @Test fun concurrentSourcesCannotOverspend() {
        val budget = MapDownloadBudget(100, 100)
        val executor = Executors.newFixedThreadPool(4)
        repeat(1000) { executor.submit { if (budget.beginResource()) budget.consume(3) } }
        executor.shutdown(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertTrue(budget.bytes <= 100); assertTrue(budget.resources <= 100); assertTrue(budget.stopped)
    }
}
