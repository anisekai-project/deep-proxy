package fr.anisekai.proxy.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Represents a complete JavaBean property, including its backing field, getter/setter methods, and name.
 */
public class Property {

    private final Field  field;
    private final Method getter;
    private final Method setter;
    private final String name;

    /**
     * Create a new {@link Property} instance.
     *
     * @param field
     *         The backing field for this property.
     * @param getter
     *         The public getter method.
     * @param setter
     *         The public setter method.
     * @param name
     *         The canonical name of the property (e.g., "name").
     */
    public Property(Field field, Method getter, Method setter, String name) {

        this.field  = field;
        this.getter = getter;
        this.setter = setter;
        this.name   = name;
    }

    /**
     * Retrieve the {@link Field} of this {@link Property}.
     *
     * @return A {@link Field}.
     */
    public Field getField() {

        return this.field;
    }

    /**
     * Retrieve the {@link Method} used as getter for this {@link Property}.
     *
     * @return A {@link Method}.
     */
    public Method getGetter() {

        return this.getter;
    }

    /**
     * Retrieve the {@link Method} used as setter for this {@link Property}.
     *
     * @return A {@link Method}.
     */
    public Method getSetter() {

        return this.setter;
    }

    /**
     * Retrieve the name of this {@link Property}.
     *
     * @return A name.
     */
    public String getName() {

        return this.name;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        Property property = (Property) o;
        return this.field.equals(property.field) &&
                this.getter.equals(property.getter) &&
                this.setter.equals(property.setter) &&
                this.name.equals(property.name);
    }

    @Override
    public int hashCode() {

        return Objects.hash(this.field, this.getter, this.setter, this.name);
    }

    @Override
    public String toString() {

        return "Property[name=%s, field=%s, getter=%s, setter=%s]".formatted(
                this.name,
                this.field.getName(),
                this.getter.getName(),
                this.setter.getName()
        );
    }

}
