package fr.anisekai.proxy.interfaces;

import java.lang.reflect.Method;

/**
 * Specific interface marking a {@link State} as an interceptor for a proxy.
 *
 * @param <T>
 *         The type of the proxy class.
 */
public interface ProxyInterceptor<T> extends State<T> {

    /**
     * Intercepts all method calls on the proxy object.
     * <p>
     * This method is the core of the proxying mechanism. It distinguishes between:
     * <ul>
     *     <li>Object methods like {@code hashCode}, {@code equals}, and {@code toString}, which are delegated to the original instance.</li>
     *     <li>Methods from the {@link State} or {@link Dirtyable} interfaces, which are handled by this proxy handler itself.</li>
     *     <li>Getters and setters of properties, which are handled by each {@link ProxyInterceptor}.</li>
     *     <li>Any other methods, which are invoked directly on the original instance.</li>
     * </ul>
     *
     * @param method
     *         the intercepted method.
     * @param args
     *         the arguments passed to the method.
     *
     * @return the result of the method invocation.
     *
     * @throws Exception
     *         if the underlying method invocation throws an exception.
     */
    Object intercept(Method method, Object[] args) throws Exception;

}
