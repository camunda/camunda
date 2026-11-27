/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.ModelParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundledSchemaBpmnParserTest {

  @Test
  void shouldIgnoreSchemaLocationHintForBpmnNamespace(@TempDir final Path tempDir)
      throws IOException {
    // given
    final Path stricterSchemaFile = tempDir.resolve("stricter.xsd");
    Files.writeString(
        stricterSchemaFile,
        """
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
            targetNamespace="http://www.omg.org/spec/BPMN/20100524/MODEL"
            elementFormDefault="qualified">
          <xs:element name="definitions">
            <xs:complexType>
              <xs:sequence>
                <xs:any processContents="skip" minOccurs="0" maxOccurs="unbounded" />
              </xs:sequence>
              <xs:attribute name="required" type="xs:string" use="required" />
              <xs:anyAttribute processContents="skip" />
            </xs:complexType>
          </xs:element>
        </xs:schema>
        """);
    final String model =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://www.omg.org/spec/BPMN/20100524/MODEL %s"
            targetNamespace="http://example.com/bpmn">
          <process id="process" isExecutable="true">
            <startEvent id="start" />
          </process>
        </definitions>
        """
            .formatted(stricterSchemaFile.toUri());

    // when / then
    assertThatCode(() -> parse(model))
        .as(
            "Expected the model to parse: the schema in its schemaLocation rejects it, but only"
                + " the bundled BPMN schema should count.")
        .doesNotThrowAnyException();
  }

  @Test
  void shouldIgnoreSchemaLocationHintForDcNamespace(@TempDir final Path tempDir)
      throws IOException {
    // given
    final Path stricterSchemaFile = tempDir.resolve("stricter.xsd");
    Files.writeString(
        stricterSchemaFile,
        """
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
            targetNamespace="http://www.omg.org/spec/DD/20100524/DC"
            elementFormDefault="qualified">
          <xs:element name="Bounds">
            <xs:complexType>
              <xs:attribute name="required" type="xs:string" use="required" />
              <xs:anyAttribute processContents="skip" />
            </xs:complexType>
          </xs:element>
        </xs:schema>
        """);
    final String model =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
            xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://www.omg.org/spec/DD/20100524/DC %s"
            targetNamespace="http://example.com/bpmn">
          <process id="process" isExecutable="true">
            <startEvent id="start" />
          </process>
          <bpmndi:BPMNDiagram id="diagram">
            <bpmndi:BPMNPlane id="plane" bpmnElement="process">
              <bpmndi:BPMNShape id="start_di" bpmnElement="start">
                <dc:Bounds x="100" y="100" width="36" height="36" />
              </bpmndi:BPMNShape>
            </bpmndi:BPMNPlane>
          </bpmndi:BPMNDiagram>
        </definitions>
        """
            .formatted(stricterSchemaFile.toUri());

    // when / then
    assertThatCode(() -> parse(model))
        .as(
            "Expected the model to parse: the schema in its schemaLocation rejects it, but only"
                + " the bundled BPMN schema should count.")
        .doesNotThrowAnyException();
  }

  @Test
  void shouldIgnoreSchemaLocationHintForVendorNamespace(@TempDir final Path tempDir)
      throws IOException {
    // given
    final Path stricterSchemaFile = tempDir.resolve("stricter.xsd");
    Files.writeString(
        stricterSchemaFile,
        """
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
            targetNamespace="http://vendor.example.com/schema"
            elementFormDefault="qualified">
          <xs:element name="property">
            <xs:complexType>
              <xs:attribute name="required" type="xs:string" use="required" />
            </xs:complexType>
          </xs:element>
        </xs:schema>
        """);
    final String model =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:vendor="http://vendor.example.com/schema"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://vendor.example.com/schema %s"
            targetNamespace="http://example.com/bpmn">
          <process id="process" isExecutable="true">
            <extensionElements>
              <vendor:property />
            </extensionElements>
            <startEvent id="start" />
          </process>
        </definitions>
        """
            .formatted(stricterSchemaFile.toUri());

    // when / then
    assertThatCode(() -> parse(model))
        .as(
            "Expected the model to parse: the schema in its schemaLocation rejects it, but only"
                + " the bundled BPMN schema should count.")
        .doesNotThrowAnyException();
  }

  @Test
  void shouldIgnoreSchemaLocationHintOnNestedElement(@TempDir final Path tempDir)
      throws IOException {
    // given
    final Path stricterSchemaFile = tempDir.resolve("stricter.xsd");
    Files.writeString(
        stricterSchemaFile,
        """
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
            targetNamespace="http://vendor.example.com/schema"
            elementFormDefault="qualified">
          <xs:element name="property">
            <xs:complexType>
              <xs:attribute name="required" type="xs:string" use="required" />
            </xs:complexType>
          </xs:element>
        </xs:schema>
        """);
    final String model =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
            targetNamespace="http://example.com/bpmn">
          <process id="process" isExecutable="true">
            <startEvent id="start"
                xmlns:vendor="http://vendor.example.com/schema"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://vendor.example.com/schema %s">
              <extensionElements>
                <vendor:property />
              </extensionElements>
            </startEvent>
          </process>
        </definitions>
        """
            .formatted(stricterSchemaFile.toUri());

    // when / then
    assertThatCode(() -> parse(model))
        .as(
            "Expected the model to parse: the schema in its schemaLocation rejects it, but only"
                + " the bundled BPMN schema should count.")
        .doesNotThrowAnyException();
  }

  @Test
  void shouldIgnoreNoNamespaceSchemaLocationHint(@TempDir final Path tempDir) throws IOException {
    // given
    final Path stricterSchemaFile = tempDir.resolve("stricter.xsd");
    Files.writeString(
        stricterSchemaFile,
        """
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <xs:element name="note">
            <xs:complexType>
              <xs:attribute name="required" type="xs:string" use="required" />
            </xs:complexType>
          </xs:element>
        </xs:schema>
        """);
    final String model =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:noNamespaceSchemaLocation="%s"
            targetNamespace="http://example.com/bpmn">
          <process id="process" isExecutable="true">
            <documentation>
              <note xmlns="" />
            </documentation>
            <startEvent id="start" />
          </process>
        </definitions>
        """
            .formatted(stricterSchemaFile.toUri());

    // when / then
    assertThatCode(() -> parse(model))
        .as(
            "Expected the model to parse: the schema in its noNamespaceSchemaLocation rejects it,"
                + " but only the bundled BPMN schema should count.")
        .doesNotThrowAnyException();
  }

  @Test
  void shouldValidateAgainstBundledSchemaDespiteSchemaLocationHint(@TempDir final Path tempDir)
      throws IOException {
    // given
    final Path laxerSchemaFile = tempDir.resolve("laxer.xsd");
    Files.writeString(
        laxerSchemaFile,
        """
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
            targetNamespace="http://www.omg.org/spec/DD/20100524/DC"
            elementFormDefault="qualified">
          <xs:element name="Bounds">
            <xs:complexType>
              <xs:attribute name="x" type="xs:double" />
              <xs:attribute name="y" type="xs:double" />
              <xs:attribute name="width" type="xs:double" />
              <xs:attribute name="height" type="xs:double" />
            </xs:complexType>
          </xs:element>
        </xs:schema>
        """);
    final String model =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
            xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://www.omg.org/spec/DD/20100524/DC %s"
            targetNamespace="http://example.com/bpmn">
          <process id="process" isExecutable="true">
            <startEvent id="start" />
          </process>
          <bpmndi:BPMNDiagram id="diagram">
            <bpmndi:BPMNPlane id="plane" bpmnElement="process">
              <bpmndi:BPMNShape id="start_di" bpmnElement="start">
                <dc:Bounds x="100" y="100" width="36" />
              </bpmndi:BPMNShape>
            </bpmndi:BPMNPlane>
          </bpmndi:BPMNDiagram>
        </definitions>
        """
            .formatted(laxerSchemaFile.toUri());

    // when / then
    assertThatThrownBy(() -> parse(model))
        .as(
            "Expected the model to be rejected: the schema in its schemaLocation accepts it, but"
                + " only the bundled BPMN schema should count.")
        .isInstanceOf(ModelParseException.class)
        .hasStackTraceContaining("cvc-complex-type.4")
        .hasStackTraceContaining("'height'");
  }

  @Test
  void shouldParseModelWithSchemaLocationHintOfModelingTool() {
    // given
    final String model =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
            xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://www.omg.org/spec/BPMN/20100524/MODEL http://www.omg.org/spec/BPMN/2.0/20100501/BPMN20.xsd"
            targetNamespace="http://example.com/bpmn">
          <process id="process" name="Process" isExecutable="true">
            <startEvent id="start" />
          </process>
          <bpmndi:BPMNDiagram id="diagram">
            <bpmndi:BPMNPlane id="plane" bpmnElement="process">
              <bpmndi:BPMNShape id="start_di" bpmnElement="start">
                <dc:Bounds x="100" y="100" width="36" height="36" />
              </bpmndi:BPMNShape>
            </bpmndi:BPMNPlane>
          </bpmndi:BPMNDiagram>
        </definitions>
        """;

    // when
    final BpmnModelInstance modelInstance = parse(model);

    // then
    final Process process = modelInstance.getModelElementById("process");
    assertThat(process.getName()).isEqualTo("Process");
  }

  @Test
  void shouldRejectModelThatDoesNotMatchBundledSchema() {
    // given
    final String model =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
            targetNamespace="http://example.com/bpmn">
          <process id="process" isExecutable="true">
            <unknownElement id="unknown" />
          </process>
        </definitions>
        """;

    // when / then
    assertThatThrownBy(() -> parse(model))
        .isInstanceOf(ModelParseException.class)
        .hasMessage("SAXException while parsing input stream")
        .rootCause()
        .hasMessageContaining("cvc-complex-type.2.4.a")
        .hasMessageContaining("unknownElement");
  }

  private static BpmnModelInstance parse(final String model) {
    return BundledSchemaBpmnParser.readModelFromStream(
        new ByteArrayInputStream(model.getBytes(StandardCharsets.UTF_8)));
  }
}
