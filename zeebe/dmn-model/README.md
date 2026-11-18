# Zeebe DMN model API

A Java API to read, write, and validate DMN models. It supports DMN 1.1 to 1.5 and is built on
the XML model API (`zeebe/xml-model`).

## Origin

This module is a fork of `org.camunda.bpm.model:camunda-dmn-model` from Camunda 7. The Camunda 7
Community Edition reached end of life, its repository is archived, and `7.24.0` is the last release
on Maven Central, so bugs in that artifact can no longer be fixed upstream.

The sources were copied from
[`model-api/dmn-model`](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0/model-api/dmn-model)
at tag `7.24.0` (commit `2d8b7ed86ba912991613a99eea25ce7c486604d9`).

The `org.camunda.bpm.model.dmn` packages were kept on purpose. `dmn-scala`
(`org.camunda.bpm.extension.dmn.scala:dmn-engine`) compiles against them and depends on the original
artifact. `parent/pom.xml` and `zeebe/dmn/build.gradle.kts` exclude `org.camunda.bpm.model:*` from
`dmn-scala`. Do the same for anything else that brings the original artifacts in, so the classes are
never on the classpath twice.
