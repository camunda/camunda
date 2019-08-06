/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.incident;

import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ExpressionIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final byte[] VARIABLES;

  static {
    final DirectBuffer buffer =
        MsgPackUtil.encodeMsgPack(
            p -> {
              p.packMapHeader(1);
              p.packString("foo");
              p.packString("bar");
            });
    VARIABLES = new byte[buffer.capacity()];
    buffer.getBytes(0, VARIABLES);
  }

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
                .condition("foo < 5")
                .endEvent()
                .moveToLastGateway()
                .sequenceFlowId("s2")
                .condition("foo >= 5 && foo < 10")
                .endEvent()
                .done())
        .deploy();
  }

  @Test
  public void shouldCreateIncidentIfExclusiveGatewayHasNoMatchingCondition() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", 12).create();

    // then incident is created
    final Record<WorkflowInstanceRecordValue> failingEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementType(BpmnElementType.EXCLUSIVE_GATEWAY)
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final Record<IncidentRecordValue> incidentCommand =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATE)
            .getFirst();
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    assertThat(incidentCommand.getSourceRecordPosition()).isEqualTo(failingEvent.getPosition());

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.CONDITION_ERROR)
        .hasErrorMessage(
            "Expected at least one condition to evaluate to true, or to have a default flow")
        .hasBpmnProcessId("workflow")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("xor")
        .hasElementInstanceKey(failingEvent.getKey())
        .hasVariableScopeKey(failingEvent.getKey());
  }

  @Test
  public void shouldCreateIncidentIfConditionFailsToEvaluate() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", "bar").create();

    // then incident is created
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.CONDITION_ERROR)
        .hasErrorMessage(
            "Expected to evaluate condition 'foo >= 5 && foo < 10' successfully, but failed because: Cannot compare values of different types: STRING and INTEGER")
        .hasBpmnProcessId("workflow")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("xor");
  }

  @Test
  public void shouldResolveIncidentForFailedCondition() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", "bar").create();

    // then incident is created
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    final Record<WorkflowInstanceRecordValue> failureEvent =
        RecordingExporter.workflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    // when correct variables is used
    ENGINE
        .variables()
        .ofScope(failureEvent.getKey())
        .withDocument(Maps.of(entry("foo", 7)))
        .update();
    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    final List<Record<IncidentRecordValue>> incidentRecords =
        RecordingExporter.incidentRecords()
            .withRecordKey(incidentEvent.getKey())
            .limit(r -> r.getIntent() == IncidentIntent.RESOLVED)
            .collect(Collectors.toList());

    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceRecords =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    // RESOLVE triggers RESOLVED
    assertThat(incidentRecords)
        .extracting(m -> tuple(m.getRecordType(), m.getIntent()))
        .containsSubsequence(
            tuple(RecordType.COMMAND, IncidentIntent.RESOLVE),
            tuple(RecordType.EVENT, IncidentIntent.RESOLVED));

    // GATEWAY_ACTIVATED triggers SEQUENCE_FLOW_TAKEN, END_EVENT_OCCURED and COMPLETED
    assertThat(workflowInstanceRecords)
        .extracting(Record::getIntent)
        .containsSubsequence(
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldResolveIncidentForFailedConditionAfterUploadingWrongVariables() {
    // given
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", "bar").create();

    final long incidentKey =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst()
            .getKey();

    final long failedEventKey =
        RecordingExporter.workflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.EXCLUSIVE_GATEWAY)
            .getFirst()
            .getKey();

    ENGINE.variables().ofScope(failedEventKey).withDocument(Maps.of(entry("foo", 10))).update();
    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incidentKey).resolve();

    final Record<IncidentRecordValue> secondIncident =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skipUntil(r -> r.getIntent() == IncidentIntent.RESOLVED)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // when correct variables is used
    ENGINE.variables().ofScope(failedEventKey).withDocument(Maps.of(entry("foo", 7))).update();
    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(secondIncident.getKey()).resolve();

    // then
    final List<Record<IncidentRecordValue>> incidentRecords =
        RecordingExporter.incidentRecords()
            .withRecordKey(secondIncident.getKey())
            .skipUntil(r -> r.getIntent() == IncidentIntent.CREATED)
            .limit(r -> r.getIntent() == IncidentIntent.RESOLVED)
            .collect(Collectors.toList());

    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceRecords =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skipUntil(
                r ->
                    r.getIntent() == ELEMENT_COMPLETED
                        && r.getValue().getBpmnElementType() == BpmnElementType.EXCLUSIVE_GATEWAY)
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    // RESOLVE triggers RESOLVED
    assertThat(incidentRecords)
        .extracting(m -> tuple(m.getRecordType(), m.getIntent()))
        .containsSubsequence(
            tuple(RecordType.COMMAND, IncidentIntent.RESOLVE),
            tuple(RecordType.EVENT, IncidentIntent.RESOLVED));

    // SEQUENCE_FLOW_TAKEN triggers the rest of the process
    assertThat(workflowInstanceRecords)
        .extracting(Record::getIntent)
        .containsSubsequence(
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldResolveIncidentForExclusiveGatewayWithoutMatchingCondition() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", 12).create();

    // then incident is created
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    final Record<WorkflowInstanceRecordValue> failureEvent =
        RecordingExporter.workflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.EXCLUSIVE_GATEWAY)
            .getFirst();

    // when
    ENGINE
        .variables()
        .ofScope(failureEvent.getKey())
        .withDocument(Maps.of(entry("foo", 7)))
        .update();
    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withElementId("workflow")
                .withIntent(ELEMENT_COMPLETED)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResolveIncidentIfInstanceCanceled() {
    // given
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("workflow").withVariable("foo", "bar").create();

    // when
    assertThat(
            RecordingExporter.incidentRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withIntent(IncidentIntent.CREATED)
                .exists())
        .isTrue();
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then incident is resolved
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.RESOLVED)
            .getFirst();

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.CONDITION_ERROR)
        .hasErrorMessage(
            "Expected to evaluate condition 'foo >= 5 && foo < 10' successfully, but failed because: Cannot compare values of different types: STRING and INTEGER")
        .hasBpmnProcessId("workflow")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("xor");
  }
}
