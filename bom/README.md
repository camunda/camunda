# Zeebe BOM

The Bill of Materials (BOM) is the recommended way for third-party projects to import Zeebe
dependencies. It bundles dependencies that we consider consumable.

These dependencies receive special attention from the team when it comes to backwards compatibility.
The team will try to maintain backwards compatibility for these libraries, but this does not mean
that we guarantee it. For our official backwards compatibility guarantees, please refer to the
[Public API documentation](https://docs.camunda.io/docs/apis-clients/public-api/).

## Usage

The BOM can be imported using maven by adding the following dependency to the `dependencyManagement`
section of your pom.

```xml
<dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.camunda</groupId>
        <artifactId>zeebe-bom</artifactId>
        <version>X.Y.Z</version>
        <scope>import</scope>
        <type>pom</type>
      </dependency>
    </dependencies>
</dependencyManagement>
```

Once imported, you can easily add the dependencies you need to the `dependencies` section of your pom.

For example, you can use it to import the
[Camunda Java Client](https://docs.camunda.io/docs/apis-clients/java-client/).

```xml
<dependencies>
  <dependency>
    <groupId>io.camunda</groupId>
    <artifactId>camunda-client-java</artifactId>
  </dependency>
</dependencies>
```

Also, you can use it to import the
Spring SDK

```xml
<dependencies>
  <dependency>
    <groupId>io.camunda</groupId>
    <artifactId>spring-boot-starter-camunda-sdk</artifactId>
  </dependency>
</dependencies>
```

