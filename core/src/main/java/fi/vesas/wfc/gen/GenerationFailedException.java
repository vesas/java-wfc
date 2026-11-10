package fi.vesas.wfc.gen;

/**
 * Signals that a pipeline pass could not produce a valid result — a WFC
 * contradiction, an unreachable region, an unsatisfiable constraint. Game code
 * should catch this at the pipeline boundary and retry with a bumped seed.
 *
 * Subclasses identify the specific failure mode for diagnostic purposes.
 */
public class GenerationFailedException extends RuntimeException {

    public GenerationFailedException(String message) {
        super(message);
    }
}
