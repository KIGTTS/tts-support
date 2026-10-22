package com.kigtts.engines.tts

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResidentResourceTest {
    private class Resource { var closed = 0 }
    @Test fun repeatedLeaseKeepsOneResourceAndReplacingKeyClosesPrevious() = runTest {
        val created = mutableListOf<Resource>()
        val cache = ResidentResource<String, Resource>(backgroundScope, { it.closed++ })
        fun create() = Resource().also { created += it }
        cache.use("a", ::create) { assertSame(it, cacheResource(created)) }
        cache.use("a", ::create) { assertEquals(1, created.size) }
        cache.use("b", ::create) { assertEquals(2, created.size); assertEquals(1, created.first().closed) }
        cache.release(); assertEquals(1, created.last().closed)
        cache.use("b", ::create) { assertEquals(3, created.size) }
        cache.close(); assertEquals(1, created.last().closed)
    }
    private fun cacheResource(resources: List<Resource>) = resources.last()
    @Test fun playbackActivityDelaysExpiryUntilTwoMinutesAfterActualStop() = runTest {
        val value = Resource(); val cache = ResidentResource<Unit, Resource>(backgroundScope, { it.closed++ })
        cache.setActive(true); cache.use(Unit, { value }) {}
        advanceTimeBy(300_000); runCurrent(); assertEquals(0, value.closed)
        cache.setActive(false); runCurrent(); advanceTimeBy(119_999); runCurrent(); assertEquals(0, value.closed)
        advanceTimeBy(2); runCurrent(); assertEquals(1, value.closed)
    }
    @Test fun idleExpiresAtTwoMinutesAndNextLeaseResetsDeadline() = runTest {
        val value = Resource()
        val cache = ResidentResource<Unit, Resource>(backgroundScope, { it.closed++ })
        cache.use(Unit, { value }) {}
        runCurrent(); advanceTimeBy(119_000); runCurrent(); assertEquals(0, value.closed)
        cache.use(Unit, { error("must reuse") }) {}
        runCurrent(); advanceTimeBy(119_000); runCurrent(); assertEquals(0, value.closed)
        advanceTimeBy(1001); runCurrent(); assertEquals(1, value.closed)
    }
    @Test fun activeLeaseCannotExpireAndPinnedResourceStillReleasesOnRouteChange() = runTest {
        val value = Resource()
        val cache = ResidentResource<Unit, Resource>(backgroundScope, { it.closed++ })
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val request = launch { cache.use(Unit, { value }) { started.complete(Unit); finish.await() } }
        started.await(); advanceTimeBy(300_000); runCurrent(); assertEquals(0, value.closed)
        finish.complete(Unit); request.join()
        cache.setResident(true); advanceTimeBy(300_000); runCurrent(); assertEquals(0, value.closed)
        cache.release(); assertEquals(1, value.closed)
    }
    @Test fun cancellationKeepsUsableModelButInferenceFailureDisposesIt() = runTest {
        val value = Resource(); val entered = CompletableDeferred<Unit>()
        val cache = ResidentResource<Unit, Resource>(backgroundScope, { it.closed++ })
        val request = launch { cache.use(Unit, { value }) { entered.complete(Unit); awaitCancellation() } }
        entered.await(); request.cancelAndJoin()
        cache.use(Unit, { error("must reuse after cancel") }) {}
        assertEquals(0, value.closed)
        assertTrue(runCatching { cache.use(Unit, { value }) { error("native failure") } }.isFailure)
        assertEquals(1, value.closed)
    }
    @Test fun unpinSchedulesIdleWithoutReopeningReleasedResource() = runTest {
        val value = Resource(); val cache = ResidentResource<Unit, Resource>(backgroundScope, { it.closed++ })
        cache.setResident(true); cache.use(Unit, { value }) {}
        advanceTimeBy(300_000); runCurrent(); assertEquals(0, value.closed)
        cache.setResident(false); runCurrent(); advanceTimeBy(120_001); runCurrent(); assertEquals(1, value.closed)
        cache.setResident(true); cache.setResident(false); advanceTimeBy(120_001); runCurrent(); assertEquals(1, value.closed)
    }
}
