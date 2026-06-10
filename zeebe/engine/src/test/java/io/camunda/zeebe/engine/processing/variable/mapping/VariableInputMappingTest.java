/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class VariableInputMappingTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final AtomicInteger COUNTER = new AtomicInteger();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRespectMappingOrderWhenNestedPropertyReferencesLaterVariable() {
    // given
    final String processId = "proc" + COUNTER.incrementAndGet();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sub",
                sub ->
                    sub.zeebeInputExpression("\"value\"", "x")
                        .zeebeInputExpression("x", "a.b.c")
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent())
            .endEvent()
            .done();

    ENGINE_RULE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(processId).create();

    // then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(processId)
        .await();

    final String aValue =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("a")
            .getFirst()
            .getValue()
            .getValue();

    assertThat(aValue).isNotNull();
    final JsonNode aJson = parseJson(aValue);
    assertThat(aJson.path("b").path("c").asText()).isEqualTo("value");
  }

  @Test
  public void shouldRespectMappingOrderWithMultipleReferences() {
    // given
    final String processId = "proc" + COUNTER.incrementAndGet();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sub",
                sub ->
                    sub.zeebeInputExpression("\"value\"", "x")
                        .zeebeInputExpression("x", "y")
                        .zeebeInputExpression("{a: \"text\", b: {c: x}}", "z")
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent())
            .endEvent()
            .done();

    ENGINE_RULE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(processId).create();

    // then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(processId)
        .await();

    assertVariableValue(processInstanceKey, "x", "\"value\"");
    assertVariableValue(processInstanceKey, "y", "\"value\"");

    final String zValue =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("z")
            .getFirst()
            .getValue()
            .getValue();

    final JsonNode zJson = parseJson(zValue);
    assertThat(zJson.path("a").asText()).isEqualTo("text");
    assertThat(zJson.path("b").path("c").asText()).isEqualTo("value");
  }

  @Test
  public void shouldRespectMappingOrderWhenFirstMappingDefinesVariableUsedBySecond() {
    // given
    final String processId = "proc" + COUNTER.incrementAndGet();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sub",
                sub ->
                    sub.zeebeInputExpression("\"value\"", "x")
                        .zeebeInputExpression("x", "a.b.c")
                        .zeebeInputExpression("x", "y")
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent())
            .endEvent()
            .done();

    ENGINE_RULE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(processId).create();

    // then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(processId)
        .await();

    assertVariableValue(processInstanceKey, "x", "\"value\"");
    assertVariableValue(processInstanceKey, "y", "\"value\"");

    final String aValue =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("a")
            .getFirst()
            .getValue()
            .getValue();

    final JsonNode aJson = parseJson(aValue);
    assertThat(aJson.path("b").path("c").asText()).isEqualTo("value");
  }

  @Test
  public void shouldPreserveOrderEvenWithComplexNesting() {
    // given
    final String processId = "proc" + COUNTER.incrementAndGet();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sub",
                sub ->
                    sub.zeebeInputExpression("{a: \"text\", b: {c: x}}", "z")
                        .zeebeInputExpression("\"value\"", "x")
                        .zeebeInputExpression("x", "y")
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent())
            .endEvent()
            .done();

    ENGINE_RULE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(processId).create();

    // then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(processId)
        .await();

    assertVariableValue(processInstanceKey, "x", "\"value\"");
    assertVariableValue(processInstanceKey, "y", "\"value\"");

    final String zValue =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("z")
            .getFirst()
            .getValue()
            .getValue();

    final JsonNode zJson = parseJson(zValue);
    assertThat(zJson.path("a").asText()).isEqualTo("text");
    assertThat(zJson.path("b").path("c").isNull()).isTrue();
  }

  @Test
  public void shouldReferencePreviousMappingWithNestedTarget() {
    // given
    final String processId = "proc" + COUNTER.incrementAndGet();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sub",
                sub ->
                    sub.zeebeInputExpression("\"value\"", "nested.variable")
                        .zeebeInputExpression("nested.variable", "x")
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent())
            .endEvent()
            .done();

    ENGINE_RULE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(processId).create();

    // then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(processId)
        .await();

    assertVariableValue(processInstanceKey, "x", "\"value\"");

    final String nestedValue =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("nested")
            .getFirst()
            .getValue()
            .getValue();

    final JsonNode nestedJson = parseJson(nestedValue);
    assertThat(nestedJson.path("variable").asText()).isEqualTo("value");
  }

  private void assertVariableValue(
      final long processInstanceKey, final String variableName, final String expectedValue) {
    final String actualValue =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName(variableName)
            .getFirst()
            .getValue()
            .getValue();

    assertThat(actualValue).isEqualTo(expectedValue);
  }

  private JsonNode parseJson(final String json) {
    try {
      return OBJECT_MAPPER.readTree(json);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException("Failed to parse JSON: " + json, e);
    }
  }
}
