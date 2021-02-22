/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ConditionIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void init() {

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("workflow")
                .startEvent()
                .exclusiveGateway("xor")
                .sequenceFlowId("s1")
                .conditionExpression("foo < 5")
                .endEvent()
                .moveToLastGateway()
                .sequenceFlowId("s2")
                .conditionExpression("foo > 10")
                .endEvent()
                .done())
        .deploy();
  }

  @Test
  public void shouldCreateIncidentIfExclusiveGatewayHasNoMatchingCondition() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", 9).create();

    // then
    final Record<WorkflowInstanceRecordValue> failingEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementType(BpmnElementType.EXCLUSIVE_GATEWAY)
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.CONDITION_ERROR)
        .hasErrorMessage(
            "Expected at least one condition to evaluate to true, or to have a default flow")
        .hasBpmnProcessId(failingEvent.getValue().getBpmnProcessId())
        .hasWorkflowInstanceKey(failingEvent.getValue().getWorkflowInstanceKey())
        .hasElementId(failingEvent.getValue().getElementId())
        .hasElementInstanceKey(failingEvent.getKey())
        .hasVariableScopeKey(failingEvent.getKey());
  }

  @Test
  public void shouldCreateIncidentIfConditionFailsToEvaluate() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", "bar").create();

    // then
    final Record<WorkflowInstanceRecordValue> failingEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementType(BpmnElementType.EXCLUSIVE_GATEWAY)
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression 'foo > 10': ValString(bar) can not be compared to ValNumber(10)")
        .hasBpmnProcessId(failingEvent.getValue().getBpmnProcessId())
        .hasWorkflowInstanceKey(failingEvent.getValue().getWorkflowInstanceKey())
        .hasElementId(failingEvent.getValue().getElementId())
        .hasElementInstanceKey(failingEvent.getKey())
        .hasVariableScopeKey(failingEvent.getKey());
  }

  @Test
  public void shouldResolveIncidentForFailedCondition() {
    // given
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", "bar").create();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // when
    ENGINE
        .variables()
        .ofScope(incidentEvent.getValue().getVariableScopeKey())
        .withDocument(Maps.of(entry("foo", 11)))
        .update();

    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .onlyEvents()
                .withRecordKey(incidentEvent.getValue().getElementInstanceKey())
                .limit(2))
        .extracting(Record::getIntent)
        .contains(WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    assertThat(
            RecordingExporter.records()
                .onlyEvents()
                .limitToWorkflowInstance(workflowInstanceKey)
                .incidentRecords())
        .extracting(Record::getIntent)
        .hasSize(2)
        .containsExactly(IncidentIntent.CREATED, IncidentIntent.RESOLVED);
  }

  @Test
  public void shouldResolveIncidentForExclusiveGatewayWithoutMatchingCondition() {
    // given
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", 7).create();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();
    // when
    ENGINE
        .variables()
        .ofScope(incidentEvent.getValue().getVariableScopeKey())
        .withDocument(Maps.of(entry("foo", 11)))
        .update();

    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .onlyEvents()
                .withRecordKey(incidentEvent.getValue().getElementInstanceKey())
                .limit(2))
        .extracting(Record::getIntent)
        .contains(WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    assertThat(
            RecordingExporter.records()
                .onlyEvents()
                .limitToWorkflowInstance(workflowInstanceKey)
                .incidentRecords())
        .extracting(Record::getIntent)
        .hasSize(2)
        .containsExactly(IncidentIntent.CREATED, IncidentIntent.RESOLVED);
  }
}
