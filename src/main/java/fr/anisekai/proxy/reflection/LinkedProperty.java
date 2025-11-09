package fr.anisekai.proxy.reflection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Represents a complete JavaBean property, including its backing field, getter/setter methods, and name. The property
 * is also linked to an object instance allowing to easily retrieve or define the underlying value using the getter and
 * setter.
 */
public class LinkedProperty extends Property {

    private final Object instance;

    /**
     * Create a new {@link LinkedProperty} instance.
     *
     * @param property
     *         The {@link Property} to use
     * @param instance
     *         The {@link Object} instance onto which the {@link Property} can be applied.
     */
    public LinkedProperty(Property property, Object instance) {

        super(property.getField(), property.getGetter(), property.getSetter(), property.getName());
        this.instance = instance;
    }

    /**
     * Retrieve the value of this {@link LinkedProperty}.
     *
     * @return The value of the {@link Property} in the underlying {@link Object}.
     *
     * @throws InvocationTargetException
     *         See {@link Method#invoke(Object, Object...)}
     * @throws IllegalAccessException
     *         See {@link Method#invoke(Object, Object...)}
     */
    public Object getValue() throws InvocationTargetException, IllegalAccessException {

        return this.getGetter().invoke(this.instance);
    }

    /**
     * Define the value of this {@link LinkedProperty}.
     *
     * @param value
     *         The new value for the {@link Property} in the underlying {@link Object}.
     *
     * @throws InvocationTargetException
     *         See {@link Method#invoke(Object, Object...)}
     * @throws IllegalAccessException
     *         See {@link Method#invoke(Object, Object...)}
     */
    public void setValue(Object value) throws InvocationTargetException, IllegalAccessException {

        this.getSetter().invoke(this.instance, value);
    }

    @Override
    public boolean equals(Object object) {

        if (!(object instanceof Property that)) return false;
        return Objects.equals(this.getField(), that.getField()) &&
                Objects.equals(this.getGetter(), that.getGetter()) &&
                Objects.equals(this.getSetter(), that.getSetter()) &&
                Objects.equals(this.getName(), that.getName());
    }

    @Override
    public int hashCode() {

        return Objects.hash(this.getField(), this.getGetter(), this.getSetter(), this.getName());
    }

    @Override
    public String toString() {

        return "LinkedProperty[name=%s, field=%s, getter=%s, setter=%s, instance=%s]".formatted(
                this.getName(),
                this.getField().getName(),
                this.getGetter().getName(),
                this.getSetter().getName(),
                this.instance
        );
    }

}
