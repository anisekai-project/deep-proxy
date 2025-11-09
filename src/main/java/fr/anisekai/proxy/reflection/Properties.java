package fr.anisekai.proxy.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class Properties {

    private static final Map<Class<?>, Set<Property>> LOOKUP = new ConcurrentHashMap<>();

    private Properties() {

    }

    /**
     * Introspects a class and returns a set of all its valid JavaBean properties. A valid property must have a public
     * getter, a public setter, and a corresponding backing field in the class hierarchy.
     *
     * @param clazz
     *         The class to analyze.
     *
     * @return A Set of {@link Property} records.
     */
    public static Set<Property> getPropertiesOf(Class<?> clazz) {

        return LOOKUP.computeIfAbsent(clazz, Properties::computeProperties);
    }

    /**
     * Introspects the object and returns a set of all its valid JavaBean properties. A valid property must have a
     * public getter, a public setter, and a corresponding backing field in the class hierarchy.
     *
     * @param object
     *         The object to analyze.
     *
     * @return A Set of {@link LinkedProperty} records.
     */
    public static Set<LinkedProperty> getPropertiesOf(Object object) {

        return getPropertiesOf(object.getClass())
                .stream()
                .map(property -> new LinkedProperty(property, object))
                .collect(Collectors.toSet());
    }

    private static Set<Property> computeProperties(Class<?> clazz) {

        Map<String, List<Method>> methodsByProperty = Arrays
                .stream(clazz.getMethods())
                .filter(method -> !method.getDeclaringClass().equals(Object.class))
                .filter(Properties::isPropertyMethod)
                .collect(Collectors.groupingBy(Properties::getPropertyName));

        Set<Property> properties = new HashSet<>();
        for (Map.Entry<String, List<Method>> entry : methodsByProperty.entrySet()) {
            findValidPair(clazz, entry.getValue(), entry.getKey()).ifPresent(properties::add);
        }

        return properties;
    }

    private static Optional<Property> findValidPair(Class<?> originatingClass, Collection<Method> methods, String propertyName) {

        Optional<Method> getterOpt = methods.stream().filter(Properties::isGetter).findFirst();
        if (getterOpt.isEmpty()) {
            return Optional.empty();
        }

        Method   getter       = getterOpt.get();
        Class<?> propertyType = getter.getReturnType();

        Optional<Method> setterOpt = methods
                .stream()
                .filter(Properties::isSetter)
                .filter(setter -> setter.getParameterCount() == 1)
                .filter(setter -> setter.getParameterTypes()[0].equals(propertyType))
                .findFirst();

        return setterOpt.flatMap(
                method -> findFieldInHierarchy(originatingClass, propertyName)
                        .map(field -> new Property(field, getter, method, propertyName))
        );

    }

    private static Optional<Field> findFieldInHierarchy(Class<?> searchClass, String fieldName) {

        Class<?> currentClass = searchClass;
        while (currentClass != null && !currentClass.equals(Object.class)) {
            try {
                return Optional.of(currentClass.getDeclaredField(fieldName));
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return Optional.empty();
    }

    private static boolean isGetter(Method method) {

        if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0 || void.class.equals(method.getReturnType())) {
            return false;
        }
        String name = method.getName();
        return name.startsWith("get") || name.startsWith("is");
    }

    private static boolean isSetter(Method method) {

        if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
            return false;
        }
        return method.getName().startsWith("set");
    }

    private static boolean isPropertyMethod(Method method) {

        return isGetter(method) || isSetter(method);
    }

    private static String getPropertyName(Method method) {

        String methodName = method.getName();
        if (methodName.startsWith("get") || methodName.startsWith("set")) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is")) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        throw new IllegalArgumentException("Method is not a valid property method: " + methodName);
    }

}
