# Camunda Search Plugin SDK

## About

Camunda Search Plugin SDK allow extending search DB (ElasticSearch/OpenSearch) with custom
capabilities.

## Usage

To start using the Search Plugin SDK, include the latest version of SDK in your project.
Then implement interfaces required for your usa-case.

To include SDK in your project, one can do the following:

```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>camunda-search-client-plugin</artifactId>
  <version>${version.latest-camunda}</version>
</dependency>
```

### Use case: adding custom headers to calls

This use case may be applicable when one needs to add custom HTTP headers to search DB call, for
example authentication, tracking, debug, or other headers.

To implement the custom headers, implement the `DatabaseCustomHeaderSupplier` interface, then
build the `jar` from your project.

Follow the [relevant Zeebe instructions](https://docs.camunda.io/docs/next/self-managed/operational-guides/configure-db-custom-headers/)
to allow Zeebe loading your `jar` file. The implemented class will be registered as an HTTP interceptor.
