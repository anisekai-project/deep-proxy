package fr.anisekai.proxy;

import fr.anisekai.proxy.exceptions.ProxyAccessException;
import fr.anisekai.proxy.interfaces.State;
import fr.anisekai.proxy.reflection.Properties;
import fr.anisekai.proxy.reflection.Property;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@DisplayName("Proxy")
@Tags({@Tag("unit-test"), @Tag("proxy")})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
public class ClassProxyFactoryTests {

    @Nested
    @Order(1)
    @DisplayName("Property Scanning")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @TestClassOrder(ClassOrderer.OrderAnnotation.class)
    class PropertyTests {

        @Test
        @Order(1)
        @DisplayName("Should find all properties")
        void shouldFindAllProperties() {

            Map<String, Property> properties = Properties
                    .getPropertiesOf(ExampleEntity.class)
                    .stream()
                    .collect(Collectors.toMap(Property::getName, Function.identity()));

            Assertions.assertEquals(6, properties.size());

            Assertions.assertTrue(properties.containsKey("id"));
            Assertions.assertTrue(properties.containsKey("name"));
            Assertions.assertTrue(properties.containsKey("active"));
            Assertions.assertTrue(properties.containsKey("tags"));
            Assertions.assertTrue(properties.containsKey("mapping"));
            Assertions.assertTrue(properties.containsKey("entity"));
            Assertions.assertFalse(properties.containsKey("ignoreThis"));
            Assertions.assertFalse(properties.containsKey("ignored"));
        }

    }

    @Nested
    @Order(2)
    @DisplayName("Class Proxy Actions")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @TestClassOrder(ClassOrderer.OrderAnnotation.class)
    class ClassProxyFactoryActionTests {

        @Nested
        @Order(1)
        @DisplayName("On Lists")
        @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
        @TestClassOrder(ClassOrderer.OrderAnnotation.class)
        class ProxyListTests {

            @Test
            @Order(1)
            @DisplayName("Should detect change (set) on (null)")
            void shouldDetectChangeFromNull() {

                ClassProxyFactory factory = new ClassProxyFactory();
                ExampleEntity     entity  = ExampleEntity.create();

                State<ExampleEntity> state = Assertions.assertDoesNotThrow(() -> factory.create(entity));
                Assertions.assertNotNull(state);

                Map<String, Property> properties = state
                        .getOriginalState()
                        .keySet()
                        .stream()
                        .collect(Collectors.toMap(Property::getName, Function.identity()));

                Property      property = properties.get("tags");
                ExampleEntity proxy    = state.getProxy();

                Assertions.assertFalse(state.isDirty());

                List<String> tags = Arrays.asList("one", "two", "three");
                proxy.setTags(tags);


                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty());
                Assertions.assertFalse(changes.isEmpty());

                Assertions.assertTrue(original.containsKey(property));
                Assertions.assertTrue(changes.containsKey(property));

                Assertions.assertNotNull(entity.getTags());
                Assertions.assertNotNull(proxy.getTags());

                Assertions.assertEquals(tags.size(), entity.getTags().size());
                Assertions.assertEquals(tags.size(), proxy.getTags().size());

                Assertions.assertTrue(state.isDirty());

                state.close();
                factory.close();
            }

            @Test
            @Order(2)
            @DisplayName("Should detect change (set) on (non-null)")
            void shouldDetectChangeFromNonNull() {

                ClassProxyFactory factory = new ClassProxyFactory();
                ExampleEntity     entity  = ExampleEntity.create();

                entity.setTags(new ArrayList<>(List.of("one")));

                State<ExampleEntity> state = Assertions.assertDoesNotThrow(() -> factory.create(entity));
                Assertions.assertNotNull(state);

                Map<String, Property> properties = state
                        .getOriginalState()
                        .keySet()
                        .stream()
                        .collect(Collectors.toMap(Property::getName, Function.identity()));

                Property      property = properties.get("tags");
                ExampleEntity proxy    = state.getProxy();

                Assertions.assertFalse(state.isDirty());

                List<String> tags = Arrays.asList("one", "two", "three");
                proxy.setTags(tags);

                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty());
                Assertions.assertFalse(changes.isEmpty());

                Assertions.assertTrue(original.containsKey(property));
                Assertions.assertTrue(changes.containsKey(property));

                Assertions.assertNotNull(entity.getTags());
                Assertions.assertNotNull(proxy.getTags());

                Assertions.assertEquals(tags.size(), entity.getTags().size());
                Assertions.assertEquals(tags.size(), proxy.getTags().size());

                Assertions.assertTrue(state.isDirty());

                state.close();
                factory.close();
            }

            @Test
            @Order(3)
            @DisplayName("Should detect change (add)")
            void shouldDetectInsertion() {

                ClassProxyFactory factory = new ClassProxyFactory();
                ExampleEntity     entity  = ExampleEntity.create();

                entity.setTags(new ArrayList<>(List.of("one")));

                State<ExampleEntity> state = Assertions.assertDoesNotThrow(() -> factory.create(entity));
                Assertions.assertNotNull(state);

                Map<String, Property> properties = state
                        .getOriginalState()
                        .keySet()
                        .stream()
                        .collect(Collectors.toMap(Property::getName, Function.identity()));

                Property      property = properties.get("tags");
                ExampleEntity proxy    = state.getProxy();

                Assertions.assertFalse(state.isDirty());

                proxy.getTags().add("two");

                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty());
                Assertions.assertFalse(changes.isEmpty());

                Assertions.assertTrue(original.containsKey(property));
                Assertions.assertTrue(changes.containsKey(property));

                Assertions.assertNotNull(entity.getTags());
                Assertions.assertNotNull(proxy.getTags());

                Assertions.assertEquals(2, entity.getTags().size());
                Assertions.assertEquals(2, proxy.getTags().size());

                Assertions.assertTrue(state.isDirty());

                state.close();
                factory.close();
            }

            @Test
            @Order(4)
            @DisplayName("Should detect change (remove)")
            void shouldDetectDeletion() {

                ClassProxyFactory factory = new ClassProxyFactory();
                ExampleEntity     entity  = ExampleEntity.create();

                entity.setTags(new ArrayList<>(List.of("one", "two")));

                State<ExampleEntity> state = Assertions.assertDoesNotThrow(() -> factory.create(entity));
                Assertions.assertNotNull(state);

                Map<String, Property> properties = state
                        .getOriginalState()
                        .keySet()
                        .stream()
                        .collect(Collectors.toMap(Property::getName, Function.identity()));

                Property      property = properties.get("tags");
                ExampleEntity proxy    = state.getProxy();

                Assertions.assertFalse(state.isDirty());

                proxy.getTags().remove("two");

                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty());
                Assertions.assertFalse(changes.isEmpty());

                Assertions.assertTrue(original.containsKey(property));
                Assertions.assertTrue(changes.containsKey(property));

                Assertions.assertNotNull(entity.getTags());
                Assertions.assertNotNull(proxy.getTags());

                Assertions.assertEquals(1, entity.getTags().size());
                Assertions.assertEquals(1, proxy.getTags().size());

                Assertions.assertTrue(state.isDirty());

                state.close();
                factory.close();
            }

        }

        @Nested
        @Order(2)
        @DisplayName("On Maps")
        @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
        @TestClassOrder(ClassOrderer.OrderAnnotation.class)
        class ProxyMapTests {

            @Test
            @Order(1)
            @DisplayName("Should detect change (set) on (null)")
            void shouldDetectChangeFromNull() {

                ClassProxyFactory factory = new ClassProxyFactory();
                ExampleEntity     entity  = ExampleEntity.create();

                State<ExampleEntity> state = Assertions.assertDoesNotThrow(() -> factory.create(entity));
                Assertions.assertNotNull(state);

                Map<String, Property> properties = state
                        .getOriginalState()
                        .keySet()
                        .stream()
                        .collect(Collectors.toMap(Property::getName, Function.identity()));

                Property      property = properties.get("mapping");
                ExampleEntity proxy    = state.getProxy();

                Assertions.assertFalse(state.isDirty());

                Map<String, String> map = new HashMap<>();
                map.put("one", "1");
                map.put("two", "2");

                proxy.setMapping(map);

                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty());
                Assertions.assertFalse(changes.isEmpty());

                Assertions.assertTrue(original.containsKey(property));
                Assertions.assertTrue(changes.containsKey(property));

                Assertions.assertNotNull(entity.getMapping());
                Assertions.assertNotNull(proxy.getMapping());

                Assertions.assertEquals(map.size(), entity.getMapping().size());
                Assertions.assertEquals(map.size(), proxy.getMapping().size());

                Assertions.assertTrue(state.isDirty());

                state.close();
                factory.close();
            }

            @Test
            @Order(2)
            @DisplayName("Should detect change (set) on (non-null)")
            void shouldDetectChangeFromNonNull() {

                ClassProxyFactory factory = new ClassProxyFactory();
                ExampleEntity     entity  = ExampleEntity.create();

                Map<String, String> map1 = new HashMap<>();
                map1.put("one", "1");
                map1.put("two", "2");
                entity.setMapping(map1);

                State<ExampleEntity> state = Assertions.assertDoesNotThrow(() -> factory.create(entity));
                Assertions.assertNotNull(state);

                Map<String, Property> properties = state
                        .getOriginalState()
                        .keySet()
                        .stream()
                        .collect(Collectors.toMap(Property::getName, Function.identity()));

                Property      property = properties.get("mapping");
                ExampleEntity proxy    = state.getProxy();

                Assertions.assertFalse(state.isDirty());

                Map<String, String> map2 = new HashMap<>();
                map2.put("one", "1");
                map2.put("two", "2");

                proxy.setMapping(map2);

                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty());
                Assertions.assertFalse(changes.isEmpty());

                Assertions.assertTrue(original.containsKey(property));
                Assertions.assertTrue(changes.containsKey(property));

                Assertions.assertNotNull(entity.getMapping());
                Assertions.assertNotNull(proxy.getMapping());

                Assertions.assertEquals(map2.size(), entity.getMapping().size());
                Assertions.assertEquals(map2.size(), proxy.getMapping().size());

                Assertions.assertTrue(state.isDirty());

                state.close();
                factory.close();
            }

            @Test
            @Order(3)
            @DisplayName("Should detect change (put)")
            void shouldDetectInsertion() {

                ClassProxyFactory factory = new ClassProxyFactory();
                ExampleEntity     entity  = ExampleEntity.create();

                Map<String, String> map = new HashMap<>();
                map.put("one", "1");
                map.put("two", "2");
                entity.setMapping(map);

                State<ExampleEntity> state = Assertions.assertDoesNotThrow(() -> factory.create(entity));
                Assertions.assertNotNull(state);

                Map<String, Property> properties = state
                        .getOriginalState()
                        .keySet()
                        .stream()
                        .collect(Collectors.toMap(Property::getName, Function.identity()));

                Property      property = properties.get("mapping");
                ExampleEntity proxy    = state.getProxy();

                Assertions.assertFalse(state.isDirty());

                proxy.getMapping().put("three", "3");

                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty());
                Assertions.assertFalse(changes.isEmpty());

                Assertions.assertTrue(original.containsKey(property));
                Assertions.assertTrue(changes.containsKey(property));

                Assertions.assertNotNull(entity.getMapping());
                Assertions.assertNotNull(proxy.getMapping());

                Assertions.assertEquals(3, entity.getMapping().size());
                Assertions.assertEquals(3, proxy.getMapping().size());

                Assertions.assertTrue(state.isDirty());

                state.close();
                factory.close();
            }

            @Test
            @Order(4)
            @DisplayName("Should detect change (remove)")
            void shouldDetectDeletion() {

                ClassProxyFactory factory = new ClassProxyFactory();
                ExampleEntity     entity  = ExampleEntity.create();

                Map<String, String> map = new HashMap<>();
                map.put("one", "1");
                map.put("two", "2");
                entity.setMapping(map);

                State<ExampleEntity> state = Assertions.assertDoesNotThrow(() -> factory.create(entity));
                Assertions.assertNotNull(state);

                Map<String, Property> properties = state
                        .getOriginalState()
                        .keySet()
                        .stream()
                        .collect(Collectors.toMap(Property::getName, Function.identity()));

                Property      property = properties.get("mapping");
                ExampleEntity proxy    = state.getProxy();

                Assertions.assertFalse(state.isDirty());

                proxy.getMapping().remove("two");

                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty());
                Assertions.assertFalse(changes.isEmpty());

                Assertions.assertTrue(original.containsKey(property));
                Assertions.assertTrue(changes.containsKey(property));

                Assertions.assertNotNull(entity.getMapping());
                Assertions.assertNotNull(proxy.getMapping());

                Assertions.assertEquals(1, entity.getMapping().size());
                Assertions.assertEquals(1, proxy.getMapping().size());

                Assertions.assertTrue(state.isDirty());

                state.close();
                factory.close();
            }

        }

        @Test
        @Order(3)
        @DisplayName("Should detect simple change on field")
        void shouldDetectChangeOnField() {

            ClassProxyFactory factory = new ClassProxyFactory();
            ExampleEntity     entity  = ExampleEntity.create();

            State<ExampleEntity> state = Assertions.assertDoesNotThrow(() -> factory.create(entity));
            Assertions.assertNotNull(state);

            Map<String, Property> properties = state
                    .getOriginalState()
                    .keySet()
                    .stream()
                    .collect(Collectors.toMap(Property::getName, Function.identity()));

            Property activeProperty = properties.get("active");

            ExampleEntity proxy = state.getProxy();

            Assertions.assertFalse(state.isDirty());

            proxy.setActive(false);

            Map<Property, Object> original = state.getOriginalState();
            Map<Property, Object> changes  = state.getDifferentialState();

            Assertions.assertFalse(original.isEmpty());
            Assertions.assertFalse(changes.isEmpty());

            Assertions.assertTrue(original.containsKey(activeProperty));
            Assertions.assertTrue(changes.containsKey(activeProperty));

            Assertions.assertTrue((boolean) original.get(activeProperty));
            Assertions.assertFalse((boolean) changes.get(activeProperty));

            Assertions.assertFalse(entity.isActive());
            Assertions.assertFalse(proxy.isActive());

            Assertions.assertTrue(state.isDirty());

            state.close();
            factory.close();
        }

        @Test
        @Order(4)
        @DisplayName("Should reset simple change on field")
        void shouldResetChangeOnField() {

            ClassProxyFactory factory = new ClassProxyFactory();
            ExampleEntity     entity  = ExampleEntity.create();

            State<ExampleEntity> state = Assertions.assertDoesNotThrow(() -> factory.create(entity));
            Assertions.assertNotNull(state);

            Map<String, Property> properties = state
                    .getOriginalState()
                    .keySet()
                    .stream()
                    .collect(Collectors.toMap(Property::getName, Function.identity()));

            Property activeProperty = properties.get("active");

            ExampleEntity proxy = state.getProxy();

            Assertions.assertFalse(state.isDirty());

            proxy.setActive(false);
            proxy.setActive(true);

            Map<Property, Object> original = state.getOriginalState();
            Map<Property, Object> changes  = state.getDifferentialState();

            Assertions.assertFalse(original.isEmpty());
            Assertions.assertTrue(changes.isEmpty());

            Assertions.assertTrue(original.containsKey(activeProperty));

            Assertions.assertTrue((boolean) original.get(activeProperty));

            Assertions.assertTrue(entity.isActive());
            Assertions.assertTrue(proxy.isActive());

            Assertions.assertFalse(state.isDirty());

            state.close();
            factory.close();
        }

        @Test
        @Order(5)
        @DisplayName("Should not share state between two instances")
        void shouldNotShareState() {

            ClassProxyFactory factory = new ClassProxyFactory();

            ExampleEntity entityA = ExampleEntity.create(1);
            ExampleEntity entityB = ExampleEntity.create(2);

            State<ExampleEntity> stateA = Assertions.assertDoesNotThrow(() -> factory.create(entityA));
            Assertions.assertNotNull(stateA);

            State<ExampleEntity> stateB = Assertions.assertDoesNotThrow(() -> factory.create(entityB));
            Assertions.assertNotNull(stateB);

            Map<String, Property> propertiesA = stateA
                    .getOriginalState()
                    .keySet()
                    .stream()
                    .collect(Collectors.toMap(Property::getName, Function.identity()));

            Map<String, Property> propertiesB = stateB
                    .getOriginalState()
                    .keySet()
                    .stream()
                    .collect(Collectors.toMap(Property::getName, Function.identity()));

            ExampleEntity proxyA = stateA.getProxy();
            ExampleEntity proxyB = stateB.getProxy();

            proxyA.setName("proxy a");
            proxyB.setName("proxy b");

            Property propertyA = propertiesA.get("name");
            Property propertyB = propertiesB.get("name");

            Map<Property, Object> changesA = stateA.getDifferentialState();
            Map<Property, Object> changesB = stateB.getDifferentialState();

            Assertions.assertFalse(changesA.isEmpty());
            Assertions.assertFalse(changesB.isEmpty());

            Assertions.assertTrue(changesA.containsKey(propertyA));
            Assertions.assertTrue(changesB.containsKey(propertyB));

            String nameA = changesA.get(propertyA).toString();
            String nameB = changesB.get(propertyB).toString();

            Assertions.assertNotEquals(nameA, nameB);
            Assertions.assertNotEquals(proxyA.getName(), proxyB.getName());
            Assertions.assertNotEquals(entityA.getName(), entityB.getName());

            stateA.close();
            stateB.close();
            factory.close();
        }

        @Test
        @Order(6)
        @DisplayName("Should not create another proxy")
        void shouldNotReProxy() {

            ClassProxyFactory factory = new ClassProxyFactory();
            ExampleEntity     entity  = ExampleEntity.create();

            State<ExampleEntity> stateA = Assertions.assertDoesNotThrow(() -> factory.create(entity));
            Assertions.assertNotNull(stateA);

            State<ExampleEntity> stateB = Assertions.assertDoesNotThrow(() -> factory.create(entity));
            Assertions.assertNotNull(stateB);

            Assertions.assertEquals(stateA, stateB);

            stateA.close();
            stateB.close();
            factory.close();
        }

        @Test
        @Order(7)
        @DisplayName("Should not be able to use closed proxy (orphan)")
        void shouldNotBeAbleUseClosedProxy() {

            ClassProxyFactory factory = new ClassProxyFactory();
            ExampleEntity     entity  = ExampleEntity.create();

            State<ExampleEntity> state = Assertions.assertDoesNotThrow(() -> factory.create(entity));
            Assertions.assertNotNull(state);

            ExampleEntity proxy = state.getProxy();

            Assertions.assertDoesNotThrow(() -> proxy.setName("test"));

            state.close();

            ProxyAccessException pae1 = Assertions.assertThrows(
                    ProxyAccessException.class,
                    () -> proxy.setName("test")
            );
            Assertions.assertTrue(pae1.getMessage().contains("Unable to find the proxy's factory."));

            factory.close();
            ProxyAccessException pae2 = Assertions.assertThrows(
                    ProxyAccessException.class,
                    () -> proxy.setName("test")
            );
            Assertions.assertTrue(pae2.getMessage().contains("Unable to find the proxy's factory"));
        }

    }

}
