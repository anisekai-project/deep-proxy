# Deep Proxy

A lightweight yet powerful Java library for creating deep proxies to track changes ("dirtiness") in complex object graphs. It is designed to be efficient, easy to use, and highly customizable.

This library is ideal for scenarios like:
*   Implementing the Unit of Work pattern in data mappers or ORMs.
*   Managing state changes in UI frameworks.
*   Building robust Undo/Redo functionality.
*   Auditing changes made to domain objects.

---

## Features

*   **Stateful Proxies**: Wrap any JavaBean-style object to monitor its state.
*   **Dirty Checking**: Easily determine if an object has been modified from its original state using a simple `isDirty()` boolean check.
*   **Deep (Recursive) Proxying**: Automatically proxies nested objects and collections, allowing you to track changes across an entire object graph.
*   **Comprehensive State Management**: The `State` interface provides full access to:
    *   The original, unwrapped object.
    *   The initial state of the object when it was proxied.
    *   A "diff" of only the properties that have changed.
*   **Revert Changes**: A simple `revert()` method restores the object and its entire nested graph to their original state.
*   **Collection & Map Support**: Specialized handlers for `List`, `Map`, and `Set` that track changes when elements are added, removed, or when the collection reference itself is replaced.
*   **Customizable Behavior**: Use the `ProxyPolicy` interface to define precisely which objects and properties should be proxied, giving you fine-grained control.
*   **High Performance**: Uses [ByteBuddy](https://bytebuddy.net/) for efficient, runtime code generation. Generated proxy classes are cached globally to ensure object proxying is fast.
*   **Clean Lifecycle**: The `ClassProxyFactory` manages the lifecycle of proxies, with a `close()` method to release all associated objects and prevent memory leaks.
*   **Safe Serialization**: Proxied objects automatically serialize as their original, underlying instance, ensuring compatibility and preventing serialization of proxy-related overhead.

---

## Getting Started

### Prerequisites

*   Java 17 or higher
*   A dependency management tool like Maven or Gradle

### Installation

> Check tags for the latest version

**Maven:**
```xml
<dependency>
  <groupId>fr.anisekai</groupId>
  <artifactId>deep-proxy</artifactId>
  <version>1.0.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'fr.anisekai:deep-proxy:1.0.0'
```

---

## Usage

The main entry point for the library is the `ClassProxyFactory`.

### 1. Basic Usage: Tracking Simple Changes

To get started, instantiate a `ClassProxyFactory` and use it to create a proxy for your object. The factory returns a `State` object, which gives you access to the proxy and its change-tracking capabilities.

```java
// 1. Create a factory
ClassProxyFactory factory = new ClassProxyFactory();

// 2. Your original object
ExampleEntity user = new ExampleEntity();
user.setId(1L);
user.setName("John Doe");
user.setActive(true);

// 3. Create the stateful proxy
State<ExampleEntity> userState = factory.create(user);
ExampleEntity userProxy = userState.getProxy();

// 4. Check the initial state
System.out.println("Is user dirty? " + userState.isDirty()); // false

// 5. Modify the object through the proxy
userProxy.setName("Jane Doe");

// 6. Check the state again
System.out.println("Is user dirty? " + userState.isDirty()); // true

// 7. Inspect the changes
Map<Property, Object> changes = userState.getDifferentialState();
changes.forEach((prop, value) -> {
    System.out.println("Property '" + prop.getName() + "' changed to: " + value);
    // Output: Property 'name' changed to: Jane Doe
});

// 8. Clean up when you're done
factory.close();
```

### 2. Reverting Changes

You can easily undo all modifications and restore the object to its original state.

```java
// ... after making changes
System.out.println("Original name: " + user.getName()); // Jane Doe

// Revert all changes
userState.revert();

System.out.println("Is user dirty after revert? " + userState.isDirty()); // false
System.out.println("Name after revert: " + user.getName()); // John Doe
```

### 3. Deep Proxying: Nested Objects

The real power of the library lies in its ability to track changes in nested objects. If a proxied object contains another object, changes to the child will mark the parent as dirty.

```java
// Assuming Address is another trackable entity
Address address = new Address("123 Main St");
user.setAddress(address);

State<ExampleEntity> userState = factory.create(user);
ExampleEntity userProxy = userState.getProxy();

// Initially, nothing is dirty
System.out.println("Is user dirty? " + userState.isDirty()); // false

// Modify a property of the nested object
userProxy.getAddress().setStreet("456 Broad St");

// The parent is now considered dirty
System.out.println("Is user dirty after address change? " + userState.isDirty()); // true

// The differential state of the parent will show the changed Address object
Map<Property, Object> changes = userState.getDifferentialState();
changes.forEach((prop, value) -> {
    System.out.println(prop.getName() + " changed."); // "address" changed.
});
```

### 4. Working with Collections and Maps

Changes inside collections are also tracked automatically.

```java
State<ExampleEntity> userState = factory.create(user);
ExampleEntity userProxy = userState.getProxy();

userProxy.setTags(new ArrayList<>(Arrays.asList("Java", "Developer")));

System.out.println("Is dirty after setting tags? " + userState.isDirty()); // true

// Revert to clean state
userState.revert();
System.out.println("Is dirty after revert? " + userState.isDirty()); // false

// Now, modify the list's contents directly
userProxy.getTags().add("Proxy");

// The object is dirty again because a mutator method was called on the list
System.out.println("Is dirty after adding a tag? " + userState.isDirty()); // true
```

### 5. Customizing Behavior with `ProxyPolicy`

By default, the factory proxies most user-defined objects and skips common JDK value types (like `String`, `LocalDate`, etc.). You can provide your own policy to customize this behavior.

For example, here's a policy that prevents any object from the package `com.example.legacy` from being proxied:

```java
ProxyPolicy customPolicy = new ProxyPolicy() {
    @Override
    public boolean shouldProxy(Property property, Object object) {
        if (object != null && object.getClass().getPackageName().startsWith("com.example.legacy")) {
            return false; // Do not proxy objects from this package
        }
        // Fall back to the default logic for everything else
        return ProxyPolicy.DEFAULT.shouldProxy(property, object);
    }
};

// Use this policy when creating the factory
ClassProxyFactory factory = new ClassProxyFactory(customPolicy);
```

---

## How It Works

This library uses **ByteBuddy** to dynamically generate a subclass of your target class at runtime. This generated class overrides methods to intercept calls.

1.  **Class Generation & Caching**: The first time a class is proxied, a new proxy class is created and stored in a static, application-wide cache. This ensures the expensive class creation step only happens once.
2.  **Interception**: All method calls on a proxy instance are routed to an interceptor.
3.  **State Management**: Each proxy instance is associated with a unique `ClassProxyImpl` object, which holds its original state and tracks any differences.
4.  **Deep Proxying**: When a getter is called, the `ProxyPolicy` is consulted. If the returned value should be tracked (e.g., another domain object or a collection), the factory recursively creates a proxy for it.
5.  **Container Handling**: `List`, `Map`, and `Set` objects are wrapped using a standard Java `InvocationHandler` (`ContainerProxyHandler`) that specifically listens for mutator methods (`add`, `remove`, `put`, etc.) to mark the container as dirty.
