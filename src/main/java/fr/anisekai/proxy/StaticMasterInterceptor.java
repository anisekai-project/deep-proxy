package fr.anisekai.proxy;

import fr.anisekai.proxy.exceptions.ProxyAccessException;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A stateless, static master interceptor that is wired into every generated proxy class. Its sole purpose is to find
 * the correct {@link ClassProxyFactory} instance responsible for the proxy and delegate the method call to it.
 */
public final class StaticMasterInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaticMasterInterceptor.class);

    static final Map<Object, ClassProxyFactory> PROXY_OWNER_REGISTRY = Collections.synchronizedMap(new IdentityHashMap<>());

    private StaticMasterInterceptor() {}

    @RuntimeType
    public static Object intercept(@This Object self, @Origin Method method, @AllArguments Object[] args) {

        LOGGER.trace("Proxying '{}()' on {}", method.getName(), self.getClass().getSimpleName());

        ClassProxyFactory owner = PROXY_OWNER_REGISTRY.get(self);

        if (owner == null) {
            throw new ProxyAccessException("Orphan Proxy: Unable to find the proxy's factory.");
        }

        return owner.intercept(self, method, args);
    }

}
