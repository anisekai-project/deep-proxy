package fr.anisekai.proxy.interfaces;

import fr.anisekai.proxy.ClassProxyImpl;
import fr.anisekai.proxy.ContainerProxyHandler;
import fr.anisekai.proxy.reflection.Property;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Defines a strategy for determining how and if objects should be proxied for change tracking.
 * <p>
 * Implementations of this interface provide the core logic that the proxying mechanism uses to decide:
 * <ol>
 *     <li>Whether a given object should be wrapped in a state-tracking proxy (a "deep" proxy).</li>
 *     <li>Whether a given object is a container (like a {@link List} or {@link Map}) whose
 *     contents should be tracked.</li>
 * </ol>
 * This allows for fine-grained control over the proxying process, enabling developers to include or
 * exclude certain types, packages, or properties from change tracking.
 */
public interface ProxyPolicy {

    /**
     * A default, general-purpose proxy policy.
     * <p>
     * This policy is designed to be a sensible default for most applications. It makes the following decisions:
     * <ul>
     *     <li>It <b>proxies</b> most user-defined objects.</li>
     *     <li>It <b>does not proxy</b> {@code null} values, primitives, enums, or arrays.</li>
     *     <li>It <b>does not proxy</b> common JDK value types from packages like {@code java.lang},
     *     {@code java.time}, and {@code java.math} (e.g., {@link String}, {@link java.time.LocalDate}).</li>
     *     <li>It identifies standard {@link List}, {@link Map}, and {@link Set} instances as containers
     *     to be handled by {@link #shouldProxyContainer(Object)}, preventing them from being proxied as
     *     regular objects.</li>
     * </ul>
     * The general heuristic is to proxy objects that are likely to be mutable domain entities while
     * skipping immutable value types and standard Java utilities.
     */
    ProxyPolicy DEFAULT = new ProxyPolicy() {
        @Override
        public boolean shouldProxy(Property property, Object object) {

            if (object == null) {
                return false;
            }

            Class<?> type = object.getClass();

            // Primitives, enums, and arrays are not proxyable as stateful objects.
            if (type.isPrimitive() || type.isEnum() || type.isArray()) {
                return false;
            }

            // Containers are handled by a different mechanism, so they should not be proxied as standard objects.
            if (this.shouldProxyContainer(object)) {
                return false;
            }

            // Skip common immutable or value-based JDK classes.
            String pkg = type.getPackage().getName();

            if (pkg.startsWith("java.lang") || pkg.startsWith("java.time") || pkg.startsWith("java.math")) {
                return false;
            }
            // java.util contains collections (already handled) and other utilities that are not typically stateful domain objects.
            return !pkg.startsWith("java.util");
        }
    };

    /**
     * Determines if a given object should be deeply proxied as a stateful, bean-like object.
     * <p>
     * This method is the primary decision point for recursive proxying. If it returns {@code true}, the proxying
     * framework will attempt to create a {@link ClassProxyImpl} (or a similar mechanism) for the object to track its
     * property changes. This is intended for mutable domain objects.
     *
     * @param property
     *         The {@link Property} from which the object value was retrieved. This provides context, allowing for
     *         policies based on property name, annotations, etc.
     * @param object
     *         The actual value retrieved from the property. This can be {@code null}.
     *
     * @return {@code true} if the object should be wrapped in a deep, state-tracking class proxy. {@code false} if it
     *         should be treated as a simple value and not be proxied.
     */
    boolean shouldProxy(Property property, Object object);

    /**
     * Determines if a given object is a container whose elements should be tracked.
     * <p>
     * If this method returns {@code true}, the framework will use a specialized proxy handler (like
     * {@link ContainerProxyHandler}) that monitors changes to the container's structure (e.g., adding/removing
     * elements) and also recursively applies the proxy policy to its contents.
     * <p>
     * The default implementation identifies instances of {@link List}, {@link Map}, and {@link Set}.
     *
     * @param object
     *         The object to check. This can be {@code null}.
     *
     * @return {@code true} if the object is a container that requires proxying, {@code false} otherwise.
     */
    default boolean shouldProxyContainer(Object object) {

        return object instanceof List || object instanceof Map || object instanceof Set;
    }

}
