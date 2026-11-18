# Zeebe XML model API

A Java API to read, write, and validate XML documents as typed models. The BPMN model
(`zeebe/bpmn-model`) and the DMN model (`zeebe/dmn-model`) are built on top of it.

## Origin

This module is a fork of `org.camunda.bpm.model:camunda-xml-model` from Camunda 7. The Camunda 7
Community Edition reached end of life, its repository is archived, and `7.24.0` is the last release
on Maven Central, so bugs in that artifact can no longer be fixed upstream.

The sources were copied from
[`model-api/xml-model`](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0/model-api/xml-model)
at tag `7.24.0` (commit `2d8b7ed86ba912991613a99eea25ce7c486604d9`).

The `org.camunda.bpm.model.xml` packages were kept on purpose. They are part of the public API of
`zeebe-bpmn-model`, and other libraries such as `dmn-scala` compile against them. Because of this, the
original artifact must never be on the same classpath as this module: exclude
`org.camunda.bpm.model:camunda-xml-model` wherever it comes in transitively.
