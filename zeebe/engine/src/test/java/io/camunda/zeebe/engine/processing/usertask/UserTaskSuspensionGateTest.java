/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class UserTaskSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectUserTaskCompleteWhileSuspended() {
    // given
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
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<UserTaskRecordValue> rejection =
        ENGINE.userTask().ofInstance(processInstanceKey).expectRejection().complete();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(UserTaskIntent.COMPLETE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectUserTaskCompleteWhileSuspending() {
    // given
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
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    seedSuspending(processInstanceKey);

    // when
    final Record<UserTaskRecordValue> rejection =
        ENGINE.userTask().ofInstance(processInstanceKey).expectRejection().complete();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(UserTaskIntent.COMPLETE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectUserTaskClaimWhileSuspended() {
    // given
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
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<UserTaskRecordValue> rejection =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAssignee("user")
            .expectRejection()
            .claim();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(UserTaskIntent.CLAIM)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectUserTaskAssignWhileSuspended() {
    // given
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
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<UserTaskRecordValue> rejection =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAssignee("user")
            .expectRejection()
            .assign();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(UserTaskIntent.ASSIGN)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectUserTaskUpdateWhileSuspended() {
    // given
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
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<UserTaskRecordValue> rejection =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAllAttributesChanged()
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(UserTaskIntent.UPDATE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldCancelUserTaskWhenInstanceTerminatedWhileSuspended() {
    // given
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
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectTaskListenerJobCompletionWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String listenerType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeTaskListener(l -> l.completing().type(listenerType))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    final long listenerJobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(listenerType)
            .getFirst()
            .getKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(listenerJobKey).expectRejection().complete();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.COMPLETE)
        .hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldResumeUserTasksBeforeProcessInstanceIsResumed() {
    // given - task "b" waits on a creating listener, so it stays in CREATING
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway("fork")
                .userTask("a", t -> t.zeebeUserTask())
                .endEvent()
                .moveToNode("fork")
                .userTask(
                    "b",
                    t -> t.zeebeUserTask().zeebeTaskListener(l -> l.creating().type(processId)))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long taskAKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    final List<Long> taskKeys =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATING)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .map(Record::getKey)
            .toList();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then
    final var userTaskRecords =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getIntent() == ProcessInstanceIntent.RESUMED
                        && r.getKey() == processInstanceKey)
            .userTaskRecords()
            .withProcessInstanceKey(processInstanceKey)
            .asList();
    final var suspendedKeys = keysWithIntent(userTaskRecords, UserTaskIntent.SUSPENDED);
    assertThat(suspendedKeys).containsExactlyInAnyOrderElementsOf(taskKeys);
    assertThat(keysWithIntent(userTaskRecords, UserTaskIntent.RESUMED))
        .describedAs("Expect one RESUMED per task, in suspension order, before the instance")
        .containsExactlyElementsOf(suspendedKeys);

    // and - the task kept its lifecycle state, so it can still be completed
    ENGINE.userTask().withKey(taskAKey).complete();
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.COMPLETED)
                .withRecordKey(taskAKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectResumeOfMissingUserTask() {
    // given - the task completed after its RESUME was buffered
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", t -> t.zeebeUserTask())
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long userTaskKey =
        ENGINE.userTask().ofInstance(processInstanceKey).complete().getValue().getUserTaskKey();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .userTask(
                UserTaskIntent.RESUME,
                new UserTaskRecord()
                    .setUserTaskKey(userTaskKey)
                    .setProcessInstanceKey(processInstanceKey))
            .key(userTaskKey));

    // then
    final var rejection =
        RecordingExporter.userTaskRecords(UserTaskIntent.RESUME)
            .onlyCommandRejections()
            .withRecordKey(userTaskKey)
            .getFirst();
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  private static List<Long> keysWithIntent(
      final List<Record<UserTaskRecordValue>> records, final UserTaskIntent intent) {
    return records.stream().filter(r -> r.getIntent() == intent).map(Record::getKey).toList();
  }

  private static void seedSuspending(final long processInstanceKey) {
    await().until(ENGINE::hasReachedEnd);
    ((MutableProcessingState) ENGINE.getProcessingState())
        .getSuspensionState()
        .setSuspensionState(processInstanceKey, State.SUSPENDING);
  }
}
