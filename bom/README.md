# Zeebe BOM

Contains versions for all Zeebe modules. It can be imported using maven by
adding the following dependency to the `dependencyManagement` section of your
pom.

```xml
<dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.zeebe</groupId>
        <artifactId>zeebe-bom</artifactId>
        <version>X.Y.Z</version>
        <scope>import</scope>
        <type>pom</type>
      </dependency>
    </dependencies>
</dependencyManagement>
```
