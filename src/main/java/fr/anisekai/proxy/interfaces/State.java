package fr.anisekai.proxy.interfaces;


import fr.anisekai.proxy.reflection.Property;

import java.io.Serializable;
import java.util.Map;

/**
 * Defines the contract for a state-aware proxy that tracks changes to an underlying object.
 * <p>
 * This interface is the primary entry point for interacting with the state-tracking capabilities of a proxied object.
 * It provides access to the original instance and the proxy, allows inspection of what has changed, and offers a
 * mechanism to revert all changes.
 *
 * @param <T>
 *         The type of the object being proxied.
 */
public interface State<T> extends Dirtyable, AutoCloseable, Serializable {

    /**
     * Returns the underlying, original object instance that is being proxied.
     * <p>
     * <b>Warning:</b> Modifications made directly to this instance will <strong>not</strong> be tracked
     * by the state-tracking mechanism. This method is primarily useful for operations that need the original object,
     * such as identity comparisons ({@code ==}), unwrapping for serialization, or passing it to external libraries that
     * do not work with proxies.
     *
     * @return The raw, untracked, original object instance.
     */
    T getInstance();

    /**
     * Returns the proxy instance that tracks state changes.
     * <p>
     * This is the object that should be used throughout your application's business logic. Any method calls made on
     * this proxy (specifically, calls to property setters) will be intercepted to track changes. Interacting with the
     * proxy is the only way to ensure that the object's state is correctly monitored.
     *
     * @return The state-tracking proxy object.
     */
    T getProxy();

    /**
     * Retrieves a snapshot of the object's state as it was when the proxy was created.
     * <p>
     * This map contains all tracked properties and their initial values. It is immutable and serves as the baseline
     * against which changes are compared.
     *
     * @return An unmodifiable map of the original property states, where the key is the {@link Property} descriptor and
     *         the value is the original property value.
     */
    Map<Property, Object> getOriginalState();

    /**
     * Retrieves a map of all properties that have been modified since the proxy was created.
     * <p>
     * This map includes any property whose value is no longer equal to its original value. The check is typically deep:
     * if a property holds a nested proxied object that is dirty, the property itself will be included in this map,
     * pointing to the current (dirty) nested object.
     * <p>
     * If a property is changed and then reverted to its original value, it will be removed from the differential
     * state.
     *
     * @return An unmodifiable map containing only the modified properties and their current values, where the key is
     *         the {@link Property} descriptor and the value is the modified property value.
     */
    Map<Property, Object> getDifferentialState();

    /**
     * Reverts all tracked changes made to the object, restoring it to its original state.
     * <p>
     * This operation iterates through the {@link #getDifferentialState()} and applies the corresponding original values
     * from {@link #getOriginalState()} back to the instance. The reversion is recursive: it will also call
     * {@code revert()} on any nested objects that are also managed by a {@link State} proxy.
     * <p>
     * After this method is called, {@link #isDirty()} will return {@code false}, and the differential state will be
     * empty.
     */
    void revert();

    /**
     * Close this {@link State} to stop the tracking process on the associated object.
     */
    void close();

}
