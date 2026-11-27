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
import org.camunda.bpm.model.dmn.DmnModelException;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BundledSchemaDmnParserTest {

  @Test
  void shouldIgnoreSchemaLocationHintForDmnNamespace(@TempDir final Path tempDir)
      throws IOException {
    // given
    final Path stricterSchemaFile = tempDir.resolve("stricter.xsd");
    Files.writeString(
        stricterSchemaFile,
        """
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
            targetNamespace="https://www.omg.org/spec/DMN/20191111/MODEL/"
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
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="https://www.omg.org/spec/DMN/20191111/MODEL/ %s"
            id="definitions" name="Definitions" namespace="http://example.com/dmn">
          <decision id="decision" name="Decision">
            <literalExpression id="expression">
              <text>"result"</text>
            </literalExpression>
          </decision>
        </definitions>
        """
            .formatted(stricterSchemaFile.toUri());

    // when / then
    assertThatCode(() -> parse(model))
        .as(
            "Expected the model to parse: the schema in its schemaLocation rejects it, but only"
                + " the bundled DMN schemas should count.")
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
            targetNamespace="http://www.omg.org/spec/DMN/20180521/DC/"
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
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
            xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/"
            xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://www.omg.org/spec/DMN/20180521/DC/ %s"
            id="definitions" name="Definitions" namespace="http://example.com/dmn">
          <decision id="decision" name="Decision">
            <literalExpression id="expression">
              <text>"result"</text>
            </literalExpression>
          </decision>
          <dmndi:DMNDI>
            <dmndi:DMNDiagram id="diagram">
              <dmndi:DMNShape id="decision_di" dmnElementRef="decision">
                <dc:Bounds x="100" y="100" width="180" height="80" />
              </dmndi:DMNShape>
            </dmndi:DMNDiagram>
          </dmndi:DMNDI>
        </definitions>
        """
            .formatted(stricterSchemaFile.toUri());

    // when / then
    assertThatCode(() -> parse(model))
        .as(
            "Expected the model to parse: the schema in its schemaLocation rejects it, but only"
                + " the bundled DMN schemas should count.")
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
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
            xmlns:vendor="http://vendor.example.com/schema"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://vendor.example.com/schema %s"
            id="definitions" name="Definitions" namespace="http://example.com/dmn">
          <decision id="decision" name="Decision">
            <extensionElements>
              <vendor:property />
            </extensionElements>
            <literalExpression id="expression">
              <text>"result"</text>
            </literalExpression>
          </decision>
        </definitions>
        """
            .formatted(stricterSchemaFile.toUri());

    // when / then
    assertThatCode(() -> parse(model))
        .as(
            "Expected the model to parse: the schema in its schemaLocation rejects it, but only"
                + " the bundled DMN schemas should count.")
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
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
            id="definitions" name="Definitions" namespace="http://example.com/dmn">
          <decision id="decision" name="Decision"
              xmlns:vendor="http://vendor.example.com/schema"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="http://vendor.example.com/schema %s">
            <extensionElements>
              <vendor:property />
            </extensionElements>
            <literalExpression id="expression">
              <text>"result"</text>
            </literalExpression>
          </decision>
        </definitions>
        """
            .formatted(stricterSchemaFile.toUri());

    // when / then
    assertThatCode(() -> parse(model))
        .as(
            "Expected the model to parse: the schema in its schemaLocation rejects it, but only"
                + " the bundled DMN schemas should count.")
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
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
            xmlns:vendor="http://vendor.example.com/schema"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:noNamespaceSchemaLocation="%s"
            id="definitions" name="Definitions" namespace="http://example.com/dmn">
          <decision id="decision" name="Decision">
            <extensionElements>
              <vendor:wrapper>
                <note xmlns="" />
              </vendor:wrapper>
            </extensionElements>
            <literalExpression id="expression">
              <text>"result"</text>
            </literalExpression>
          </decision>
        </definitions>
        """
            .formatted(stricterSchemaFile.toUri());

    // when / then
    assertThatCode(() -> parse(model))
        .as(
            "Expected the model to parse: the schema in its noNamespaceSchemaLocation rejects it,"
                + " but only the bundled DMN schemas should count.")
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
            targetNamespace="http://www.omg.org/spec/DMN/20180521/DC/"
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
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
            xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/"
            xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://www.omg.org/spec/DMN/20180521/DC/ %s"
            id="definitions" name="Definitions" namespace="http://example.com/dmn">
          <decision id="decision" name="Decision">
            <literalExpression id="expression">
              <text>"result"</text>
            </literalExpression>
          </decision>
          <dmndi:DMNDI>
            <dmndi:DMNDiagram id="diagram">
              <dmndi:DMNShape id="decision_di" dmnElementRef="decision">
                <dc:Bounds x="100" y="100" width="180" />
              </dmndi:DMNShape>
            </dmndi:DMNDiagram>
          </dmndi:DMNDI>
        </definitions>
        """
            .formatted(laxerSchemaFile.toUri());

    // when / then
    assertThatThrownBy(() -> parse(model))
        .as(
            "Expected the model to be rejected: the schema in its schemaLocation accepts it, but"
                + " only the bundled DMN schemas should count.")
        .isInstanceOf(DmnModelException.class)
        .hasMessage("Unable to parse model")
        .hasStackTraceContaining("cvc-complex-type.4")
        .hasStackTraceContaining("'height'");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://www.omg.org/spec/DMN/20151101/dmn.xsd",
        "http://www.omg.org/spec/DMN/20151101/dmn11.xsd",
        "http://www.omg.org/spec/DMN/20180521/MODEL/",
        "https://www.omg.org/spec/DMN/20191111/MODEL/",
        "https://www.omg.org/spec/DMN/20211108/MODEL/",
        "https://www.omg.org/spec/DMN/20230324/MODEL/"
      })
  void shouldParseModelOfEachSupportedDmnVersion(final String dmnNamespace) {
    // given
    final String model =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="%s"
            id="definitions" name="Definitions" namespace="http://example.com/dmn">
          <decision id="decision" name="Decision">
            <literalExpression id="expression">
              <text>"result"</text>
            </literalExpression>
          </decision>
        </definitions>
        """
            .formatted(dmnNamespace);

    // when
    final DmnModelInstance modelInstance = parse(model);

    // then
    final Decision decision = modelInstance.getModelElementById("decision");
    assertThat(decision.getName()).isEqualTo("Decision");
  }

  @Test
  void shouldRejectModelThatDoesNotMatchBundledSchema() {
    // given
    final String model =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
            id="definitions" name="Definitions" namespace="http://example.com/dmn">
          <decision id="decision" name="Decision">
            <unknownElement id="unknown" />
          </decision>
        </definitions>
        """;

    // when / then
    assertThatThrownBy(() -> parse(model))
        .isInstanceOf(DmnModelException.class)
        .hasMessage("Unable to parse model")
        .rootCause()
        .hasMessageContaining("cvc-complex-type.2.4.a")
        .hasMessageContaining("unknownElement");
  }

  private static DmnModelInstance parse(final String model) {
    return BundledSchemaDmnParser.readModelFromStream(
        new ByteArrayInputStream(model.getBytes(StandardCharsets.UTF_8)));
  }
}
