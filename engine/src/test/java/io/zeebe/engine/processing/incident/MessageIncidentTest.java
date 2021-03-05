/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.intent.ProcessInstanceSubscriptionIntent;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class MessageIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent(
              "catch",
              e -> e.message(m -> m.name("cancel").zeebeCorrelationKeyExpression("orderId")))
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void init() {
    ENGINE.deployment().withXmlResource(PROCESS).deploy();
  }

  private BpmnModelInstance createProcessWithMessageNameFeelExpression(final String processId) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .intermediateCatchEvent(
            "catch",
            e ->
                e.message(
                    m -> m.nameExpression("nameLookup").zeebeCorrelationKeyExpression("12345")))
        .done();
  }

  @Test
  public void shouldCreateIncidentIfNameExpressionCannotBeEvaluated() {
    ENGINE
        .deployment()
        .withXmlResource(createProcessWithMessageNameFeelExpression("UNRESOLVABLE_NAME_EXPRESSION"))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("UNRESOLVABLE_NAME_EXPRESSION").create();

    final Record<ProcessInstanceRecordValue> failureEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId("catch")
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression 'nameLookup': no variable found for name 'nameLookup'")
        .hasBpmnProcessId("UNRESOLVABLE_NAME_EXPRESSION")
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("catch")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void shouldCreateIncidentIfNameExpressionEvaluatesToWrongType() {
    ENGINE
        .deployment()
        .withXmlResource(createProcessWithMessageNameFeelExpression("NAME_EXPRESSION_INVALID_TYPE"))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("NAME_EXPRESSION_INVALID_TYPE")
            .withVariable("nameLookup", 25)
            .create();

    final Record<ProcessInstanceRecordValue> failureEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId("catch")
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression 'nameLookup' to be 'STRING', but was 'NUMBER'.")
        .hasBpmnProcessId("NAME_EXPRESSION_INVALID_TYPE")
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("catch")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void shouldResolveIncidentIfNameCouldNotBeEvaluated() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            createProcessWithMessageNameFeelExpression("UNRESOLVABLE_NAME_EXPRESSION2"))
        .deploy();

    final long processInstance =
        ENGINE.processInstance().ofBpmnProcessId("UNRESOLVABLE_NAME_EXPRESSION2").create();

    final Record<IncidentRecordValue> incidentCreatedRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstance)
            .getFirst();

    ENGINE
        .variables()
        .ofScope(incidentCreatedRecord.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("nameLookup", "messageName")))
        .update();

    // when
    final Record<IncidentRecordValue> incidentResolvedEvent =
        ENGINE
            .incident()
            .ofInstance(processInstance)
            .withKey(incidentCreatedRecord.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.processInstanceSubscriptionRecords(
                    ProcessInstanceSubscriptionIntent.OPENED)
                .withProcessInstanceKey(processInstance)
                .exists())
        .isTrue();

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentCreatedRecord.getKey());
  }

  @Test
  public void shouldCreateIncidentIfCorrelationKeyNotFound() {
    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<ProcessInstanceRecordValue> failureEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId("catch")
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression 'orderId': no variable found for name 'orderId'")
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("catch")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void shouldCreateIncidentIfCorrelationKeyOfInvalidType() {
    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("orderId", true).create();

    final Record<ProcessInstanceRecordValue> failureEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("catch")
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Failed to extract the correlation key for 'orderId': The value must be either a string or a number, but was BOOLEAN.")
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("catch")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void shouldResolveIncidentIfCorrelationKeyNotFound() {
    // given
    final long processInstance = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<IncidentRecordValue> incidentCreatedRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstance)
            .getFirst();

    ENGINE
        .variables()
        .ofScope(incidentCreatedRecord.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("orderId", "order123")))
        .update();

    // when
    final Record<IncidentRecordValue> incidentResolvedEvent =
        ENGINE
            .incident()
            .ofInstance(processInstance)
            .withKey(incidentCreatedRecord.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.processInstanceSubscriptionRecords(
                    ProcessInstanceSubscriptionIntent.OPENED)
                .withProcessInstanceKey(processInstance)
                .exists())
        .isTrue();

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentCreatedRecord.getKey());
  }
}
