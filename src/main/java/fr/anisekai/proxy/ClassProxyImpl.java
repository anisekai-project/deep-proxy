package fr.anisekai.proxy;

import fr.anisekai.proxy.interfaces.Dirtyable;
import fr.anisekai.proxy.interfaces.ProxyInterceptor;
import fr.anisekai.proxy.interfaces.State;
import fr.anisekai.proxy.reflection.LinkedProperty;
import fr.anisekai.proxy.reflection.Properties;
import fr.anisekai.proxy.reflection.Property;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

/**
 * A simplified implementation of a state-tracking interceptor. It uses a "Source vs Patches" strategy to manage object
 * state.
 *
 * @param <S>
 *         The type of the proxied instance.
 */
public class ClassProxyImpl<S> implements ProxyInterceptor<S> {

    private       S                           instance;
    private final S                           proxy;
    private final ClassProxyFactory           factory;
    private final Consumer<ClassProxyImpl<S>> onClose;

    private final Map<Method, Property> methodLookup = new HashMap<>();
    private final Map<Property, Object> source       = new HashMap<>();
    private final Map<Property, Object> patches      = new HashMap<>();

    /**
     * Creates a new ProxyObject.
     *
     * @param factory
     *         The central factory managing the proxy lifecycle.
     * @param instance
     *         The real object.
     * @param proxy
     *         The generated proxy instance.
     * @param onClose
     *         Callback to unregister from the factory.
     */
    public ClassProxyImpl(ClassProxyFactory factory, S instance, S proxy, Consumer<ClassProxyImpl<S>> onClose) {

        this.factory  = factory;
        this.instance = instance;
        this.proxy    = proxy;
        this.onClose  = onClose;

        for (LinkedProperty prop : Properties.getPropertiesOf(instance)) {
            try {
                Object value = prop.getValue();
                this.source.put(prop, value);
                this.methodLookup.put(prop.getGetter(), prop);
                this.methodLookup.put(prop.getSetter(), prop);
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize proxy state", e);
            }
        }
    }

    @Override
    public Object intercept(Method method, Object[] args) throws Exception {

        if (this.isObjectOverride(method)) {
            return this.handleObjectMethod(method, args);
        }

        if (method.getDeclaringClass().isAssignableFrom(this.getClass())) {
            return method.invoke(this, args);
        }

        Property property = this.methodLookup.get(method);
        if (property != null) {
            if (property.getGetter().equals(method)) {
                return this.get(property);
            }
            if (property.getSetter().equals(method) && args != null && args.length == 1) {
                this.set(property, args[0]);
                return null;
            }
        }

        return method.invoke(this.instance, args);
    }

    private Object get(Property property) {

        Object value = this.patches.getOrDefault(property, this.source.get(property));
        return this.factory.wrapIfNecessary(property, value);
    }

    private void set(Property property, Object newValue) throws Exception {

        Object unproxiedValue = this.factory.unwrap(newValue);
        Object oldValue       = this.source.get(property);

        property.getSetter().invoke(this.instance, unproxiedValue);

        boolean isChanged;
        if (unproxiedValue instanceof Collection || unproxiedValue instanceof Map) {
            isChanged = (oldValue != unproxiedValue);
        } else {
            isChanged = !Objects.equals(oldValue, unproxiedValue);
        }

        if (isChanged) {
            this.patches.put(property, newValue);
        } else {
            this.patches.remove(property);
        }
    }


    @Override
    public boolean isDirty() {

        if (!this.patches.isEmpty()) {
            return true;
        }

        for (Map.Entry<Property, Object> entry : this.source.entrySet()) {
            Property property = entry.getKey();
            if (this.patches.containsKey(property)) continue;

            Object value = entry.getValue();
            if (value == null) continue;

            State<?> childState = this.factory.getExistingState(value);

            if (childState != null && childState.isDirty()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void revert() {

        this.patches.clear();
        this.source.forEach((prop, originalValue) -> {
            try {
                prop.getSetter().invoke(this.instance, originalValue);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void close() {

        this.onClose.accept(this);
    }

    private Collection<Object> getCurrentStateValues() {

        Map<Property, Object> current = new HashMap<>(this.source);
        current.putAll(this.patches);
        return current.values();
    }

    private boolean isObjectOverride(Method method) {

        String name  = method.getName();
        int    count = method.getParameterCount();
        return (name.equals("hashCode") && count == 0) ||
                (name.equals("toString") && count == 0) ||
                (name.equals("equals") && count == 1);
    }

    private Object handleObjectMethod(Method method, Object[] args) {

        return switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(this.instance);
            case "toString" -> this.instance.toString();
            case "equals" -> {
                Object other = args[0];
                yield this.instance.equals(this.factory.unwrap(other));
            }
            default -> throw new IllegalStateException();
        };
    }

    @Override
    public S getInstance() {

        return this.instance;
    }

    @Override
    public S getProxy() {

        return this.proxy;
    }

    @Override
    public Map<Property, Object> getOriginalState() {

        return Collections.unmodifiableMap(this.source);
    }

    @Override
    public Map<Property, Object> getDifferentialState() {

        Map<Property, Object> diff = new HashMap<>(this.patches);

        for (Map.Entry<Property, Object> entry : this.source.entrySet()) {
            Property property = entry.getKey();
            if (diff.containsKey(property)) continue;

            Object value = entry.getValue();
            if (value == null) continue;

            State<?> childState = this.factory.getExistingState(value);

            if (childState != null && childState.isDirty()) {
                diff.put(property, childState.getProxy());
            }
        }

        return Collections.unmodifiableMap(diff);
    }

    void refreshInstance(S newInstance) {

        this.instance = newInstance;
        this.patches.clear();
        this.source.clear();

        for (LinkedProperty prop : Properties.getPropertiesOf(newInstance)) {
            try {
                this.source.put(prop, prop.getValue());
            } catch (Exception e) {
                throw new RuntimeException("Failed to re-baseline proxy state during refresh", e);
            }
        }
    }

}