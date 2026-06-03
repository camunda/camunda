# Unified Configuration System

## Overview / What it is

The Unified Configuration system is a Spring Boot-based configuration mechanism that centralizes the configuration for the Camunda components (former standalone apps) under a common `camunda.*` property namespace. It replaces legacy, scattered properties (e.g., `zeebe.broker.*`) with a unified structure where each section is named after the logical role of the component, rather than the application-specific name (e.g., `camunda.data.secondary-storage.*` instead of the legacy `camunda.tasklist.elasticsearch.*`).

## What problems it solves

### Fragmented and repeated configuration

As the former standalone applications (Zeebe, Tasklist, Operate, Identity) have been merged together into the Orchestration Cluster, there was the need to gather the configuration for all the apps within a centralized (or unified) place. A good example of this is that instead of configuring Elasticsearch for each application, it is now configured once for all of them:

```
camunda.database.url=<url>
camunda.tasklist.elasticsearch.url=<url>
camunda.tasklist.zeebeElasticsearch.url=<url>
camunda.operate.elasticsearch.url=<url>
camunda.operate.zeebeElasticsearch.url=<url>
zeebe.broker.exporters.camundaexporter.args.connect.url=<url>
```

vs.

```
camunda.data.secondary-storage.elasticsearch.url=<url>
```

## Features

### Static declaration

The configuration fields are easily identified by navigating through the objects within the `Configuration` module. Each class is defined statically, has a `PREFIX` constant that shows the breadcrumb of the properties up to that point, and the usage of the fields are identifiable through the IDE's static analysis of the code.

### Validation

Each property is read by its standard getter. Each getter performs the validations for the property, even in relation with the legacy configuration keys that the property is replacing (e.g., when `camunda.data.secondary-storage.elasticsearch.url` is read, the system makes sure that the configuration is not ambiguous, compared to the legacy keys associated with the URL).

### Type checking

All of the configuration properties are well typed. This not only works with the most native types, such as `String` and `int`, but also with more complex types such as `Duration` and `DataSize`. While a legacy timeout property might be expressed as an `int`, without clarifying whether it had to be in seconds or millisecond, the adoption of precise types allows the user to specify the unit of measure at the moment of setting the property value. In the following example, reading the configuration key would not give an indication on whether the value had to be expressed in seconds or milliseconds, while with the configuration system, it is known that the duration property can be expressed with the final "s", or with any other needed unit.

```
camunda.operate.elasticsearch.socketTimeout=...
```

vs.

```
camunda.data.secondary-storage.elasticsearch.socket-timeout=10s
```

If the unit of measure is missing, then milliseconds are used by default.

### Backwards compatibility with legacy configuration keys

The Unified Configuration system replaces the legacy configuration properties. Nevertheless, in various cases, it is designed to work well even for Customers that have legacy configuration already defined, if possible.

Each unified configuration property defines the legacy configuration keys it relates to. For example, the new `camunda.data.secondary-storage.elasticsearch.bulk.size` is associated with a legacy configuration key `zeebe.broker.exporters.camundaexporter.args.bulk.size`. The [Public Documentation](https://docs.camunda.io/docs/next/self-managed/components/orchestration-cluster/core-settings/configuration/configuration-mapping/#about-unified-configuration-property-changes), for each configuration key, shows:

* what the legacy keys are
* whether the backwards compatibility is supported

The backwards compatibility is listed in the column "Type" and can be one of the following values:

##### New

The unified key is not associated with any legacy key

##### Direct mapping

The unified key is associated with legacy keys, and the backwards compatibility is fully supported: if the legacy keys are present in the configuration and the unified key is missing, the value is read from the legacy key.

##### Breaking change

The unified key is associated with legacy keys, but the legacy keys are no longer accepted. The only exception is when both the new and the legacy keys are present, and the values across the multiple declarations are identical.

### Kebab-case

While Spring Boot works with any available notation, the Unified Configuration is documented in kebab-case by default.

## How to use it (Developer's guide)

The Unified Configuration system works in a way that keeps it decoupled from the internal applications. The following PR will be used as concrete example that defines a new property, that does not replace any legacy configuration key (i.e., a brand new configuration): https://github.com/camunda/camunda/pull/53643

##### 1) Define the new variable

If a new configuration is being defined, (e.g., `camunda.data.secondary-storage.elasticsearch.perform-cleanup=true/false`), the a new `boolean` goes in `DocumentBasedSecondaryStorageDatabase.java`, with default value, getter and setter:

```java
private boolean performCleanup = false;

public boolean isPerformCleanup() {
    // add validation here, if any is wanted

    return this.performCleanup;
}

public void setPerformCleanup(final boolean performCleanup) {
    this.performCleanup = performCleanup;
}
```

##### 2) Define the corresponding internal variable

Depending on what the consumer of the config is, such consumer should have its own copy of the property. For example, if the consumer is the schema manager, then:

* `SchemaManagerConfiguration.java` gets the following code:

```java
private boolean performCleanup = false;

public boolean isPerformCleanup() {
    return performCleanup;
  }

  public void setPerformCleanup(final boolean performCleanup) {
    this.performCleanup = performCleanup;
  }
```

* `SearchEngineSchemaManagerPropertiesOverride.java` should be already defining an `override` object, that is used to propagate the new property from the Unified Configuration object to the internal consumer:

```java
unifiedConfiguration
    .getCamunda()
    .getData()
    .getSecondaryStorage()
    .getElasticsearchOrOpensearch()
    .ifPresent(
        secondaryStorage -> {
            override.setPerformCleanup(secondaryStorage.isPerformCleanup());
        }
    );
```

##### 3) Consume the property

The property can be consumed by the schema manager with

```java
config.schemaManager().isPerformCleanup()
```

where `config` is an instance of `SearchEngineConfiguration`.

##### 4) Association with a set of legacy keys

The case where a new property needs to be wired with existing, legacy properties, the getter of the new property in the Unified Configuration objects becomes as follows:

```java
public String getNewProperty() {              // replace String with the wanted property type
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".property-name",            // replace with new property name
        propertyName,                         // replace with variable for the new property
        String.class,                         // replace with the wanted property type
        BackwardsCompatibilityMode.SUPPORTED, // or SUPPORTED_ONLY_IF_VALUES_MATCH or NOT_SUPPORTED
        Set.of("key1", "key2", ...));         // legacy keys here
}
```

