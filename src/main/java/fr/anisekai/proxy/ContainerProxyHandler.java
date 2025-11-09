package fr.anisekai.proxy;


import fr.anisekai.proxy.interfaces.Dirtyable;
import fr.anisekai.proxy.interfaces.ProxyPolicy;
import fr.anisekai.proxy.interfaces.State;
import fr.anisekai.proxy.reflection.Property;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * An {@link InvocationHandler} for proxying container objects like {@link Collection} and {@link Map}.
 * <p>
 * This handler intercepts method calls to a container to provide two main features:
 * <ol>
 *     <li><b>Change Tracking:</b> It marks the container as "dirty" if a method that modifies
 *     its structure is called (e.g., {@code add}, {@code remove}, {@code clear}).</li>
 *     <li><b>Deep Proxying:</b> It recursively proxies the elements within the container
 *     according to the provided {@link ProxyPolicy}. This allows for tracking changes not only
 *     to the container itself but also within the objects it contains.</li>
 * </ol>
 * It also handles the necessary wrapping and unwrapping of proxies when elements are added to,
 * retrieved from, or passed as arguments to the container's methods.
 */
public class ContainerProxyHandler implements InvocationHandler, Dirtyable {

    /**
     * A set of method names that are known to mutate the state of a {@link Collection} or {@link Map}. When a method
     * with one of these names is invoked, the container is marked as dirty.
     */
    private static final Collection<String> MUTATOR_METHODS = new HashSet<>(Arrays.asList(
            "add", "remove", "put", "clear", "addAll", "removeAll", "putAll", "retainAll",
            "removeIf", "replaceAll", "compute", "computeIfAbsent", "computeIfPresent", "merge", "set"
    ));

    private final ClassProxyFactory factory;


    private final Property    property;
    private final Object      originalContainer;
    private final ProxyPolicy policy;

    /**
     * The shared context for the entire graph of proxies, used to avoid circular dependencies and to reuse existing
     * proxies for the same underlying object instance.
     */
    private final Map<Object, State<?>> proxyContext;

    /**
     * A map that stores the state handlers for the elements of this container that are proxied. It maps the original
     * element instance to its {@link Dirtyable} state handler. Using {@link IdentityHashMap} is crucial to ensure
     * elements are keyed by their reference, not by {@code equals()}.
     */
    private final Map<Object, Dirtyable> elementProxies = new IdentityHashMap<>();

    private boolean isDirty = false;

    /**
     * Constructs a new proxy handler for a container object.
     * <p>
     * Upon construction, it immediately iterates through the container's initial elements and creates proxies for them
     * if the {@link ProxyPolicy} requires it.
     *
     * @param property
     *         The property representing this container in its parent object.
     * @param originalContainer
     *         The actual container instance to be proxied.
     * @param policy
     *         The policy for creating nested proxies.
     * @param proxyContext
     *         The shared proxy context for the object graph.
     */
    @SuppressWarnings("ChainOfInstanceofChecks")
    ContainerProxyHandler(ClassProxyFactory factory, Property property, Object originalContainer, ProxyPolicy policy, Map<Object, State<?>> proxyContext) {

        this.factory  = factory;
        this.property = property;

        this.originalContainer = originalContainer;
        this.policy            = policy;
        this.proxyContext      = proxyContext;

        if (originalContainer instanceof Collection) {
            ((Iterable<?>) originalContainer).forEach(this::proxyElementIfNeeded);
        } else if (originalContainer instanceof Map) {
            ((Map<?, ?>) originalContainer).values().forEach(this::proxyElementIfNeeded);
        }
    }

    /**
     * Intercepts all method calls made on the proxied container.
     * <p>
     * This method is the central hub of the proxy's logic. It:
     * <ul>
     *   <li>Handles calls to {@link Dirtyable} and standard {@code Object} methods.</li>
     *   <li>Detects calls to mutator methods (e.g., {@code add}, {@code remove}) and marks the container as dirty.</li>
     *   <li>Proxies new elements that are being added to the container.</li>
     *   <li>Unwraps any proxy arguments before passing them to the original container.</li>
     *   <li>Wraps return values (like elements from a {@code get} call or an {@code Iterator}) in proxies if necessary.</li>
     * </ul>
     *
     * @param proxy
     *         The proxy instance that the method was invoked on.
     * @param method
     *         The {@code Method} instance corresponding to the interface method invoked on the proxy instance.
     * @param args
     *         An array of objects containing the values of the arguments passed in the method invocation.
     *
     * @return The value to return from the method invocation on the proxy instance.
     *
     * @throws Throwable
     *         The exception to throw from the method invocation on the proxy instance.
     */
    @Override
    @SuppressWarnings("ChainOfInstanceofChecks")
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        String methodName = method.getName();
        if (method.getParameterCount() == 0) {
            switch (methodName) {
                case "isDirty" -> {
                    return this.isDirty();
                }
                case "hashCode" -> {
                    return this.originalContainer.hashCode();
                }
                case "toString" -> {
                    return this.originalContainer.toString();
                }
            }
        }
        if (methodName.equals("equals") && method.getParameterCount() == 1) {
            Object other = args[0];
            // Unwrap the other object if it's also a proxy
            if (Proxy.isProxyClass(other.getClass())) {
                InvocationHandler handler = Proxy.getInvocationHandler(other);
                if (handler instanceof ContainerProxyHandler) {
                    other = ((ContainerProxyHandler) handler).originalContainer;
                }
            }
            return this.originalContainer.equals(other);
        }

        if (MUTATOR_METHODS.contains(method.getName())) {
            this.isDirty = true;
            // When adding new elements, we must proxy them.
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof Collection) {
                        ((Iterable<?>) arg).forEach(this::proxyElementIfNeeded);
                    } else if (arg instanceof Map) {
                        ((Map<?, ?>) arg).values().forEach(this::proxyElementIfNeeded);
                    } else {
                        this.proxyElementIfNeeded(arg);
                    }
                }
            }
        }

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof State) {
                    args[i] = ((State<?>) args[i]).getInstance();
                } else if (Proxy.isProxyClass(args[i].getClass())) {
                    InvocationHandler handler = Proxy.getInvocationHandler(args[i]);
                    if (handler instanceof ContainerProxyHandler) {
                        args[i] = ((ContainerProxyHandler) handler).originalContainer;
                    }
                }
            }
        }

        Object result = method.invoke(this.originalContainer, args);

        if (this.elementProxies.containsKey(result)) {
            Dirtyable elementState = this.elementProxies.get(result);
            if (elementState instanceof State) {
                return ((State<?>) elementState).getProxy();
            }
        }

        if (result instanceof Iterator) {
            return this.proxyIterator((Iterator<?>) result);
        }

        return result;
    }

    private void proxyElementIfNeeded(Object element) {

        if (this.policy.shouldProxy(this.property, element) && !this.elementProxies.containsKey(element)) {
            State<?> elementState = this.proxyContext.get(element);
            if (elementState == null) {
                elementState = this.factory.create(element);
            }
            this.elementProxies.put(element, elementState);
        }
    }

    @Override
    public boolean isDirty() {

        if (this.isDirty) {
            return true;
        }
        return this.elementProxies.values().stream().anyMatch(Dirtyable::isDirty);
    }

    private Iterator<?> proxyIterator(Iterator<?> original) {

        return new Iterator<>() {
            public boolean hasNext() {return original.hasNext();}

            public Object next() {

                Object element = original.next();
                return ContainerProxyHandler.this.elementProxies.containsKey(element) ? ((State<?>) ContainerProxyHandler.this.elementProxies.get(
                        element)).getProxy() : element;
            }

            public void remove() {

                ContainerProxyHandler.this.isDirty = true;
                original.remove();
            }
        };
    }

}
