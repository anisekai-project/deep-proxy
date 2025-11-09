package fr.anisekai.proxy.exceptions;

/**
 * Specific subclass of {@link ProxyException} used when an exception occur when intercepting a proxy call.
 */
public class ProxyInvocationException extends ProxyException {

    /**
     * Create a new {@link ProxyInvocationException}.
     *
     * @param message
     *         The exception detail message.
     * @param cause
     *         The {@link Throwable} causing the {@link ProxyCreationException}
     */
    public ProxyInvocationException(String message, Throwable cause) {

        super(message, cause);
    }

}
