package fi.vesas.wfc.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import fi.vesas.wfc.Constraints;

public class AsyncAndCancellationTest {

    @Test
    public void runAsyncCompletesOnExecutor() throws Exception {

        GenContext ctx = new GenContext(4, 4, 1L);
        GenPass pass = c -> c.intLayer("r").fill(42);

        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Void> f = new GenPipeline().add(pass).runAsync(ctx, exec);
            f.get(1, TimeUnit.SECONDS);
            assertEquals(42, ctx.intLayer("r").get(0, 0));
            assertEquals(42, ctx.intLayer("r").get(3, 3));
        } finally {
            exec.shutdown();
        }
    }

    @Test
    public void runAsyncPropagatesExceptionThroughFuture() {

        GenContext ctx = new GenContext(2, 2, 1L);
        GenPass failing = c -> { throw new RuntimeException("boom"); };

        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Void> f = new GenPipeline().add(failing).runAsync(ctx, exec);
            CompletionException ex = assertThrows(CompletionException.class, f::join);
            assertTrue(ex.getCause() instanceof RuntimeException);
            assertEquals("boom", ex.getCause().getMessage());
        } finally {
            exec.shutdown();
        }
    }

    @Test
    public void cancelStopsLongRunningPassPromptly() throws Exception {

        GenContext ctx = new GenContext(4, 4, 1L);

        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger iterations = new AtomicInteger();

        GenPass spinner = c -> {
            started.countDown();
            while (!c.isCancelled()) {
                iterations.incrementAndGet();
                Thread.onSpinWait();
            }
        };

        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Void> f = new GenPipeline().add(spinner).runAsync(ctx, exec);
            assertTrue(started.await(1, TimeUnit.SECONDS), "spinner pass did not start");

            ctx.cancel();
            f.get(1, TimeUnit.SECONDS);  // must complete within 1s of cancellation

            assertTrue(iterations.get() > 0, "spinner should have iterated at least once");
        } finally {
            exec.shutdown();
        }
    }

    @Test
    public void wfcPassShortCircuitsWhenContextAlreadyCancelled() {

        GenContext ctx = new GenContext(20, 20, 1L);
        ctx.cancel();

        WfcPass wfc = new WfcPass("terrain", 3, new Constraints());
        wfc.apply(ctx);  // no exception, no throw

        // layer exists but was not written by the solver (all default zeros)
        assertEquals(0, ctx.intLayer("terrain").get(5, 5));
        assertEquals(0, ctx.intLayer("terrain").get(15, 15));
    }

    @Test
    public void pipelineSkipsRemainingPassesAfterCancellation() {

        GenContext ctx = new GenContext(4, 4, 1L);

        new GenPipeline()
                .add(c -> c.intLayer("a").fill(1))
                .add(c -> c.cancel())
                .add(c -> c.intLayer("b").fill(2))
                .run(ctx);

        assertEquals(1, ctx.intLayer("a").get(0, 0));
        assertTrue(!ctx.hasLayer("b"), "pass after cancel should not have run");
    }
}
