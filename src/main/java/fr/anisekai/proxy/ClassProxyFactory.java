package fr.anisekai.proxy;

import fr.anisekai.proxy.exceptions.ProxyAccessException;
import fr.anisekai.proxy.exceptions.ProxyCreationException;
import fr.anisekai.proxy.exceptions.ProxyException;
import fr.anisekai.proxy.exceptions.ProxyInvocationException;
import fr.anisekai.proxy.interfaces.Dirtyable;
import fr.anisekai.proxy.interfaces.ProxyInterceptor;
import fr.anisekai.proxy.interfaces.ProxyPolicy;
import fr.anisekai.proxy.interfaces.State;
import fr.anisekai.proxy.reflection.Property;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Centralized factory and registry for state-aware proxies.
 * <p>
 * This factory manages the lifecycle of proxies, ensures that an object instance is only proxied once (referential
 * integrity), and provides utility methods for deep-wrapping and unwrapping object graphs.
 */
public final class ClassProxyFactory implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassProxyFactory.class);

    /**
     * A global, static cache for the generated proxy classes. This is the expensive part we want to do only once per
     * original class across the entire application.
     */
    private static final Map<Class<?>, Class<?>> PROXY_CLASS_CACHE = new ConcurrentHashMap<>();

    private final Map<Object, ProxyInterceptor<?>> proxyToInterceptor = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Object, State<?>>            instanceToState    = Collections.synchronizedMap(new IdentityHashMap<>());
    private final ProxyPolicy                      policy;

    public ClassProxyFactory() {

        this(ProxyPolicy.DEFAULT);
    }

    public ClassProxyFactory(ProxyPolicy policy) {

        this.policy = policy;
    }

    /**
     * Creates or retrieves a proxy for the given instance.
     *
     * @param instance
     *         The object to track.
     * @param <T>
     *         The type of the object.
     *
     * @return A {@link State} handle for the proxy.
     */
    @SuppressWarnings("unchecked")
    public <T> State<T> create(T instance) {

        if (instance == null) return null;

        return (State<T>) this.instanceToState.computeIfAbsent(instance, this::generateProxy);
    }

    /**
     * Refresh the proxy for the given object instance.
     * <p>
     * This allows keeping the same proxy reference while swapping the underlying tracked object instance. This is
     * useful when a persistence operation returns a different instance representing the same logical entity.
     *
     * @param previousInstance
     *         The instance currently being tracked.
     * @param nextInstance
     *         The new instance to be tracked by the same proxy.
     * @param <T>
     *         The type of the object.
     *
     * @throws ProxyException
     *         if the previousInstance is not currently proxied.
     */
    @SuppressWarnings("unchecked")
    public <T> void refresh(T previousInstance, T nextInstance) {

        if (previousInstance == null || nextInstance == null) {
            throw new IllegalArgumentException("Instances cannot be null during refresh.");
        }

        State<T> state = (State<T>) this.instanceToState.get(previousInstance);
        if (state == null) {
            throw new ProxyException("The provided instance is not managed by this factory.");
        }

        if (!(state instanceof ClassProxyImpl<T> interceptor)) {
            throw new ProxyException("Refresh is only supported for standard object proxies.");
        }

        this.instanceToState.remove(previousInstance);
        interceptor.refreshInstance(nextInstance);
        this.instanceToState.put(nextInstance, interceptor);

        LOGGER.debug(
                "Refreshed proxy for {} -> {}",
                previousInstance.getClass().getSimpleName(),
                nextInstance.getClass().getSimpleName()
        );
    }

    /**
     * Wraps a value in a proxy if the current {@link ProxyPolicy} requires it. This is the "Deep Proxying" engine used
     * by interceptors.
     *
     * @param property
     *         The property context.
     * @param value
     *         The value to potentially wrap.
     *
     * @return The proxied value or the original value.
     */
    public Object wrapIfNecessary(Property property, Object value) {

        if (value == null) return null;

        State<?> existingState = this.getExistingState(value);
        if (existingState != null) return existingState.getProxy();

        if (this.policy.shouldProxy(property, value)) {
            return this.create(value).getProxy();
        }

        if (this.policy.shouldProxyContainer(value)) {
            return this.createContainerProxy(property, value);
        }

        return value;
    }

    /**
     * Unwraps a potential proxy back to its original instance. This works recursively for collections.
     *
     * @param value
     *         The value to unwrap.
     *
     * @return The raw instance.
     */
    public Object unwrap(Object value) {

        return switch (value) {
            case null -> null;
            case State<?> s -> s.getInstance();
            case List<?> l -> l.stream().map(this::unwrap).toList();
            case Set<?> s -> s.stream().map(this::unwrap).collect(Collectors.toSet());
            case Map<?, ?> m -> {
                Map<Object, Object> m2 = new HashMap<>();
                m.forEach((k, v) -> m2.put(k, this.unwrap(v)));
                yield m2;
            }
            default -> value;
        };
    }

    /**
     * Entry point for ByteBuddy's static Master Interceptor.
     */
    public Object intercept(Object self, Method method, Object[] args) {

        ProxyInterceptor<?> interceptor = this.proxyToInterceptor.get(self);
        if (interceptor == null) {
            throw new ProxyAccessException("Orphan Proxy: No interceptor registered for this instance.");
        }

        try {
            return interceptor.intercept(method, args);
        } catch (Exception e) {
            throw new ProxyInvocationException("Failed to intercept " + method.getName(), e);
        }
    }

    @Override
    public void close() {
        List.copyOf(this.proxyToInterceptor.values()).forEach(State::close);
        this.proxyToInterceptor.clear();
        this.instanceToState.clear();
    }

    /**
     * Retrieves an existing state for an instance without creating a new proxy.
     */
    @Nullable
    public State<?> getExistingState(Object instance) {

        if (instance == null) return null;
        if (instance instanceof State<?> state) return state;
        return this.instanceToState.get(instance);
    }

    private State<?> generateProxy(Object instance) {

        try {
            Class<?> proxyClass = getOrGenerateByteBuddyClass(instance.getClass());
            Object   proxy      = proxyClass.getDeclaredConstructor().newInstance();

            ProxyInterceptor<Object> interceptor = new ClassProxyImpl<>(
                    this,
                    instance,
                    proxy,
                    p -> {
                        this.proxyToInterceptor.remove(p.getProxy());
                        this.instanceToState.remove(p.getInstance());
                        StaticMasterInterceptor.PROXY_OWNER_REGISTRY.remove(p.getProxy());
                    }
            );

            this.proxyToInterceptor.put(proxy, interceptor);
            StaticMasterInterceptor.PROXY_OWNER_REGISTRY.put(proxy, this);

            return interceptor;
        } catch (Exception e) {
            throw new ProxyCreationException("Could not create proxy for " + instance.getClass(), e);
        }
    }

    private Object createContainerProxy(Property property, Object container) {

        ContainerProxyHandler handler = new ContainerProxyHandler(
                this, property, container, p -> {
            this.proxyToInterceptor.remove(p.getProxy());
            this.instanceToState.remove(p.getInstance());
            StaticMasterInterceptor.PROXY_OWNER_REGISTRY.remove(p.getProxy());
        }
        );

        Object proxy = Proxy.newProxyInstance(
                Dirtyable.class.getClassLoader(),
                this.deriveInterfaces(property, container),
                handler
        );

        this.proxyToInterceptor.put(proxy, handler);
        this.instanceToState.put(container, handler);

        StaticMasterInterceptor.PROXY_OWNER_REGISTRY.put(proxy, this);
        handler.setProxy(proxy);
        return proxy;
    }

    private Class<?>[] deriveInterfaces(Property property, Object container) {

        Set<Class<?>> interfaces = new HashSet<>();
        Collections.addAll(interfaces, container.getClass().getInterfaces());
        if (property.getGetter().getReturnType().isInterface()) {
            interfaces.add(property.getGetter().getReturnType());
        }
        interfaces.add(Dirtyable.class);
        return interfaces.toArray(new Class<?>[0]);
    }

    private static Class<?> getOrGenerateByteBuddyClass(Class<?> origin) {

        return PROXY_CLASS_CACHE.computeIfAbsent(
                origin, clazz -> new ByteBuddy()
                        .subclass(clazz)
                        .implement(State.class)
                        .defineMethod("writeReplace", Object.class, Visibility.PRIVATE)
                        .intercept(MethodDelegation.to(WriteReplaceInterceptor.class))
                        .method(ElementMatchers.any())
                        .intercept(MethodDelegation.to(StaticMasterInterceptor.class))
                        .make()
                        .load(clazz.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                        .getLoaded()
        );
    }

    /**
     * Class responsible for allowing serialization of a proxy instance.
     */
    public static final class WriteReplaceInterceptor {

        private WriteReplaceInterceptor() {}

        @RuntimeType
        public static Object writeReplace(@This Object self) {
            // Find the owner factory of the proxy being serialized.
            ClassProxyFactory owner = StaticMasterInterceptor.PROXY_OWNER_REGISTRY.get(self);
            if (owner == null) {
                throw new IllegalStateException("Cannot serialize an orphan proxy.");
            }

            // Get the state handler for this proxy and return its raw, underlying instance.
            State<?> state = owner.proxyToInterceptor.get(self);
            if (state != null) {
                return state.getInstance();
            }

            throw new IllegalStateException("Cannot serialize proxy without a valid state.");
        }

    }

}
