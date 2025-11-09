package fr.anisekai.proxy.exceptions;

/**
 * Specific subclass of {@link ProxyException} used when a proxy could not be created.
 */
public class ProxyCreationException extends ProxyException {

    /**
     * Create a new {@link ProxyCreationException}.
     *
     * @param message
     *         The exception detail message.
     */
    public ProxyCreationException(String message) {

        super(message);
    }

    /**
     * Create a new {@link ProxyCreationException}.
     *
     * @param message
     *         The exception detail message.
     * @param cause
     *         The {@link Throwable} causing the {@link ProxyCreationException}
     */
    public ProxyCreationException(String message, Throwable cause) {

        super(message, cause);
    }

}
