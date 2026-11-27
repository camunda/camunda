/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.model.bpmn.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.camunda.bpm.model.xml.ModelParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BpmnParserSchemaLocationHintsTest {

  @Test
  void shouldIgnoreSchemaLocationHintForVendorNamespace(@TempDir final Path tempDir)
      throws IOException {
    // given
    final Path vendorSchema = tempDir.resolve("vendor.xsd");
    final String vendorSchemaXml =
        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
            + "    targetNamespace=\"http://vendor.example/schema\" elementFormDefault=\"qualified\">"
            + "  <xs:element name=\"property\">"
            + "    <xs:complexType>"
            + "      <xs:attribute name=\"name\" type=\"xs:string\" />"
            + "      <xs:attribute name=\"required\" type=\"xs:string\" use=\"required\" />"
            + "    </xs:complexType>"
            + "  </xs:element>"
            + "</xs:schema>";
    Files.write(vendorSchema, vendorSchemaXml.getBytes(StandardCharsets.UTF_8));
    final String xml =
        String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + "    xmlns:vendor=\"http://vendor.example/schema\""
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + "    xsi:schemaLocation=\"http://vendor.example/schema %s\""
                + "    id=\"definitions\" targetNamespace=\"http://bpmn.io/schema/bpmn\">"
                + "  <bpmn:process id=\"process\" isExecutable=\"true\">"
                + "    <bpmn:startEvent id=\"start\">"
                + "      <bpmn:extensionElements>"
                + "        <vendor:property name=\"color\" />"
                + "      </bpmn:extensionElements>"
                + "    </bpmn:startEvent>"
                + "  </bpmn:process>"
                + "</bpmn:definitions>",
            vendorSchema.toUri());

    // when / then
    assertThatCode(() -> parse(xml))
        .as(
            "Expected the model to parse: the schema in its schemaLocation rejects it,"
                + " but only the bundled BPMN schemas should count")
        .doesNotThrowAnyException();
  }

  @Test
  void shouldIgnoreSchemaLocationHintForDiagramNamespace(@TempDir final Path tempDir)
      throws IOException {
    // given
    final Path diagramSchema = tempDir.resolve("DC.xsd");
    final String diagramSchemaXml =
        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
            + "    targetNamespace=\"http://www.omg.org/spec/DD/20100524/DC\""
            + "    elementFormDefault=\"qualified\">"
            + "  <xs:element name=\"Bounds\">"
            + "    <xs:complexType>"
            + "      <xs:attribute name=\"required\" type=\"xs:string\" use=\"required\" />"
            + "    </xs:complexType>"
            + "  </xs:element>"
            + "</xs:schema>";
    Files.write(diagramSchema, diagramSchemaXml.getBytes(StandardCharsets.UTF_8));
    final String xml =
        String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + "    xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\""
                + "    xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\""
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + "    xsi:schemaLocation=\"http://www.omg.org/spec/DD/20100524/DC %s\""
                + "    id=\"definitions\" targetNamespace=\"http://bpmn.io/schema/bpmn\">"
                + "  <bpmn:process id=\"process\" isExecutable=\"true\">"
                + "    <bpmn:startEvent id=\"start\" />"
                + "  </bpmn:process>"
                + "  <bpmndi:BPMNDiagram id=\"diagram\">"
                + "    <bpmndi:BPMNPlane id=\"plane\" bpmnElement=\"process\">"
                + "      <bpmndi:BPMNShape id=\"start_di\" bpmnElement=\"start\">"
                + "        <dc:Bounds x=\"100\" y=\"100\" width=\"36\" height=\"36\" />"
                + "      </bpmndi:BPMNShape>"
                + "    </bpmndi:BPMNPlane>"
                + "  </bpmndi:BPMNDiagram>"
                + "</bpmn:definitions>",
            diagramSchema.toUri());

    // when / then
    assertThatCode(() -> parse(xml))
        .as(
            "Expected the model to parse: the schema in its schemaLocation rejects it,"
                + " but only the bundled BPMN schemas should count")
        .doesNotThrowAnyException();
  }

  @Test
  void shouldIgnoreNoNamespaceSchemaLocationHint(@TempDir final Path tempDir) throws IOException {
    // given
    final Path notesSchema = tempDir.resolve("notes.xsd");
    final String notesSchemaXml =
        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
            + "  <xs:element name=\"note\">"
            + "    <xs:complexType>"
            + "      <xs:attribute name=\"required\" type=\"xs:string\" use=\"required\" />"
            + "    </xs:complexType>"
            + "  </xs:element>"
            + "</xs:schema>";
    Files.write(notesSchema, notesSchemaXml.getBytes(StandardCharsets.UTF_8));
    final String xml =
        String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + "    xsi:noNamespaceSchemaLocation=\"%s\""
                + "    id=\"definitions\" targetNamespace=\"http://bpmn.io/schema/bpmn\">"
                + "  <bpmn:process id=\"process\" isExecutable=\"true\">"
                + "    <bpmn:startEvent id=\"start\">"
                + "      <bpmn:documentation><note /></bpmn:documentation>"
                + "    </bpmn:startEvent>"
                + "  </bpmn:process>"
                + "</bpmn:definitions>",
            notesSchema.toUri());

    // when / then
    assertThatCode(() -> parse(xml))
        .as(
            "Expected the model to parse: the schema in its noNamespaceSchemaLocation rejects"
                + " it, but only the bundled BPMN schemas should count")
        .doesNotThrowAnyException();
  }

  @Test
  void shouldIgnoreSchemaLocationHintOnNestedElement(@TempDir final Path tempDir)
      throws IOException {
    // given
    final Path vendorSchema = tempDir.resolve("vendor.xsd");
    final String vendorSchemaXml =
        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
            + "    targetNamespace=\"http://vendor.example/schema\" elementFormDefault=\"qualified\">"
            + "  <xs:element name=\"property\">"
            + "    <xs:complexType>"
            + "      <xs:attribute name=\"name\" type=\"xs:string\" />"
            + "      <xs:attribute name=\"required\" type=\"xs:string\" use=\"required\" />"
            + "    </xs:complexType>"
            + "  </xs:element>"
            + "</xs:schema>";
    Files.write(vendorSchema, vendorSchemaXml.getBytes(StandardCharsets.UTF_8));
    final String xml =
        String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + "    id=\"definitions\" targetNamespace=\"http://bpmn.io/schema/bpmn\">"
                + "  <bpmn:process id=\"process\" isExecutable=\"true\">"
                + "    <bpmn:startEvent id=\"start\""
                + "        xmlns:vendor=\"http://vendor.example/schema\""
                + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + "        xsi:schemaLocation=\"http://vendor.example/schema %s\">"
                + "      <bpmn:extensionElements>"
                + "        <vendor:property name=\"color\" />"
                + "      </bpmn:extensionElements>"
                + "    </bpmn:startEvent>"
                + "  </bpmn:process>"
                + "</bpmn:definitions>",
            vendorSchema.toUri());

    // when / then
    assertThatCode(() -> parse(xml))
        .as(
            "Expected the model to parse: the schema in its schemaLocation rejects it,"
                + " but only the bundled BPMN schemas should count")
        .doesNotThrowAnyException();
  }

  @Test
  void shouldIgnoreSchemaLocationHintForBpmnNamespace(@TempDir final Path tempDir)
      throws IOException {
    // given
    final Path bpmnSchema = tempDir.resolve("BPMN20.xsd");
    final String bpmnSchemaXml =
        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
            + "    targetNamespace=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
            + "    elementFormDefault=\"qualified\">"
            + "  <xs:element name=\"definitions\">"
            + "    <xs:complexType>"
            + "      <xs:attribute name=\"required\" type=\"xs:string\" use=\"required\" />"
            + "    </xs:complexType>"
            + "  </xs:element>"
            + "</xs:schema>";
    Files.write(bpmnSchema, bpmnSchemaXml.getBytes(StandardCharsets.UTF_8));
    final String xml =
        String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + "    xsi:schemaLocation=\"http://www.omg.org/spec/BPMN/20100524/MODEL %s\""
                + "    id=\"definitions\" targetNamespace=\"http://bpmn.io/schema/bpmn\">"
                + "  <bpmn:process id=\"process\" isExecutable=\"true\">"
                + "    <bpmn:startEvent id=\"start\" />"
                + "  </bpmn:process>"
                + "</bpmn:definitions>",
            bpmnSchema.toUri());

    // when / then
    assertThatCode(() -> parse(xml))
        .as(
            "Expected the model to parse: the schema in its schemaLocation rejects it,"
                + " but only the bundled BPMN schemas should count")
        .doesNotThrowAnyException();
  }

  @Test
  void shouldIgnoreSchemaLocationHintToMalformedSchema(@TempDir final Path tempDir)
      throws IOException {
    // given
    final Path vendorSchema = tempDir.resolve("vendor.xsd");
    Files.write(vendorSchema, "this is not a schema".getBytes(StandardCharsets.UTF_8));
    final String xml =
        String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + "    xmlns:vendor=\"http://vendor.example/schema\""
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + "    xsi:schemaLocation=\"http://vendor.example/schema %s\""
                + "    id=\"definitions\" targetNamespace=\"http://bpmn.io/schema/bpmn\">"
                + "  <bpmn:process id=\"process\" isExecutable=\"true\">"
                + "    <bpmn:startEvent id=\"start\">"
                + "      <bpmn:extensionElements>"
                + "        <vendor:property name=\"color\" />"
                + "      </bpmn:extensionElements>"
                + "    </bpmn:startEvent>"
                + "  </bpmn:process>"
                + "</bpmn:definitions>",
            vendorSchema.toUri());

    // when / then
    assertThatCode(() -> parse(xml))
        .as(
            "Expected the model to parse: the schema in its schemaLocation isn't valid XML,"
                + " but only the bundled BPMN schemas should count")
        .doesNotThrowAnyException();
  }

  @Test
  void shouldValidateAgainstBundledSchemaDespiteSchemaLocationHint(@TempDir final Path tempDir)
      throws IOException {
    // given
    final Path diagramSchema = tempDir.resolve("DC.xsd");
    final String diagramSchemaXml =
        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
            + "    targetNamespace=\"http://www.omg.org/spec/DD/20100524/DC\""
            + "    elementFormDefault=\"qualified\">"
            + "  <xs:element name=\"Bounds\">"
            + "    <xs:complexType>"
            + "      <xs:attribute name=\"x\" type=\"xs:double\" />"
            + "      <xs:attribute name=\"y\" type=\"xs:double\" />"
            + "      <xs:attribute name=\"width\" type=\"xs:double\" />"
            + "      <xs:attribute name=\"height\" type=\"xs:double\" />"
            + "    </xs:complexType>"
            + "  </xs:element>"
            + "</xs:schema>";
    Files.write(diagramSchema, diagramSchemaXml.getBytes(StandardCharsets.UTF_8));
    final String xml =
        String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + "    xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\""
                + "    xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\""
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + "    xsi:schemaLocation=\"http://www.omg.org/spec/DD/20100524/DC %s\""
                + "    id=\"definitions\" targetNamespace=\"http://bpmn.io/schema/bpmn\">"
                + "  <bpmn:process id=\"process\" isExecutable=\"true\">"
                + "    <bpmn:startEvent id=\"start\" />"
                + "  </bpmn:process>"
                + "  <bpmndi:BPMNDiagram id=\"diagram\">"
                + "    <bpmndi:BPMNPlane id=\"plane\" bpmnElement=\"process\">"
                + "      <bpmndi:BPMNShape id=\"start_di\" bpmnElement=\"start\">"
                + "        <dc:Bounds x=\"100\" y=\"100\" width=\"36\" />"
                + "      </bpmndi:BPMNShape>"
                + "    </bpmndi:BPMNPlane>"
                + "  </bpmndi:BPMNDiagram>"
                + "</bpmn:definitions>",
            diagramSchema.toUri());

    // when / then
    assertThatThrownBy(() -> parse(xml))
        .as(
            "Expected the model to be rejected: the schema in its schemaLocation accepts it,"
                + " but only the bundled BPMN schemas should count")
        .isInstanceOf(ModelParseException.class)
        .hasStackTraceContaining("cvc-complex-type.4")
        .hasStackTraceContaining("'height'");
  }

  @Test
  void shouldRejectUndeclaredElementInStrictWildcardDespiteSchemaLocationHint(
      @TempDir final Path tempDir) throws IOException {
    // given
    final Path vendorSchema = tempDir.resolve("vendor.xsd");
    final String vendorSchemaXml =
        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
            + "    targetNamespace=\"http://vendor.example/schema\" elementFormDefault=\"qualified\">"
            + "  <xs:element name=\"meta\">"
            + "    <xs:complexType />"
            + "  </xs:element>"
            + "</xs:schema>";
    Files.write(vendorSchema, vendorSchemaXml.getBytes(StandardCharsets.UTF_8));
    final String xml =
        String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + "    xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\""
                + "    xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\""
                + "    xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\""
                + "    xmlns:vendor=\"http://vendor.example/schema\""
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + "    xsi:schemaLocation=\"http://vendor.example/schema %s\""
                + "    id=\"definitions\" targetNamespace=\"http://bpmn.io/schema/bpmn\">"
                + "  <bpmn:process id=\"process\" isExecutable=\"true\">"
                + "    <bpmn:startEvent id=\"start\" />"
                + "  </bpmn:process>"
                + "  <bpmndi:BPMNDiagram id=\"diagram\">"
                + "    <bpmndi:BPMNPlane id=\"plane\" bpmnElement=\"process\">"
                + "      <bpmndi:BPMNShape id=\"shape\" bpmnElement=\"start\">"
                + "        <di:extension>"
                + "          <vendor:meta />"
                + "        </di:extension>"
                + "        <dc:Bounds x=\"100\" y=\"100\" width=\"36\" height=\"36\" />"
                + "      </bpmndi:BPMNShape>"
                + "    </bpmndi:BPMNPlane>"
                + "  </bpmndi:BPMNDiagram>"
                + "</bpmn:definitions>",
            vendorSchema.toUri());

    // when / then
    assertThatThrownBy(() -> parse(xml))
        .as(
            "Expected the model to be rejected: the schema in its schemaLocation declares the"
                + " element, but only the bundled BPMN schemas should count")
        .isInstanceOf(ModelParseException.class)
        .hasStackTraceContaining("cvc-complex-type.2.4.c")
        .hasStackTraceContaining("vendor:meta");
  }

  @Test
  void shouldRejectNonBpmnRootElementDespiteSchemaLocationHint(@TempDir final Path tempDir)
      throws IOException {
    // given
    final Path vendorSchema = tempDir.resolve("vendor.xsd");
    final String vendorSchemaXml =
        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
            + "    targetNamespace=\"http://vendor.example/schema\" elementFormDefault=\"qualified\">"
            + "  <xs:element name=\"root\">"
            + "    <xs:complexType />"
            + "  </xs:element>"
            + "</xs:schema>";
    Files.write(vendorSchema, vendorSchemaXml.getBytes(StandardCharsets.UTF_8));
    final String xml =
        String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<vendor:root xmlns:vendor=\"http://vendor.example/schema\""
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + "    xsi:schemaLocation=\"http://vendor.example/schema %s\" />",
            vendorSchema.toUri());

    // when / then
    assertThatThrownBy(() -> parse(xml))
        .as(
            "Expected the document to be rejected: the schema in its schemaLocation declares its"
                + " root element, but only the bundled BPMN schemas should count")
        .isInstanceOf(ModelParseException.class)
        .hasStackTraceContaining("cvc-elt.1.a")
        .hasStackTraceContaining("vendor:root");
  }

  @Test
  void shouldParseModelWithSchemaLocationHintToBpmnSpecification() {
    // given
    final String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
            + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
            + "    xsi:schemaLocation=\"http://www.omg.org/spec/BPMN/20100524/MODEL http://www.omg.org/spec/BPMN/2.0/20100501/BPMN20.xsd\""
            + "    id=\"definitions\" targetNamespace=\"http://bpmn.io/schema/bpmn\">"
            + "  <bpmn:process id=\"process\" isExecutable=\"true\">"
            + "    <bpmn:startEvent id=\"start\" />"
            + "  </bpmn:process>"
            + "</bpmn:definitions>";

    // when
    final BpmnModelInstance model = parse(xml);

    // then
    final StartEvent start = model.getModelElementById("start");
    assertThat(start).isNotNull();
  }

  private static BpmnModelInstance parse(final String xml) {
    return Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }
}
