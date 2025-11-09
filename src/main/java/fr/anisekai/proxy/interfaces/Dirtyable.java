package fr.anisekai.proxy.interfaces;

/**
 * Defines a contract for objects that can track modifications to their state.
 * <p>
 * An object implementing this interface can report whether it has been changed relative to a baseline state (typically,
 * its state at the time of creation or the last time it was considered "clean").
 * <p>
 * This contract is often applied recursively in object graphs. An object implementing {@link Dirtyable} may be
 * considered dirty not only if its own immediate properties have changed, but also if any of its managed, nested
 * {@link Dirtyable} components are also dirty.
 */
public interface Dirtyable {

    /**
     * Determines whether the object's state has been modified relative to its original state.
     * <p>
     * This check is typically deep or recursive. An object is considered dirty if its own direct properties have been
     * altered (e.g., a setter was called with a new value, an element was added to a collection), <b>or</b> if any of
     * its constituent {@link Dirtyable} components report that they are dirty.
     *
     * @return {@code true} if the object's state or the state of any of its nested {@link Dirtyable} children has been
     *         modified. Returns {@code false} only if the object and all its components are in their original,
     *         unmodified state.
     */
    boolean isDirty();

}
