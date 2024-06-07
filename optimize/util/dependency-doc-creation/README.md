
This projects creates enables you to automatically create a third party dependencies with there licenses report.

# Prerequisites

* node (check [here](https://github.com/camunda/camunda-optimize/blob/master/client/README.md) for the version)
* maven 3+
* bash
* java 11

# How to

In order to create the files, just run the dedicated script:
```
sh ./createOptimizeDependencyFiles.sh
```

If you just want to create the back-end dependencies, you can run:
```
mvn exec:java -Dexec.mainClass="org.camunda.optimize.MarkDownDependencyCreator" -Dexec.args=PATH_TO_LICENSE_FILE
```
where PATH_TO_LICENSE_FILE needs to be substituted with the path where the license information of the back-end dependencies lies.