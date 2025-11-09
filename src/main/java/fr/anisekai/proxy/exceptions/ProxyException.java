package fr.anisekai.proxy.exceptions;

/**
 * A generic exception class used when something goes wrong with proxying
 */
public class ProxyException extends RuntimeException {

    /**
     * Create a new {@link ProxyException}
     *
     * @param message
     *         The exception detail message.
     */
    public ProxyException(String message) {

        super(message);
    }

    /**
     * Create a new {@link ProxyException}.
     *
     * @param message
     *         The exception detail message.
     * @param cause
     *         The {@link Throwable} causing the {@link ProxyException}
     */
    public ProxyException(String message, Throwable cause) {

        super(message, cause);
    }

}
