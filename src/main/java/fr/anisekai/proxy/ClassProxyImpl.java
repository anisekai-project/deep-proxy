package fr.anisekai.proxy;


import fr.anisekai.proxy.exceptions.ProxyCreationException;
import fr.anisekai.proxy.interfaces.Dirtyable;
import fr.anisekai.proxy.interfaces.ProxyInterceptor;
import fr.anisekai.proxy.interfaces.ProxyPolicy;
import fr.anisekai.proxy.interfaces.State;
import fr.anisekai.proxy.reflection.LinkedProperty;
import fr.anisekai.proxy.reflection.Properties;
import fr.anisekai.proxy.reflection.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Consumer;

/**
 * A proxy that tracks changes to a JavaBean-like object.
 * <p>
 * This class creates a proxy around an instance of a class {@code S} to intercept property access (getters and
 * setters). It maintains the original state of the object and tracks any modifications made through the proxy. This
 * allows for checking if the object is "dirty" (i.e., has changed) and for reverting all changes back to the original
 * state or simply act upon specific changes.
 * <p>
 * The proxying is deep, meaning that if a property is an object that should also be proxied (according to the
 * {@link ProxyPolicy}), a proxy will be created for it as well, allowing for nested change tracking.
 * <p>
 * This implementation uses ByteBuddy to create the proxy class at runtime.
 *
 * @param <S>
 *         the type of the object being proxied.
 *
 * @see ProxyPolicy
 */
public class ClassProxyImpl<S> implements ProxyInterceptor<S> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassProxyImpl.class);

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient ClassProxyFactory factory;
    private final transient S                 instance;
    private final transient S                 proxy;

    private final transient Map<Property, Object>         originalState;
    private final transient Map<Property, Object>         differentialState;
    private final transient Map<Method, Property>         methodLookup;
    private final transient ProxyPolicy                   policy;
    private final transient Map<Object, State<?>>         proxyContext;
    private final transient Consumer<ProxyInterceptor<S>> clear;

    /**
     * A map that holds the {@link Dirtyable} state handlers for sub-proxies. These are proxies for properties of the
     * main proxied object. This allows for recursively checking the dirty state of the entire object graph.
     */
    private final transient Map<Property, Dirtyable> subProxyStates;

    /**
     * A cache for the sub-proxy instances. This avoids creating new sub-proxy instances every time a getter is called
     * for a property that should be proxied.
     */
    private final transient Map<Property, Object> subProxyCache;


    /**
     * Creates a new proxy for the given instance with a shared proxy context. This constructor is used for creating
     * nested proxies to track changes in a graph of objects and to prevent circular dependencies.
     *
     * @param instance
     *         the object instance to proxy.
     * @param policy
     *         the policy defining the proxying behavior.
     * @param proxyContext
     *         a map that serves as a context for a graph of proxies, mapping original instances to their proxy handlers
     *         to avoid cycles.
     *
     * @throws ReflectiveOperationException
     *         if there is an error during proxy creation.
     * @throws IllegalStateException
     *         if a circular dependency is detected.
     */
    ClassProxyImpl(ClassProxyFactory factory, S instance, S proxy, ProxyPolicy policy, Map<Object, State<?>> proxyContext, Consumer<ProxyInterceptor<S>> clear) throws ReflectiveOperationException {

        if (proxyContext.containsKey(instance)) {
            throw new IllegalStateException(String.format(
                    "Circular dependency detected for instance of %s",
                    instance.getClass().getName()
            ));
        }

        this.factory        = factory;
        this.instance       = instance;
        this.proxy          = proxy;
        this.policy         = policy;
        this.proxyContext   = proxyContext;
        this.clear          = clear;
        this.subProxyStates = new HashMap<>();
        this.subProxyCache  = new HashMap<>();

        Set<LinkedProperty> properties = Properties.getPropertiesOf(instance);
        this.originalState     = new HashMap<>();
        this.differentialState = new HashMap<>();
        this.methodLookup      = new HashMap<>();

        for (LinkedProperty property : properties) {
            this.originalState.put(property, property.getValue());
            this.methodLookup.put(property.getGetter(), property);
            this.methodLookup.put(property.getSetter(), property);
        }

        this.proxyContext.put(this.instance, this);
    }

    /**
     * A specialized equality check that treats collections and maps as equal only if they are the same instance. For
     * other types, it uses {@link Objects#equals(Object, Object)}.
     * <p>
     * This is to avoid costly equality checks on large collections and to ensure that if a collection is replaced with
     * a different instance (even with the same contents), it is marked as a change.
     *
     * @param newValue
     *         the new value of the property.
     * @param originalValue
     *         the original value of the property.
     *
     * @return {@code true} if the values are considered equal, {@code false} otherwise.
     */
    private static boolean strictlyEquals(Object newValue, Object originalValue) {

        boolean isEquals;
        if (newValue instanceof Collection || newValue instanceof Map || originalValue instanceof Collection || originalValue instanceof Map) {
            isEquals = (originalValue == newValue);
        } else {
            isEquals = Objects.equals(originalValue, newValue);
        }
        return isEquals;
    }

    @Override
    public Object intercept(Method method, Object[] args) throws Exception {

        String methodName = method.getName();
        int    paramCount = method.getParameterCount();

        if (methodName.equals("hashCode") && paramCount == 0) {
            return System.identityHashCode(this.instance);
        }

        if (methodName.equals("equals") && paramCount == 1) {
            Object other = args[0];

            Object otherInstance = other;
            if (other instanceof State) {
                otherInstance = ((State<?>) other).getInstance();
            }

            return this.instance.equals(otherInstance);
        }
        if (methodName.equals("toString") && paramCount == 0) {
            return this.instance.toString();
        }

        if (method.getDeclaringClass().equals(State.class) || method.getDeclaringClass().equals(Dirtyable.class)) {
            return method.invoke(this, args);
        }

        LOGGER.trace("Handling '{}()' on {}", method.getName(), this.instance.getClass().getSimpleName());

        Property property = this.methodLookup.get(method);
        if (property == null) {
            return method.invoke(this.instance, args);
        }

        if (property.getGetter().equals(method)) {
            return this.handleGetter(property, args);
        }

        if (property.getSetter().equals(method)) {
            this.handleSetter(property, args);
            return null;
        }

        return method.invoke(this.instance, args);
    }

    private Object handleGetter(Property property, Object[] args) throws ReflectiveOperationException {

        if (this.subProxyCache.containsKey(property)) {
            return this.subProxyCache.get(property);
        }

        Object value            = property.getGetter().invoke(this.instance, args);
        Object subProxyInstance = null;

        if (this.policy.shouldProxy(property, value)) {

            State<?> subProxyState = this.proxyContext.computeIfAbsent(
                    value,
                    ignore -> this.factory.create(value)
            );
            this.subProxyStates.put(property, subProxyState);
            subProxyInstance = subProxyState.getProxy();

        } else if (this.policy.shouldProxyContainer(value)) {

            Class<?> getterReturnType = property.getGetter().getReturnType();

            if (!getterReturnType.isInterface()) {
                throw new ProxyCreationException(String.format(
                        "Cannot proxy container for property '%s'. The getter's return type must be an interface (e.g., java.util.List).",
                        property.getName()
                ));
            }

            Set<Class<?>> interfacesToProxy = new HashSet<>();
            interfacesToProxy.add(getterReturnType);
            Collections.addAll(interfacesToProxy, value.getClass().getInterfaces());
            interfacesToProxy.add(Dirtyable.class);

            ContainerProxyHandler handler = new ContainerProxyHandler(
                    this.factory,
                    property,
                    value,
                    this.policy,
                    this.proxyContext
            );

            this.subProxyStates.put(property, handler);

            subProxyInstance = Proxy.newProxyInstance(
                    this.instance.getClass().getClassLoader(),
                    interfacesToProxy.toArray(new Class<?>[0]),
                    handler
            );
        }

        if (subProxyInstance != null) {
            this.subProxyCache.put(property, subProxyInstance);
            return subProxyInstance;
        }

        return value;
    }

    private void handleSetter(Property property, Object[] args) throws IllegalAccessException, InvocationTargetException {

        this.subProxyStates.remove(property);
        this.subProxyCache.remove(property);

        if (args.length == 0) {
            throw new IllegalStateException(String.format(
                    "Unable to proxy method '%s' of type '%s': No argument to use for setter.",
                    property.getSetter().getName(),
                    this.instance.getClass().getName()
            ));
        }

        Object newValue      = args[0];
        Object originalValue = this.originalState.get(property);

        if (strictlyEquals(newValue, originalValue)) {
            this.differentialState.remove(property);
        } else {
            this.differentialState.put(property, newValue);
        }

        property.getSetter().invoke(this.instance, newValue);
    }

    @Override
    public S getInstance() {return this.instance;}

    @Override
    public S getProxy() {return this.proxy;}

    @Override
    public boolean isDirty() {

        if (!this.differentialState.isEmpty()) {
            return true;
        }
        return this.subProxyStates.values().stream().anyMatch(Dirtyable::isDirty);
    }

    @Override
    public Map<Property, Object> getOriginalState() {

        //noinspection Java9CollectionFactory (Required, Map.copyOf does not allow null values)
        return Collections.unmodifiableMap(new HashMap<>(this.originalState));
    }

    @Override
    public Map<Property, Object> getDifferentialState() {

        Map<Property, Object> changes = new HashMap<>(this.differentialState);

        for (Map.Entry<Property, Dirtyable> entry : this.subProxyStates.entrySet()) {
            Property  property = entry.getKey();
            Dirtyable subState = entry.getValue();

            if (subState.isDirty() && !changes.containsKey(property)) {
                try {
                    Object currentValue = property.getGetter().invoke(this.instance);
                    changes.put(property, currentValue);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(
                            "Failed to retrieve current state for dirty property: " + property.getName(),
                            e
                    );
                }
            }
        }

        return Collections.unmodifiableMap(changes);
    }

    @Override
    public void revert() {

        this.subProxyStates.values().forEach(sub -> {
            if (sub instanceof State) {
                ((State<?>) sub).revert();
            }
        });

        for (Map.Entry<Property, Object> entry : this.getDifferentialState().entrySet()) {
            try {
                Property property      = entry.getKey();
                Object   originalValue = this.originalState.get(property);
                property.getSetter().invoke(this.instance, originalValue);
            } catch (Exception e) {
                throw new RuntimeException("Failed to revert property: " + entry.getKey().getName(), e);
            }
        }

        this.differentialState.clear();
        this.subProxyCache.clear();
        this.subProxyStates.clear();
    }

    @Override
    public void close() {

        this.clear.accept(this);
    }

}
