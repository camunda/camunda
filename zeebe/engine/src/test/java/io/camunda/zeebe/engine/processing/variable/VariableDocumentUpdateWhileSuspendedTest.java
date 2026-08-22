/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Variable modification must remain available while a process instance is suspended (see #58089).
 * Follow-up token movement from conditional events is buffered until resume.
 */
public final class VariableDocumentUpdateWhileSuspendedTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldUpdateVariablesWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String jobType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("recoverable", "before"))
            .create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final var updated =
        ENGINE
            .variables()
            .ofScope(processInstanceKey)
            .withDocument(Map.of("recoverable", "after", "added", 1))
            .update();

    // then
    assertThat(updated.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(updated.getIntent()).isEqualTo(VariableDocumentIntent.UPDATED);
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("recoverable")
                .withValue("\"after\"")
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("added")
                .withValue("1")
                .exists())
        .isTrue();
    assertThat(ENGINE.getProcessingState().getSuspensionState().isSuspended(processInstanceKey))
        .isTrue();
  }

  @Test
  public void shouldBufferConditionalTriggerFromVariableUpdateUntilResume() {
    // given - suspended on an unsatisfied conditional intermediate catch event
    final String processId = Strings.newRandomValidBpmnId();
    final String catchEventId = "conditional-catch";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(catchEventId)
                .condition(c -> c.condition("=x > 10").zeebeVariableEvents("create, update"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariables(Map.of("x", 1)).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(catchEventId)
        .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - variable update satisfies the condition while suspended
    final var updated =
        ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 42)).update();

    // then - conditional TRIGGER is buffered rather than processed immediately
    final Record<?> buffered =
        RecordingExporter.records()
            .withValueType(ValueType.BUFFERED_COMMAND)
            .withIntent(BufferedCommandIntent.BUFFERED)
            .filter(
                r -> {
                  final var value = (BufferedCommandRecordValue) r.getValue();
                  return value.getProcessInstanceKey() == processInstanceKey
                      && value.getValueType() == ValueType.CONDITIONAL_SUBSCRIPTION
                      && value.getIntent() == ConditionalSubscriptionIntent.TRIGGER;
                })
            .getFirst();
    assertThat(buffered.getPosition()).isGreaterThan(updated.getPosition());
    assertThat(ENGINE.getProcessingState().getSuspensionState().isSuspended(processInstanceKey))
        .describedAs("variable update must not clear suspension")
        .isTrue();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - drain activates the catch event and completes the process
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(catchEventId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  /**
   * A user task scope drives the update lifecycle and can create an {@code updating} task listener
   * job. That job cannot be completed while suspended, so the task would stay in {@code UPDATING}
   * and the request would never complete. The command is rejected instead.
   */
  @Test
  public void shouldRejectVariableUpdateScopedAtUserTaskWhileSuspended() {
    // given - suspended on a Camunda user task with an updating task listener
    final String processId = Strings.newRandomValidBpmnId();
    final String listenerType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask(
                    "task",
                    t -> t.zeebeUserTask().zeebeTaskListener(l -> l.updating().type(listenerType)))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final Record<UserTaskRecordValue> userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final var rejection =
        ENGINE
            .variables()
            .ofScope(userTask.getValue().getElementInstanceKey())
            .withDocument(Map.of("x", 1))
            .expectRejection()
            .update();

    // then - the task is untouched and no listener job was created
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains(String.valueOf(processInstanceKey));
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= rejection.getPosition())
                .userTaskRecords()
                .withIntent(UserTaskIntent.UPDATING)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .describedAs("user task must not enter the update lifecycle while suspended")
        .isFalse();
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= rejection.getPosition())
                .jobRecords()
                .withIntent(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .describedAs("no updating task listener job must be created while suspended")
        .isFalse();
  }
}
