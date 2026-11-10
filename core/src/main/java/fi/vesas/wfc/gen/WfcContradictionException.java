package fi.vesas.wfc.gen;

/**
 * Thrown by {@link WfcPass} when the underlying solver cannot produce a valid
 * assignment. Game code should catch this at the pipeline boundary and retry
 * with a different seed (typically {@code seed + 1}) or a different pass order.
 */
public class WfcContradictionException extends GenerationFailedException {

    public WfcContradictionException(String message) {
        super(message);
    }
}
