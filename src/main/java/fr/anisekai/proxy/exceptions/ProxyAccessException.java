package fr.anisekai.proxy.exceptions;

/**
 * Specific subclass of {@link ProxyException} used when a proxy is used without any interceptor available.
 */
public class ProxyAccessException extends ProxyException {

    /**
     * Create a new {@link ProxyAccessException}.
     *
     * @param message
     *         The exception detail message.
     */
    public ProxyAccessException(String message) {

        super(message);
    }

}
