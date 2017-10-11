# Zeebe BOM

Contains versions for all Zeebe modules. It can be imported using maven by
adding the following dependency to the `dependencyManagement` section of your
pom.

```xml
<dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.zeebe</groupId>
        <artifactId>zb-bom</artifactId>
        <version>X.Y.Z</version>
        <scope>import</scope>
        <type>pom</type>
      </dependency>
    </dependencies>
</dependencyManagement>
```

## DISCLAIMER

This project is work in progress and currently NOT meant for production use!

## Code of Conduct

This project adheres to the Contributor Covenant [Code of
Conduct](/CODE_OF_CONDUCT.md). By participating, you are expected to uphold
this code. Please report unacceptable behavior to code-of-conduct@zeebe.io.

## License

Most Zeebe source files are made available under the [Apache License, Version
2.0](/LICENSE) except for the [broker-core][] component. The [broker-core][]
source files are made available under the terms of the [GNU Affero General
Public License (GNU AGPLv3)][agpl]. See individual source files for
details.

[broker-core]: https://github.com/zeebe-io/zeebe/tree/master/broker-core
[agpl]: https://github.com/zeebe-io/zeebe/blob/master/GNU-AGPL-3.0
