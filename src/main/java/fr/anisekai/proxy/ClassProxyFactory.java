package fr.anisekai.proxy;

import fr.anisekai.proxy.exceptions.ProxyAccessException;
import fr.anisekai.proxy.exceptions.ProxyCreationException;
import fr.anisekai.proxy.exceptions.ProxyInvocationException;
import fr.anisekai.proxy.interfaces.ProxyInterceptor;
import fr.anisekai.proxy.interfaces.ProxyPolicy;
import fr.anisekai.proxy.interfaces.State;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectStreamException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating state-aware proxy instances.
 * <p>
 * This class is the main entry point for the proxying feature. It efficiently creates proxies by caching the generated
 * proxy classes, ensuring high performance. Each proxy instance is associated with a unique state-tracking interceptor,
 * guaranteeing state separation.
 */
public final class ClassProxyFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassProxyFactory.class);

    /**
     * A global, static cache for the generated proxy classes. This is the expensive part we want to do only once per
     * original class across the entire application.
     */
    private static final class ProxyClassCache {

        private static final Map<Class<?>, Class<?>> CACHE = new ConcurrentHashMap<>();

        private static Class<?> get(Class<?> originalClass) {

            return CACHE.computeIfAbsent(
                    originalClass, clazz -> {
                        try {
                            return new ByteBuddy()
                                    .subclass(clazz)
                                    .implement(State.class)
                                    .defineMethod("writeReplace", Object.class, Visibility.PRIVATE)
                                    .intercept(MethodDelegation.to(WriteReplaceInterceptor.class))
                                    .method(ElementMatchers.any())
                                    .intercept(MethodDelegation.to(StaticMasterInterceptor.class))
                                    .make()
                                    .load(clazz.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                                    .getLoaded();
                        } catch (Exception e) {
                            throw new ProxyCreationException(
                                    "Failed to generate proxy class for: " + clazz.getName(),
                                    e
                            );
                        }
                    }
            );
        }

    }

    private final Map<Object, ProxyInterceptor<?>> interceptors    = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Object, Object>              proxyToInstance = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Object, State<?>>            instanceToState = Collections.synchronizedMap(new IdentityHashMap<>());
    private final ProxyPolicy                      policy;

    /**
     * Creates a new ProxyFactory with the default proxy policy.
     */
    public ClassProxyFactory() {

        this(ProxyPolicy.DEFAULT);
    }

    /**
     * Creates a new ProxyFactory with a custom proxy policy.
     *
     * @param policy
     *         The policy that determines which nested objects should also be proxied.
     */
    public ClassProxyFactory(ProxyPolicy policy) {

        this.policy = policy;
    }

    /**
     * Creates a new stateful proxy for the given object instance.
     *
     * @param instance
     *         The object to proxy. Must not be null.
     * @param <T>
     *         The type of the object.
     *
     * @return A {@link State} instance that wraps the original object and tracks its changes.
     *
     * @throws ProxyCreationException
     *         if the proxy cannot be created.
     */
    @SuppressWarnings("unchecked")
    public <T> State<T> create(T instance) {

        if (this.instanceToState.containsKey(instance)) {
            LOGGER.trace(
                    "Ignoring proxying request on {}: The proxy already exists.",
                    instance.getClass().getSimpleName()
            );
            return (State<T>) this.instanceToState.get(instance);
        }

        try {
            Class<?> proxyClass = ProxyClassCache.get(instance.getClass());
            T        proxy      = (T) proxyClass.getDeclaredConstructor().newInstance();

            LOGGER.debug("Created proxy for {}", instance.getClass().getSimpleName());
            StaticMasterInterceptor.PROXY_OWNER_REGISTRY.put(proxy, this);

            ProxyInterceptor<T> interceptor = new ClassProxyImpl<>(
                    this,
                    instance,
                    proxy,
                    this.policy,
                    new IdentityHashMap<>(),
                    p -> {
                        LOGGER.debug(
                                "Unregistering proxy for {} (proxy {})",
                                p.getInstance().getClass().getSimpleName(),
                                p.getProxy().getClass().getSimpleName()
                        );
                        this.interceptors.remove(p.getProxy());
                        this.proxyToInstance.remove(p.getProxy());
                        this.instanceToState.remove(p.getInstance());
                        StaticMasterInterceptor.PROXY_OWNER_REGISTRY.remove(p.getProxy()); // Cleanup the static map to prevent leaks.
                    }
            );

            this.interceptors.put(proxy, interceptor);
            this.proxyToInstance.put(proxy, instance);
            this.instanceToState.put(instance, interceptor);
            return interceptor;

        } catch (Exception e) {
            throw new ProxyCreationException("Failed to create proxy for class: " + instance.getClass().getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> ProxyInterceptor<T> findInterceptor(T proxy) {

        return (ProxyInterceptor<T>) this.interceptors.get(proxy);
    }

    @NotNull
    private <T> ProxyInterceptor<T> getInterceptor(T proxy) {

        ProxyInterceptor<T> interceptor = this.findInterceptor(proxy);
        if (interceptor == null) {
            throw new ProxyAccessException("Orphan Proxy: Unable to find the proxy's interceptor.");
        }
        return interceptor;
    }

    /**
     * Retrieve the {@link State} associated to the provided proxy.
     *
     * @param proxy
     *         The proxy from which the {@link State} should be retrieved.
     * @param <T>
     *         Type of the proxy class
     *
     * @return A possibly {@code null} {@link State}.
     */
    @Nullable
    public <T> State<T> getState(T proxy) {

        if (proxy instanceof State<?>) {
            return this.findInterceptor(proxy);
        }

        return null; // not a proxy
    }

    /**
     * The instance-based interceptor method. This is called by the StaticMasterInterceptor after it has looked up the
     * correct factory instance.
     */
    @RuntimeType
    public Object intercept(@This Object self, @Origin Method method, @AllArguments Object[] inputArgs) {

        Object[] args = inputArgs == null ? new Object[0] : inputArgs;

        try {
            String methodName = method.getName();
            int    argCount   = method.getParameterCount();

            if (argCount == 0 && "hashCode".equals(methodName)) {
                return this.getInterceptor(self).getInstance().hashCode();
            }
            if (argCount == 0 && "toString".equals(methodName)) {
                return this.getInterceptor(self).getInstance().toString();
            }
            if (argCount == 1 && "equals".equals(methodName)) {
                Object selfInstance  = this.getInterceptor(self).getInstance();
                Object otherArg      = args[0];
                Object otherInstance = this.proxyToInstance.getOrDefault(otherArg, otherArg);
                return selfInstance.equals(otherInstance);
            }

            LOGGER.trace("Intercepting '{}()' on {}", method.getName(), self.getClass().getSimpleName());
            return this.getInterceptor(self).intercept(method, args);
        } catch (Exception e) {
            throw new ProxyInvocationException(
                    String.format("Unable to invoke '%s' on '%s'", method.getName(), self.getClass().getName()),
                    e
            );
        }
    }

    /**
     * Clears all caches and releases all proxy instances created by *this* factory. It also ensures that its proxies
     * are removed from the global registry.
     */
    public void close() {

        for (ProxyInterceptor<?> interceptor : List.copyOf(this.interceptors.values())) {
            interceptor.close();
        }
        this.interceptors.clear();
        this.proxyToInstance.clear();
        this.instanceToState.clear();
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
                // This can happen if the proxy is being serialized after its context has been closed.
                // It's safest to throw an exception or return null if that's acceptable.
                // For caching, we want the raw data, but the proxy is effectively an orphan.
                // A more robust but complex solution would be to get the 'instance' from a weak map.
                // For now, we assume the proxy is alive.
                throw new IllegalStateException("Cannot serialize an orphan proxy.");
            }

            // Get the state handler for this proxy and return its raw, underlying instance.
            State<?> state = owner.getState(self);
            if (state != null) {
                return state.getInstance();
            }

            throw new IllegalStateException("Cannot serialize proxy without a valid state.");
        }

    }

}
