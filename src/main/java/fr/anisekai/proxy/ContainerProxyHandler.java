package fr.anisekai.proxy;

import fr.anisekai.proxy.interfaces.ProxyInterceptor;
import fr.anisekai.proxy.interfaces.State;
import fr.anisekai.proxy.reflection.Property;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

/**
 * A simplified invocation handler for collections and maps.
 * <p>
 * This handler delegates the "heavy lifting" (proxy creation and memoization) to the {@link ClassProxyFactory},
 * focusing only on structural mutation tracking and recursive dirty checks.
 */
public class ContainerProxyHandler implements InvocationHandler, State<Object>, ProxyInterceptor<Object> {

    /**
     * A set of method names that are known to mutate the state of a {@link Collection} or {@link Map}. When a method
     * with one of these names is invoked, the container is marked as dirty.
     */
    private static final Set<String> MUTATORS = Set.of(
            "add", "addAll", "remove", "removeAll", "retainAll", "clear",
            "set", "put", "putAll", "removeIf", "replaceAll", "merge",
            "compute", "computeIfAbsent", "computeIfPresent"
    );

    private final ClassProxyFactory               factory;
    private final Property                        property;
    private final Object                          originalContainer;
    private final Consumer<ContainerProxyHandler> onClose;
    private       Object                          proxy;

    private boolean structurallyDirty = false;

    /**
     * Creates a new container proxy handler.
     *
     * @param factory
     *         The central factory for unwrapping/wrapping elements.
     * @param property
     *         The property metadata for policy checks.
     * @param originalContainer
     *         The real collection or map instance.
     */
    public ContainerProxyHandler(ClassProxyFactory factory, Property property, Object originalContainer, Consumer<ContainerProxyHandler> onClose) {

        this.factory           = factory;
        this.property          = property;
        this.originalContainer = originalContainer;
        this.onClose           = onClose;
    }

    @Override
    public Object intercept(Method method, Object[] args) throws Exception {

        String name = method.getName();

        if (method.getParameterCount() == 0) {
            switch (name) {
                case "isDirty" -> {
                    return this.isDirty();
                }
                case "hashCode" -> {
                    return this.originalContainer.hashCode();
                }
                case "toString" -> {
                    return "[Proxy] " + this.originalContainer.toString();
                }
            }
        }

        if (name.equals("equals") && method.getParameterCount() == 1) {
            return this.originalContainer.equals(this.factory.unwrap(args[0]));
        }

        if (MUTATORS.contains(name)) {
            this.structurallyDirty = true;
        }

        Object[] unwrappedArgs = null;
        if (args != null) {
            unwrappedArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                unwrappedArgs[i] = this.factory.unwrap(args[i]);
            }
        }

        Object result = method.invoke(this.originalContainer, unwrappedArgs);
        return this.wrapResult(result);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        return this.intercept(method, args);
    }

    @Override
    public Object getInstance() {

        return this.originalContainer;
    }

    @Override
    public Object getProxy() {

        return this.proxy;
    }

    public void setProxy(Object proxy) {

        this.proxy = proxy;
    }

    @Override
    public Map<Property, Object> getOriginalState() {

        // Unsupported for containers, for now.
        return Map.of();
    }

    @Override
    public Map<Property, Object> getDifferentialState() {

        // Unsupported for containers, for now.
        return Map.of();
    }

    @Override
    public void revert() {

        // Unsupported for containers, for now.
    }

    @Override
    public void close() {

        this.onClose.accept(this);
    }

    @Override
    public boolean isDirty() {

        if (this.structurallyDirty) {
            return true;
        }

        if (this.originalContainer instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                State<?> state = this.factory.getExistingState(element);
                if (state != null && state.isDirty()) return true;
            }
        } else if (this.originalContainer instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                State<?> state = this.factory.getExistingState(value);
                if (state != null && state.isDirty()) return true;
            }
        }

        return false;
    }

    private Object wrapResult(Object result) {

        if (result instanceof Iterator<?> it) {
            return this.wrapIterator(it);
        }
        // General wrapping (for .get(i), .next(), etc.)
        return this.factory.wrapIfNecessary(this.property, result);
    }

    private Iterator<Object> wrapIterator(Iterator<?> original) {

        return new Iterator<>() {
            @Override
            public boolean hasNext() {return original.hasNext();}

            @Override
            public Object next() {return ContainerProxyHandler.this.wrapResult(original.next());}

            @Override
            public void remove() {

                ContainerProxyHandler.this.structurallyDirty = true;
                original.remove();
            }
        };
    }

}