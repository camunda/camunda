/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class EscalationIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "wf";
  private static final String ESCALATION_CODE = "ESCALATION";
  private static final String THROW_ELEMENT_ID = "throw";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateIncidentIfEscalationCodeExpressionOfEndEventCannotBeEvaluated() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .endEvent(THROW_ELEMENT_ID, i -> i.escalationExpression("escalationCodeLookup"))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var endEvent =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.END_EVENT)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Expected result of the expression 'escalationCodeLookup' to be 'STRING', but was 'NULL'. \
            The evaluation reported the following warnings: \
            [NO_VARIABLE_FOUND] No variable found with name 'escalationCodeLookup'""")
        .hasBpmnProcessId(endEvent.getValue().getBpmnProcessId())
        .hasProcessDefinitionKey(endEvent.getValue().getProcessDefinitionKey())
        .hasProcessInstanceKey(endEvent.getValue().getProcessInstanceKey())
        .hasElementId(endEvent.getValue().getElementId())
        .hasElementInstanceKey(endEvent.getKey())
        .hasJobKey(-1)
        .hasVariableScopeKey(endEvent.getKey());
  }

  @Test
  public void shouldCreateIncidentIfEscalationCodeExpressionOfEndEventEvaluatesToWrongType() {
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .endEvent(THROW_ELEMENT_ID, i -> i.escalationExpression("escalationCodeLookup"))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("escalationCodeLookup", 25)
            .create();

    final Record<ProcessInstanceRecordValue> failureEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId(THROW_ELEMENT_ID)
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
            "Expected result of the expression 'escalationCodeLookup' to be 'STRING', but was 'NUMBER'.")
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId(THROW_ELEMENT_ID)
        .hasElementInstanceKey(failureEvent.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void
      shouldCreateIncidentIfEscalationCodeExpressionOfIntermediateEscalationEventCannotBeEvaluated() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateThrowEvent(
                THROW_ELEMENT_ID, i -> i.escalationExpression("escalationCodeLookup"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var intermediateThrowEvent =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.INTERMEDIATE_THROW_EVENT)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Expected result of the expression 'escalationCodeLookup' to be 'STRING', but was 'NULL'. \
            The evaluation reported the following warnings: \
            [NO_VARIABLE_FOUND] No variable found with name 'escalationCodeLookup'""")
        .hasBpmnProcessId(intermediateThrowEvent.getValue().getBpmnProcessId())
        .hasProcessDefinitionKey(intermediateThrowEvent.getValue().getProcessDefinitionKey())
        .hasProcessInstanceKey(intermediateThrowEvent.getValue().getProcessInstanceKey())
        .hasElementId(intermediateThrowEvent.getValue().getElementId())
        .hasElementInstanceKey(intermediateThrowEvent.getKey())
        .hasJobKey(-1)
        .hasVariableScopeKey(intermediateThrowEvent.getKey());
  }

  @Test
  public void
      shouldCreateIncidentIfEscalationCodeExpressionOfIntermediateEscalationEventEvaluatesToWrongType() {
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateThrowEvent(
                THROW_ELEMENT_ID, i -> i.escalationExpression("escalationCodeLookup"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("escalationCodeLookup", 25)
            .create();

    final Record<ProcessInstanceRecordValue> failureEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId(THROW_ELEMENT_ID)
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
            "Expected result of the expression 'escalationCodeLookup' to be 'STRING', but was 'NUMBER'.")
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId(THROW_ELEMENT_ID)
        .hasElementInstanceKey(failureEvent.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void shouldCreateIncidentOnEscalationCodeWithCustomTenant() {
    // given
    final String tenantId = "acme";
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .endEvent(THROW_ELEMENT_ID, i -> i.escalationExpression("escalationCodeLookup"))
            .done();
    ENGINE.deployment().withXmlResource(process).withTenantId(tenantId).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // then
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withTenantId(tenantId)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue()).hasTenantId(tenantId);
  }

  @Test
  public void shouldResolveIncidentIfEscalationCodeOfEndEventCouldNotBeEvaluated() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .endEvent(THROW_ELEMENT_ID, i -> i.escalationExpression("escalationCodeLookup"))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<IncidentRecordValue> incidentCreatedRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE
        .variables()
        .ofScope(incidentCreatedRecord.getValue().getElementInstanceKey())
        .withDocument(Map.of("escalationCodeLookup", ESCALATION_CODE))
        .update();

    // when
    final Record<IncidentRecordValue> incidentResolvedEvent =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreatedRecord.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .onlyEvents())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentCreatedRecord.getKey());
  }

  @Test
  public void
      shouldResolveIncidentIfEscalationCodeOfIntermediateEscalationEventCouldNotBeEvaluated() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateThrowEvent(
                THROW_ELEMENT_ID, i -> i.escalationExpression("escalationCodeLookup"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<IncidentRecordValue> incidentCreatedRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE
        .variables()
        .ofScope(incidentCreatedRecord.getValue().getElementInstanceKey())
        .withDocument(Map.of("escalationCodeLookup", ESCALATION_CODE))
        .update();

    // when
    final Record<IncidentRecordValue> incidentResolvedEvent =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreatedRecord.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .onlyEvents())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentCreatedRecord.getKey());
  }
}
