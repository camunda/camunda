/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.modelreader;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class ProcessModelReaderTest {

  @Test
  void shouldExtractFormKey() throws IOException {
    final String processId = "formProcess";
    final var bpmnBytes = parseBpmnResourceXml("process-with-form-key-reference.bpmn");

    final var reader = ProcessModelReader.of(bpmnBytes, processId);
    assertThat(reader).isPresent();
    final var formLink = reader.get().extractStartFormLink();
    assertThat(formLink).isPresent();

    assertThat(formLink.get().formKey()).isEqualTo("camunda-forms:bpmn:testForm");
    assertThat(formLink.get().formId()).isNull();
  }

  @Test
  void shouldExtractFormId() throws IOException {
    final String processId = "formProcess";
    final var bpmnBytes = parseBpmnResourceXml("process-with-form-id-reference.bpmn");

    final var reader = ProcessModelReader.of(bpmnBytes, processId);
    assertThat(reader).isPresent();
    final var formLink = reader.get().extractStartFormLink();
    assertThat(formLink).isPresent();

    assertThat(formLink.get().formId()).isEqualTo("testForm");
    assertThat(formLink.get().formKey()).isNull();
  }

  @Test
  void shouldExtractIsPublic() throws IOException {
    final String processId = "formProcess";
    final var bpmnBytes = parseBpmnResourceXml("process-with-form-key-reference.bpmn");

    final var reader = ProcessModelReader.of(bpmnBytes, processId);
    assertThat(reader).isPresent();
    final var isPublic = reader.get().extractIsPublicAccess();

    assertThat(isPublic).isTrue();
  }

  @Test
  void shouldNotExtractFormRelatedData() throws IOException {
    final String processId = "formProcess";
    final var bpmnBytes = parseBpmnResourceXml("process-without-form-reference.bpmn");

    final var reader = ProcessModelReader.of(bpmnBytes, processId);
    assertThat(reader).isPresent();

    final var formLink = reader.get().extractStartFormLink();
    final var isPublic = reader.get().extractIsPublicAccess();

    assertThat(formLink).isNotPresent();
    assertThat(isPublic).isFalse();
  }

  @Test
  void shouldExtractFlowNodes() throws IOException {
    final String processAId = "Process_A";
    final Map<String, String> processAFlowNodes =
        Map.of(
            "StartEvent_A",
            "Start Event A",
            "UserTask_A",
            "User Task A",
            "A1_CallActivity",
            "A1 Call Activity",
            "A2_CallActivity",
            "A2 Call Activity",
            "EndEvent_A",
            "End Event A");
    final String processBId = "Process_B";
    final Map<String, String> processBFlowNodes =
        Map.of(
            "StartEvent_B",
            "Start Event B",
            "UserTask_B",
            "User Task B",
            "B1_CallActivity",
            "B1 Call Activity",
            "B2_CallActivity",
            "B2 Call Activity",
            "EndEvent_B",
            "End Event B");

    final var bpmnBytes = parseBpmnResourceXml("two-processes.bpmn");

    final var processAModelReader = ProcessModelReader.of(bpmnBytes, processAId);
    assertThat(processAModelReader).isPresent();
    final var flowNodesA = processAModelReader.get().extractFlowNodes();
    assertThat(flowNodesA).hasSize(processAFlowNodes.keySet().size());
    processAFlowNodes.forEach(
        (id, name) -> {
          assertThat(flowNodesA.stream().anyMatch(fn -> fn.getId().equals(id))).isTrue();
          assertThat(flowNodesA.stream().anyMatch(fn -> fn.getName().equals(name))).isTrue();
        });

    final var processBModelReader = ProcessModelReader.of(bpmnBytes, processBId);
    assertThat(processBModelReader).isPresent();
    final var flowNodesB = processAModelReader.get().extractFlowNodes();
    assertThat(flowNodesB).hasSize(processBFlowNodes.keySet().size());
    processAFlowNodes.forEach(
        (id, name) -> {
          assertThat(flowNodesB.stream().anyMatch(fn -> fn.getId().equals(id))).isTrue();
          assertThat(flowNodesB.stream().anyMatch(fn -> fn.getName().equals(name))).isTrue();
        });
  }

  @Test
  void shouldExtractCallActivities() throws IOException {
    final String processAId = "Process_A";
    final Map<String, String> processACallActivities =
        Map.of("A1_CallActivity", "A1 Call Activity", "A2_CallActivity", "A2 Call Activity");
    final String processBId = "Process_B";
    final Map<String, String> processBCallActivities =
        Map.of("B1_CallActivity", "B1 Call Activity", "B2_CallActivity", "B2 Call Activity");

    final var bpmnBytes = parseBpmnResourceXml("two-processes.bpmn");

    final var processAModelReader = ProcessModelReader.of(bpmnBytes, processAId);
    assertThat(processAModelReader).isPresent();
    final var callActivitiesA = processAModelReader.get().extractCallActivities();
    assertThat(callActivitiesA).hasSize(processACallActivities.keySet().size());
    processACallActivities.forEach(
        (id, name) -> {
          assertThat(
                  callActivitiesA.stream()
                      .anyMatch(callActivity -> callActivity.getId().equals(id)))
              .isTrue();
          assertThat(
                  callActivitiesA.stream()
                      .anyMatch(callActivity -> callActivity.getName().equals(name)))
              .isTrue();
        });

    final var processBModelReader = ProcessModelReader.of(bpmnBytes, processBId);
    assertThat(processBModelReader).isPresent();
    final var callActivitiesB = processBModelReader.get().extractCallActivities();
    assertThat(callActivitiesB).hasSize(processBCallActivities.keySet().size());
    processBCallActivities.forEach(
        (id, name) -> {
          assertThat(
                  callActivitiesB.stream()
                      .anyMatch(callActivity -> callActivity.getId().equals(id)))
              .isTrue();
          assertThat(
                  callActivitiesB.stream()
                      .anyMatch(callActivity -> callActivity.getName().equals(name)))
              .isTrue();
        });
  }

  @Test
  void shouldExtractEmbeddedForm() throws IOException {
    final String processId = "embeddedFormProcess";
    final ObjectMapper objectMapper = new ObjectMapper();
    final Map<String, String> formSchemas =
        Map.of("my-embedded-form-one", formOneJson(), "my-embedded-form-two", formTwoJson());

    final var bpmnBytes = parseBpmnResourceXml("embedded-forms-process.bpmn");
    final var modelReader = ProcessModelReader.of(bpmnBytes, processId);

    assertThat(modelReader).isPresent();
    final var forms = modelReader.get().extractEmbeddedForms();
    assertThat(forms).isPresent();
    assertThat(forms.get()).hasSize(2);
    forms
        .get()
        .forEach(
            form -> {
              assertThat(formSchemas.containsKey(form.id())).isTrue();
              try {
                final JsonNode expected = objectMapper.readTree(formSchemas.get(form.id()));
                final JsonNode actual = objectMapper.readTree(form.schema());
                assertThat(expected.equals(actual)).isTrue();
              } catch (final JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Test
  void shouldExtractSubProcessFlowNodes() throws IOException {
    final String processId = "subprocess";
    final var bpmnBytes = parseBpmnResourceXml("process-with-subprocesses.bpmn");

    final var reader = ProcessModelReader.of(bpmnBytes, processId);
    assertThat(reader).isPresent();
    final List<String> expectedFlowNodes =
        List.of(
            "L0_StartEvent",
            "L0_CallActivity",
            "L0_UserTask",
            "L1_SubProcess",
            "L1_StartEvent",
            "L1_UserTask",
            "L2_SubProcess",
            "L2_StartEvent",
            "L2_UserTask",
            "L2_CallActivity",
            "L2_EndEvent",
            "L1_EndEvent",
            "L0_EndEvent");
    final var flowNodes = reader.get().extractFlowNodes();
    assertThat(flowNodes).hasSize(expectedFlowNodes.size());
    expectedFlowNodes.forEach(
        id ->
            assertThat(
                    flowNodes.stream()
                        .anyMatch(fn -> fn.getId().equals(id) && fn.getName().equals(id)))
                .isTrue());
  }

  @Test
  void shouldExtractSubProcessCallActivities() throws IOException {
    final String processId = "subprocess";
    final var bpmnBytes = parseBpmnResourceXml("process-with-subprocesses.bpmn");

    final var reader = ProcessModelReader.of(bpmnBytes, processId);
    assertThat(reader).isPresent();
    final List<String> expectedCallActivities = List.of("L0_CallActivity", "L2_CallActivity");
    final var flowNodes = reader.get().extractCallActivities();
    assertThat(flowNodes).hasSize(expectedCallActivities.size());
    expectedCallActivities.forEach(
        id ->
            assertThat(
                    flowNodes.stream()
                        .anyMatch(
                            callActivity ->
                                callActivity.getId().equals(id)
                                    && callActivity.getName().equals(id)))
                .isTrue());
  }

  private String formOneJson() {
    return """
{
  "components": [
    {
      "label": "Text field",
      "type": "textfield",
      "layout": {
        "row": "Row_14zv75q",
        "columns": null
      },
      "id": "Field_0h71kwj",
      "key": "textfield_t43iqk"
    }
  ],
  "type": "default",
  "id": "my-form",
  "executionPlatform": "Camunda Cloud",
  "executionPlatformVersion": "8.6.0",
  "versionTag": "3",
  "exporter": {
    "name": "Camunda Modeler",
    "version": "5.28.0"
  },
  "schemaVersion": 16
}""";
  }

  private String formTwoJson() {
    return """
{
  "components": [
    {
      "label": "Text field",
      "type": "textfield",
      "layout": {
        "row": "Row_14zv75q",
        "columns": null
      },
      "id": "Field_0h71kwj",
      "key": "textfield_t43iqk"
    }
  ],
  "type": "default",
  "id": "my-form",
  "executionPlatform": "Camunda Cloud",
  "executionPlatformVersion": "8.6.0",
  "versionTag": "3",
  "exporter": {
    "name": "Camunda Modeler",
    "version": "5.28.0"
  },
  "schemaVersion": 16
}""";
  }

  private byte[] parseBpmnResourceXml(final String resourceName) throws IOException {
    final ClassLoader classLoader = getClass().getClassLoader();
    final Path filePath =
        Paths.get(
            Objects.requireNonNull(classLoader.getResource("process/" + resourceName)).getPath());
    return Files.readAllBytes(filePath);
  }
}
