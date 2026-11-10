package fi.vesas.wfc.gen;

/**
 * A single step in a {@link GenPipeline}. Reads layers from the context,
 * writes new layers or mutates existing ones. Stateless between runs aside
 * from its configuration — the same pass instance can be reused across
 * multiple contexts.
 */
@FunctionalInterface
public interface GenPass {

    void apply(GenContext ctx);
}
