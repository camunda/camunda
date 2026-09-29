/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class UserTaskSuspendResumeTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldWriteUserTaskEventsWhenSuspendingAndResumingProcessInstance() {
    // given
    final long processInstanceKey = createInstanceWithUserTask();
    final var userTask = awaitUserTaskCreated(processInstanceKey);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - each task event is written before the instance event that ends its phase
    final var records = recordsUntil(processInstanceKey, ProcessInstanceIntent.RESUMED);
    assertThat(
            records.stream()
                .filter(
                    r ->
                        r.getValueType() == ValueType.USER_TASK
                            || r.getValueType() == ValueType.PROCESS_INSTANCE
                                && ((ProcessInstanceRecordValue) r.getValue()).getBpmnElementType()
                                    == BpmnElementType.PROCESS)
                .map(Record::getIntent))
        .containsSubsequence(
            ProcessInstanceIntent.SUSPENDING,
            UserTaskIntent.SUSPENDED,
            ProcessInstanceIntent.SUSPENDED,
            ProcessInstanceIntent.RESUMING,
            UserTaskIntent.RESUMED,
            ProcessInstanceIntent.RESUMED);
    assertThat(
            records.stream()
                .filter(r -> r.getValueType() == ValueType.BUFFERED_COMMAND)
                .filter(r -> r.getRecordType() == RecordType.EVENT)
                .filter(
                    r ->
                        ((BufferedCommandRecordValue) r.getValue()).getValueType()
                            == ValueType.USER_TASK)
                .map(
                    r -> {
                      final var value = (BufferedCommandRecordValue) r.getValue();
                      return tuple(r.getIntent(), value.getIntent(), value.getCommandKey());
                    }))
        .describedAs("the user task suspend and resume are each buffered, then drained")
        .containsExactly(
            tuple(BufferedCommandIntent.BUFFERED, UserTaskIntent.SUSPENDED, userTask.getKey()),
            tuple(BufferedCommandIntent.DRAINED, UserTaskIntent.SUSPENDED, userTask.getKey()),
            tuple(BufferedCommandIntent.BUFFERED, UserTaskIntent.RESUMED, userTask.getKey()),
            tuple(BufferedCommandIntent.DRAINED, UserTaskIntent.RESUMED, userTask.getKey()));
    final var suspended =
        RecordingExporter.userTaskRecords(UserTaskIntent.SUSPENDED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(suspended.getKey()).isEqualTo(userTask.getKey());
    assertThat(suspended.getValue().getUserTaskKey()).isEqualTo(userTask.getKey());
    assertThat(suspended.getValue().getElementInstanceKey())
        .isEqualTo(userTask.getValue().getElementInstanceKey());
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.RESUMED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getKey())
        .isEqualTo(userTask.getKey());
  }

  @Test
  public void shouldWriteUserTaskEventsForEveryUserTaskOfProcessInstance() {
    // given - one user task at the root and one nested in an embedded subprocess
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway("fork")
                .userTask("root-task")
                .zeebeUserTask()
                .endEvent()
                .moveToNode("fork")
                .subProcess(
                    "sub",
                    s ->
                        s.embeddedSubProcess().startEvent().userTask("nested-task").zeebeUserTask())
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final List<Long> userTaskKeys =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .map(Record::getKey)
            .toList();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.SUSPENDED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2)
                .map(Record::getKey))
        .containsExactlyInAnyOrderElementsOf(userTaskKeys);
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.RESUMED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2)
                .map(Record::getKey))
        .containsExactlyInAnyOrderElementsOf(userTaskKeys);
  }

  @Test
  public void shouldNotWriteUserTaskEventsForCalledChildInstance() {
    // given - suspension does not cascade into called child instances
    final String parentProcessId = Strings.newRandomValidBpmnId();
    final String childProcessId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .userTask()
                .zeebeUserTask()
                .endEvent()
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .deploy();
    final long parentInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();
    final long childInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withBpmnProcessId(childProcessId)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();
    awaitUserTaskCreated(childInstanceKey);

    // when
    ENGINE.processInstance().withInstanceKey(parentInstanceKey).suspend();
    ENGINE.processInstance().withInstanceKey(parentInstanceKey).resume();

    // then
    assertThat(
            RecordingExporter.records()
                .limit(
                    r ->
                        isProcessInstanceEvent(r, parentInstanceKey, ProcessInstanceIntent.RESUMED))
                .filter(
                    r ->
                        r.getIntent() == UserTaskIntent.SUSPENDED
                            || r.getIntent() == UserTaskIntent.RESUMED)
                .map(r -> ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey()))
        .doesNotContain(childInstanceKey);
  }

  @Test
  public void shouldResumeUserTaskOnceWhenResumeRestarts() {
    // given
    final long processInstanceKey = createInstanceWithUserTask();
    final long userTaskKey = awaitUserTaskCreated(processInstanceKey).getKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - the second RESUME is processed while RESUMING and restarts the drain
    ENGINE.writeRecords(resumeCommand(processInstanceKey), resumeCommand(processInstanceKey));

    // then - the buffered RESUME is drained once, so RESUMED is not repeated
    assertThat(
            recordsUntil(processInstanceKey, ProcessInstanceIntent.RESUMED).stream()
                .filter(r -> r.getIntent() == UserTaskIntent.RESUMED)
                .map(Record::getKey))
        .containsExactly(userTaskKey);
  }

  private static long createInstanceWithUserTask() {
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();
    return ENGINE.processInstance().ofBpmnProcessId(processId).create();
  }

  private static Record<UserTaskRecordValue> awaitUserTaskCreated(final long processInstanceKey) {
    return RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }

  /** Records of the process instance, up to its first event with the given intent. */
  private static List<Record<RecordValue>> recordsUntil(
      final long processInstanceKey, final ProcessInstanceIntent lastIntent) {
    return RecordingExporter.records()
        .limit(r -> isProcessInstanceEvent(r, processInstanceKey, lastIntent))
        .filter(
            r ->
                r.getValue() instanceof final ProcessInstanceRelated related
                    && related.getProcessInstanceKey() == processInstanceKey)
        .toList();
  }

  private static boolean isProcessInstanceEvent(
      final Record<RecordValue> record,
      final long processInstanceKey,
      final ProcessInstanceIntent intent) {
    return record.getValueType() == ValueType.PROCESS_INSTANCE
        && record.getIntent() == intent
        && record.getKey() == processInstanceKey;
  }

  private static RecordToWrite resumeCommand(final long processInstanceKey) {
    return RecordToWrite.command()
        .processInstance(
            ProcessInstanceIntent.RESUME,
            new ProcessInstanceRecord().setProcessInstanceKey(processInstanceKey))
        .key(processInstanceKey);
  }
}
