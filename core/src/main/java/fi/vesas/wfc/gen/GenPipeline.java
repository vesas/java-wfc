package fi.vesas.wfc.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Ordered sequence of {@link GenPass}es. Game code composes a pipeline once and
 * runs it per context. Passes run in insertion order on the caller's thread.
 *
 * Cancellation is checked between passes. A pass that takes a long time should
 * also poll {@link GenContext#isCancelled()} internally at safe points.
 */
public class GenPipeline {

    private final List<GenPass> passes = new ArrayList<>();

    public GenPipeline add(GenPass pass) {
        passes.add(pass);
        return this;
    }

    public int size() {
        return passes.size();
    }

    public void run(GenContext ctx) {
        for (GenPass pass : passes) {
            if (ctx.isCancelled()) {
                return;
            }
            pass.apply(ctx);
        }
    }

    /**
     * Submit the pipeline to an executor for background execution. Typical use:
     * a LibGDX game calls this on a worker pool, polls the returned future, and
     * swaps the solved layers into the renderer once complete. Exceptions from
     * any pass (e.g. {@link GenerationFailedException}) propagate through the
     * future.
     */
    public CompletableFuture<Void> runAsync(GenContext ctx, Executor executor) {
        return CompletableFuture.runAsync(() -> run(ctx), executor);
    }
}
