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

            Assertions.assertEquals(8, properties.size());

            Assertions.assertTrue(properties.containsKey("id"), "Missing 'id' property");
            Assertions.assertTrue(properties.containsKey("name"), "Missing 'name' property");
            Assertions.assertTrue(properties.containsKey("active"), "Missing 'active' property");
            Assertions.assertTrue(properties.containsKey("tags"), "Missing 'tags' property'");
            Assertions.assertTrue(properties.containsKey("mapping"), "Missing 'mapping' property");
            Assertions.assertTrue(properties.containsKey("entity"), "Missing 'entity' property");
            Assertions.assertTrue(properties.containsKey("entities"), "Missing 'entities' property");
            Assertions.assertTrue(properties.containsKey("entityMap"), "Missing 'entityMap' property");
            Assertions.assertFalse(properties.containsKey("ignoreThis"), "Extraneous 'ignoreThis' property");
            Assertions.assertFalse(properties.containsKey("ignored"), "Extraneous 'ignored' property");
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

                Assertions.assertFalse(state.isDirty(), "State is already dirty");

                List<String> tags = Arrays.asList("one", "two", "three");
                proxy.setTags(tags);


                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty(), "No recorded state");
                Assertions.assertFalse(changes.isEmpty(), "No changes detected");

                Assertions.assertTrue(original.containsKey(property), "Missing property in recorded state");
                Assertions.assertTrue(changes.containsKey(property), "Missing property in change map");

                Assertions.assertNotNull(entity.getTags(), "null list from entity");
                Assertions.assertNotNull(proxy.getTags(), "null list from proxy");

                Assertions.assertEquals(tags.size(), entity.getTags().size(), "entity list desync");
                Assertions.assertEquals(tags.size(), proxy.getTags().size(), "proxy list desync");

                Assertions.assertTrue(state.isDirty(), "State was not dirty");

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

                Assertions.assertFalse(state.isDirty(), "State is already dirty");

                List<String> tags = Arrays.asList("one", "two", "three");
                proxy.setTags(tags);

                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty(), "No recorded state");
                Assertions.assertFalse(changes.isEmpty(), "No changes detected");

                Assertions.assertTrue(original.containsKey(property), "Missing property in recorded state");
                Assertions.assertTrue(changes.containsKey(property), "Missing property in change map");

                Assertions.assertNotNull(entity.getTags(), "null list from entity");
                Assertions.assertNotNull(proxy.getTags(), "null list from proxy");

                Assertions.assertEquals(tags.size(), entity.getTags().size(), "entity list desync");
                Assertions.assertEquals(tags.size(), proxy.getTags().size(), "proxy list desync");

                Assertions.assertTrue(state.isDirty(), "State was not dirty");

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

                Assertions.assertFalse(state.isDirty(), "State is already dirty");

                proxy.getTags().add("two");

                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty(), "No recorded state");
                Assertions.assertFalse(changes.isEmpty(), "No changes detected");

                Assertions.assertTrue(original.containsKey(property), "Missing property in recorded state");
                Assertions.assertTrue(changes.containsKey(property), "Missing property in change map");

                Assertions.assertNotNull(entity.getTags(), "null list from entity");
                Assertions.assertNotNull(proxy.getTags(), "null list from proxy");

                Assertions.assertEquals(2, entity.getTags().size(), "entity list desync");
                Assertions.assertEquals(2, proxy.getTags().size(), "proxy list desync");

                Assertions.assertTrue(state.isDirty(), "State was not dirty");

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

                Assertions.assertFalse(state.isDirty(), "State is already dirty");

                proxy.getTags().remove("two");

                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty(), "No recorded state");
                Assertions.assertFalse(changes.isEmpty(), "No changes detected");

                Assertions.assertTrue(original.containsKey(property), "Missing property in recorded state");
                Assertions.assertTrue(changes.containsKey(property), "Missing property in change map");

                Assertions.assertNotNull(entity.getTags(), "null list from entity");
                Assertions.assertNotNull(proxy.getTags(), "null list from proxy");

                Assertions.assertEquals(1, entity.getTags().size(), "entity list desync");
                Assertions.assertEquals(1, proxy.getTags().size(), "proxy list desync");

                Assertions.assertTrue(state.isDirty(), "State was not dirty");

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

                Assertions.assertFalse(state.isDirty(), "State is already dirty");

                Map<String, String> map = new HashMap<>();
                map.put("one", "1");
                map.put("two", "2");

                proxy.setMapping(map);

                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty(), "No recorded state");
                Assertions.assertFalse(changes.isEmpty(), "No changes detected");

                Assertions.assertTrue(original.containsKey(property), "Missing property in recorded state");
                Assertions.assertTrue(changes.containsKey(property), "Missing property in change map");

                Assertions.assertNotNull(entity.getMapping(), "null map from entity");
                Assertions.assertNotNull(proxy.getMapping(), "null map from proxy");

                Assertions.assertEquals(map.size(), entity.getMapping().size(), "entity map desync");
                Assertions.assertEquals(map.size(), proxy.getMapping().size(), "proxy map desync");

                Assertions.assertTrue(state.isDirty(), "State was not dirty");

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

                Assertions.assertFalse(state.isDirty(), "State is already dirty");

                Map<String, String> map2 = new HashMap<>();
                map2.put("one", "1");
                map2.put("two", "2");

                proxy.setMapping(map2);

                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty(), "No recorded state");
                Assertions.assertFalse(changes.isEmpty(), "No changes detected");

                Assertions.assertTrue(original.containsKey(property), "Missing property in recorded state");
                Assertions.assertTrue(changes.containsKey(property), "Missing property in change map");

                Assertions.assertNotNull(entity.getMapping(), "null map from entity");
                Assertions.assertNotNull(proxy.getMapping(), "null map from proxy");

                Assertions.assertEquals(map2.size(), entity.getMapping().size(), "entity map desync");
                Assertions.assertEquals(map2.size(), proxy.getMapping().size(), "proxy map desync");

                Assertions.assertTrue(state.isDirty(), "State was not dirty");

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

                Assertions.assertFalse(state.isDirty(), "State is already dirty");

                proxy.getMapping().put("three", "3");

                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty(), "No recorded state");
                Assertions.assertFalse(changes.isEmpty(), "No changes detected");

                Assertions.assertTrue(original.containsKey(property), "Missing property in recorded state");
                Assertions.assertTrue(changes.containsKey(property), "Missing property in change map");

                Assertions.assertNotNull(entity.getMapping(), "null map from entity");
                Assertions.assertNotNull(proxy.getMapping(), "null map from proxy");

                Assertions.assertEquals(3, entity.getMapping().size(), "entity map desync");
                Assertions.assertEquals(3, proxy.getMapping().size(), "proxy map desync");

                Assertions.assertTrue(state.isDirty(), "State was not dirty");

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

                Assertions.assertFalse(state.isDirty(), "State is already dirty");

                proxy.getMapping().remove("two");

                Map<Property, Object> original = state.getOriginalState();
                Map<Property, Object> changes  = state.getDifferentialState();

                Assertions.assertFalse(original.isEmpty(), "No recorded state");
                Assertions.assertFalse(changes.isEmpty(), "No changes detected");

                Assertions.assertTrue(original.containsKey(property), "Missing property in recorded state");
                Assertions.assertTrue(changes.containsKey(property), "Missing property in change map");

                Assertions.assertNotNull(entity.getMapping(), "null map from entity");
                Assertions.assertNotNull(proxy.getMapping(), "null map from proxy");

                Assertions.assertEquals(1, entity.getMapping().size(), "entity map desync");
                Assertions.assertEquals(1, proxy.getMapping().size(), "proxy map desync");

                Assertions.assertTrue(state.isDirty(), "State was not dirty");

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

            Property property = properties.get("active");

            ExampleEntity proxy = state.getProxy();

            Assertions.assertFalse(state.isDirty(), "State is already dirty");

            proxy.setActive(false);

            Map<Property, Object> original = state.getOriginalState();
            Map<Property, Object> changes  = state.getDifferentialState();

            Assertions.assertFalse(original.isEmpty(), "No recorded state");
            Assertions.assertFalse(changes.isEmpty(), "No changes detected");

            Assertions.assertTrue(original.containsKey(property), "Missing property in recorded state");
            Assertions.assertTrue(changes.containsKey(property), "Missing property in change map");

            Assertions.assertTrue((boolean) original.get(property), "wrong source value");
            Assertions.assertFalse((boolean) changes.get(property), "wrong final value");

            Assertions.assertFalse(entity.isActive(), "entity value desync");
            Assertions.assertFalse(proxy.isActive(), "proxy value desync");

            Assertions.assertTrue(state.isDirty(), "State was not dirty");

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

            Property property = properties.get("active");

            ExampleEntity proxy = state.getProxy();

            Assertions.assertFalse(state.isDirty());

            proxy.setActive(false);
            proxy.setActive(true);

            Map<Property, Object> original = state.getOriginalState();
            Map<Property, Object> changes  = state.getDifferentialState();

            Assertions.assertFalse(original.isEmpty(), "No recorded state");
            Assertions.assertTrue(changes.isEmpty(), "Changes detected");

            Assertions.assertTrue(original.containsKey(property), "Missing property in recorded state");

            Assertions.assertTrue((boolean) original.get(property), "wrong source value");

            Assertions.assertTrue(entity.isActive(), "entity value desync");
            Assertions.assertTrue(proxy.isActive(), "proxy value desync");

            Assertions.assertFalse(state.isDirty(), "State is dirty");

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

            Assertions.assertFalse(changesA.isEmpty(), "State A changes empty");
            Assertions.assertFalse(changesB.isEmpty(), "State B changes empty");

            Assertions.assertTrue(changesA.containsKey(propertyA), "Missing property in State A changes");
            Assertions.assertTrue(changesB.containsKey(propertyB), "Missing property in State B changes");

            String nameA = changesA.get(propertyA).toString();
            String nameB = changesB.get(propertyB).toString();

            Assertions.assertNotEquals(nameA, nameB, "Changes are shared");
            Assertions.assertNotEquals(proxyA.getName(), proxyB.getName(), "Proxy return values are shared");
            Assertions.assertNotEquals(entityA.getName(), entityB.getName(), "Entity modification overwritten");

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

            Assertions.assertSame(stateA, stateB, "New proxy has been created");

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

        @Test
        @Order(8)
        @DisplayName("Should change the proxy instance")
        void shouldChangeProxyTarget() {

            ClassProxyFactory factory = new ClassProxyFactory();
            ExampleEntity     one     = ExampleEntity.create(1);
            ExampleEntity     two     = ExampleEntity.create(2);

            State<ExampleEntity> state = Assertions.assertDoesNotThrow(() -> factory.create(one));
            Assertions.assertNotNull(state);
            ExampleEntity proxy = state.getProxy();

            Assertions.assertEquals(1L, proxy.getId(), "Wrong starting instance");
            Assertions.assertDoesNotThrow(() -> factory.refresh(one, two));
            Assertions.assertEquals(2L, proxy.getId(), "Proxy target unchanged");

            state.close();
            factory.close();
        }

    }

    @Nested
    @Order(3)
    @DisplayName("Class Proxy Unwrapping")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @TestClassOrder(ClassOrderer.OrderAnnotation.class)
    class ClassProxyUnwrappingTests {

        @Test
        @Order(1)
        @DisplayName("Should retrieve a proxyless instance")
        void shouldRetrieveProxyless() {

            ClassProxyFactory factory = new ClassProxyFactory();
            ExampleEntity     entity  = ExampleEntity.create();

            State<ExampleEntity> state = Assertions.assertDoesNotThrow(() -> factory.create(entity));
            Assertions.assertNotNull(state);

            ExampleEntity instance = state.getInstance();

            Assertions.assertEquals(entity, instance, "Did not retrieved original instance");

            state.close();
            factory.close();
        }

        @Test
        @Order(2)
        @DisplayName("Should retrieve a proxyless instance with child")
        void shouldRetrieveProxylessChild() {

            ClassProxyFactory factory = new ClassProxyFactory();
            ExampleEntity     entityA = ExampleEntity.create(1);
            ExampleEntity     entityB = ExampleEntity.create(2);

            State<ExampleEntity> stateA = Assertions.assertDoesNotThrow(() -> factory.create(entityA));
            State<ExampleEntity> stateB = Assertions.assertDoesNotThrow(() -> factory.create(entityB));

            Assertions.assertNotNull(stateA);
            Assertions.assertNotNull(stateB);

            ExampleEntity proxyA = stateA.getProxy();
            ExampleEntity proxyB = stateB.getProxy();

            proxyA.setEntity(proxyB);

            ExampleEntity instanceA = stateA.getInstance();
            ExampleEntity instanceB = stateB.getInstance();

            Assertions.assertEquals(proxyA, instanceA, "Logical equality should match (A)");
            Assertions.assertEquals(proxyA.getEntity(), instanceB, "Logical equality should match (B)");
            Assertions.assertNotSame(proxyA, instanceA, "Unproxied entity leak (A)");
            Assertions.assertNotSame(proxyA.getEntity(), instanceB, "Unproxied entity leak (B)");
            Assertions.assertInstanceOf(State.class, proxyA.getEntity(), "Should be a proxy");

            stateA.close();
            stateB.close();
            factory.close();
        }

        @Test
        @Order(2)
        @DisplayName("Should retrieve a proxyless instance with containers")
        void shouldRetrieveProxylessContainer() {

            ClassProxyFactory factory = new ClassProxyFactory();
            ExampleEntity     entityA = ExampleEntity.create(1);
            ExampleEntity     entityB = ExampleEntity.create(2);

            List<ExampleEntity> list = new ArrayList<>();

            list.add(entityB);

            entityA.setEntities(list);

            State<ExampleEntity> stateA = Assertions.assertDoesNotThrow(() -> factory.create(entityA));
            Assertions.assertNotNull(stateA);

            ExampleEntity proxyA    = stateA.getProxy();
            ExampleEntity instanceA = stateA.getInstance();

            Assertions.assertEquals(proxyA, instanceA, "Logical equality should match");
            Assertions.assertEquals(proxyA.getEntities(), list, "Proxy list mismatch");
            Assertions.assertSame(instanceA.getEntities(), list, "Instance list mismatch");

            stateA.close();
            factory.close();
        }

    }

}
