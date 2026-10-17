/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.SUSPENDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.SuspensionBatchRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SuspensionBatchIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.SuspensionBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public final class SuspendProcessInstanceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldSuspendProcessInstance() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    final Record<ProcessInstanceRecordValue> suspended =
        ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then
    assertThat(suspended.getIntent()).isEqualTo(SUSPENDED);
    Assertions.assertThat(suspended.getValue())
        .hasBpmnProcessId(processId)
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldRejectSuspendWhenSuspensionMarkerPresent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<ProcessInstanceRecordValue> rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .expectSuspendRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ProcessInstanceIntent.SUSPEND)
        .hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectSuspendWhenKeyIsNotAProcessInstance() {
    // given - a process instance with a user task, whose element instance key is NOT the process
    // instance's own key
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask("task").endEvent().done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long userTaskElementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .getFirst()
            .getKey();

    // when - try to suspend using the user task's element instance key, not the process instance's
    final Record<ProcessInstanceRecordValue> rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(userTaskElementInstanceKey)
            .onPartition(1)
            .expectSuspendRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ProcessInstanceIntent.SUSPEND)
        .hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectSuspendIfProcessInstanceNotFound() {
    // given
    final long nonExistentKey = -1L;

    // when
    final Record<ProcessInstanceRecordValue> rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(nonExistentKey)
            .onPartition(1)
            .expectSuspendRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ProcessInstanceIntent.SUSPEND)
        .hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectSuspendIfProcessInstanceIsTerminating() {
    // given - a runtime instruction terminates the process instance, and a canceling task
    // listener blocks the termination
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask("trigger", t -> t.zeebeJobType(processId + "-trigger"))
                .endEvent()
                .moveToNode("fork")
                .userTask(
                    "task",
                    t -> t.zeebeUserTask().zeebeTaskListener(l -> l.canceling().type(processId)))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeTerminateInstruction("trigger")
            .create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    ENGINE.job().ofInstance(processInstanceKey).withType(processId + "-trigger").complete();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(processId)
        .await();

    // when
    final Record<ProcessInstanceRecordValue> rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .expectSuspendRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(ProcessInstanceIntent.SUSPEND)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to suspend a process instance with key '%d', but it is already being terminated"
                .formatted(processInstanceKey));
  }

  @Test
  public void shouldVisitElementInstancesDepthFirst() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess("sub")
                .embeddedSubProcess()
                .startEvent()
                .parallelGateway("fork")
                .userTask("a", t -> t.zeebeUserTask())
                .endEvent()
                .moveToNode("fork")
                .userTask(
                    "b",
                    t -> t.zeebeUserTask().zeebeTaskListener(l -> l.creating().type(processId)))
                .endEvent()
                .subProcessDone()
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final List<Long> taskKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.USER_TASK)
            .limit(2)
            .map(Record::getKey)
            .sorted()
            .toList();
    final long subProcessKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("sub")
            .getFirst()
            .getKey();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATING)
        .withProcessInstanceKey(processInstanceKey)
        .limit(2)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then
    assertThat(suspensionWalk(processInstanceKey))
        .describedAs("Expect post-order traversal: both tasks before the subprocess, before the PI")
        .containsExactly(
            tuple(processInstanceKey, -1L, -1L),
            tuple(subProcessKey, processInstanceKey, -1L),
            tuple(taskKeys.get(0), subProcessKey, -1L),
            tuple(taskKeys.get(1), subProcessKey, -1L),
            tuple(subProcessKey, processInstanceKey, taskKeys.get(1)),
            tuple(processInstanceKey, -1L, subProcessKey));
    assertThat(suspendedUserTasks(processInstanceKey))
        .describedAs("Expect one SUSPENDED per task, whatever its lifecycle state")
        .containsExactlyElementsOf(taskKeys);
  }

  @Test
  public void shouldNotSuspendTerminatingUserTask() {
    // given - an interrupting boundary event terminates the task, and a canceling task listener
    // keeps the task in ELEMENT_TERMINATING
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask(
                    "task",
                    t -> t.zeebeUserTask().zeebeTaskListener(l -> l.canceling().type(processId)))
                .boundaryEvent(
                    "boundary",
                    b -> b.message(m -> m.name(processId).zeebeCorrelationKeyExpression("\"key\"")))
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.message().withName(processId).withCorrelationKey("key").publish();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(processId)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then
    assertThat(suspendedUserTasks(processInstanceKey))
        .describedAs("Expect no SUSPENDED for a user task that is about to be canceled")
        .isEmpty();
  }

  @Test
  public void shouldNotVisitCalledProcessInstance() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String childProcessId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            "child.bpmn",
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .userTask("task", t -> t.zeebeUserTask())
                .endEvent()
                .done())
        .withXmlResource(
            "parent.bpmn",
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long callActivityKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("call")
            .getFirst()
            .getKey();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withParentProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.USER_TASK)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then
    assertThat(suspensionWalk(processInstanceKey))
        .containsExactly(
            tuple(processInstanceKey, -1L, -1L),
            tuple(callActivityKey, processInstanceKey, -1L),
            tuple(processInstanceKey, -1L, callActivityKey));
    assertThat(suspendedUserTasks(processInstanceKey)).isEmpty();
  }

  @Test
  public void shouldProcessEachWalkCommandInItsOwnBatch() {
    // given
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
                .userTask("b", t -> t.zeebeUserTask())
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(2)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then
    final var walkRecords =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == SUSPENDED && r.getKey() == processInstanceKey)
            .filter(r -> r.getValueType() == ValueType.SUSPENSION_BATCH)
            .toList();
    // root(descend), a(descend), a(suspend)+move to b, b(suspend)+ascend to root, root(suspend):
    // 4 commands, 3 events
    assertThat(walkRecords).hasSize(7);
    // The first walk command (the root's descend step) is written by SUSPEND, which is not itself
    // processed in a separate batch, so it rides along and is processed inline too — its own
    // follow-up (the second walk command) still carries SUSPEND's source position.
    assertThat(walkRecords.get(1).getSourceRecordPosition())
        .isEqualTo(walkRecords.get(0).getSourceRecordPosition());
    // From there on, every walk record is produced by processing the most recently appended walk
    // command, never a command further back — i.e. each command is processed in its own batch, not
    // folded together with the commands that precede it.
    long lastCommandPosition = walkRecords.get(1).getPosition();
    for (int i = 2; i < walkRecords.size(); i++) {
      final var record = walkRecords.get(i);
      assertThat(record.getSourceRecordPosition())
          .describedAs("Expect each walk command to be processed in its own batch")
          .isEqualTo(lastCommandPosition);
      if (record.getRecordType() == RecordType.COMMAND) {
        lastCommandPosition = record.getPosition();
      }
    }
  }

  @Test
  public void shouldContinueAtNextSiblingWhenTargetElementInstanceIsGone() {
    // given - a subprocess with two parallel user tasks; the lower-keyed one is completed
    // normally, so its element instance is actually removed from state, while the other stays
    // active
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess("sub")
                .embeddedSubProcess()
                .startEvent()
                .parallelGateway("fork")
                .userTask("a", t -> t.zeebeUserTask())
                .endEvent()
                .moveToNode("fork")
                .userTask("b", t -> t.zeebeUserTask())
                .endEvent()
                .subProcessDone()
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long subProcessKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("sub")
            .getFirst()
            .getKey();
    final List<Record<UserTaskRecordValue>> createdTasks =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .collect(Collectors.toCollection(ArrayList::new));
    createdTasks.sort(Comparator.comparingLong(r -> r.getValue().getElementInstanceKey()));
    final long removedElementInstanceKey = createdTasks.get(0).getValue().getElementInstanceKey();
    final long removedUserTaskKey = createdTasks.get(0).getKey();
    final long remainingElementInstanceKey = createdTasks.get(1).getValue().getElementInstanceKey();

    // complete the lower-keyed task so its element instance is really removed from state
    ENGINE.userTask().withKey(removedUserTaskKey).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withRecordKey(removedElementInstanceKey)
        .await();

    // when - the process instance is marked SUSPENDING directly (bypassing the real walk), and a
    // crafted SUSPEND_ELEMENT_INSTANCE command targets the removed element instance, with the
    // subprocess (which still exists) as its declared parent
    ENGINE.pauseProcessing(1);
    ENGINE.stop();
    ENGINE.writeRecords(
        RecordToWrite.event()
            .key(processInstanceKey)
            .processInstance(ProcessInstanceIntent.SUSPENDING, new ProcessInstanceRecord()),
        RecordToWrite.command()
            .key(processInstanceKey)
            .suspensionBatch(
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE,
                new SuspensionBatchRecord()
                    .setProcessInstanceKey(processInstanceKey)
                    .setElementInstanceKey(removedElementInstanceKey)
                    .setParentKey(subProcessKey)
                    .setIndex(-1)));
    ENGINE.start();

    // then - the walk moves straight on to the remaining sibling under the subprocess (the
    // index+1 seek skips over the removed task's deleted row), then ascends through the
    // subprocess and the root once there is nothing left to visit
    assertThat(suspensionWalk(processInstanceKey))
        .containsExactly(
            tuple(removedElementInstanceKey, subProcessKey, -1L),
            tuple(remainingElementInstanceKey, subProcessKey, -1L),
            tuple(subProcessKey, processInstanceKey, remainingElementInstanceKey),
            tuple(processInstanceKey, -1L, subProcessKey));
    assertThat(suspendedUserTasks(processInstanceKey)).containsExactly(remainingElementInstanceKey);

    // and - UserTask.SUSPENDED for the remaining task, then ELEMENT_INSTANCE_SUSPENDED for it,
    // then for the subprocess, then for the root, then ProcessInstance.SUSPENDED, in that order
    final var userTaskSuspended =
        RecordingExporter.userTaskRecords(UserTaskIntent.SUSPENDED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final var taskSuspended =
        RecordingExporter.suspensionBatchRecords(SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(remainingElementInstanceKey)
            .getFirst();
    final var subProcessSuspended =
        RecordingExporter.suspensionBatchRecords(SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(subProcessKey)
            .getFirst();
    final var rootSuspended =
        RecordingExporter.suspensionBatchRecords(SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(processInstanceKey)
            .getFirst();
    final var processInstanceSuspended =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.SUSPENDED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(
            List.of(
                userTaskSuspended.getPosition(),
                taskSuspended.getPosition(),
                subProcessSuspended.getPosition(),
                rootSuspended.getPosition(),
                processInstanceSuspended.getPosition()))
        .isSorted();
  }

  @Test
  public void shouldRestartFromRootWhenTargetAndParentAreGone() {
    // given - one branch (a subprocess with a user task) completes entirely and is removed from
    // state, while a second, parallel user task keeps the process instance running
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway("fork")
                .subProcess("sub")
                .embeddedSubProcess()
                .startEvent()
                .userTask("inner", t -> t.zeebeUserTask())
                .endEvent()
                .subProcessDone()
                .endEvent()
                .moveToNode("fork")
                .userTask("task", t -> t.zeebeUserTask())
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long subProcessKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("sub")
            .getFirst()
            .getKey();
    final var innerTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("inner")
            .getFirst();
    final long innerElementInstanceKey = innerTask.getValue().getElementInstanceKey();
    final long remainingElementInstanceKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .getFirst()
            .getValue()
            .getElementInstanceKey();

    // complete the inner task so the whole subprocess completes and is removed from state,
    // leaving the parallel "task" as the process instance's only remaining active child
    ENGINE.userTask().withKey(innerTask.getKey()).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withRecordKey(subProcessKey)
        .await();

    // when - the process instance is marked SUSPENDING directly (bypassing the real walk), and a
    // crafted SUSPEND_ELEMENT_INSTANCE command targets the removed inner task, whose declared
    // parent (the subprocess) is also gone
    ENGINE.pauseProcessing(1);
    ENGINE.stop();
    ENGINE.writeRecords(
        RecordToWrite.event()
            .key(processInstanceKey)
            .processInstance(ProcessInstanceIntent.SUSPENDING, new ProcessInstanceRecord()),
        RecordToWrite.command()
            .key(processInstanceKey)
            .suspensionBatch(
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE,
                new SuspensionBatchRecord()
                    .setProcessInstanceKey(processInstanceKey)
                    .setElementInstanceKey(innerElementInstanceKey)
                    .setParentKey(subProcessKey)
                    .setIndex(-1)));
    ENGINE.start();

    // then - both the target and its declared parent are gone, so the walk restarts at the root
    // with index=-1, descends into the one remaining task, and finishes the walk from there
    assertThat(suspensionWalk(processInstanceKey))
        .containsExactly(
            tuple(innerElementInstanceKey, subProcessKey, -1L),
            tuple(processInstanceKey, -1L, -1L),
            tuple(remainingElementInstanceKey, processInstanceKey, -1L),
            tuple(processInstanceKey, -1L, remainingElementInstanceKey));
    assertThat(suspendedUserTasks(processInstanceKey)).containsExactly(remainingElementInstanceKey);

    final var processInstanceSuspended =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.SUSPENDED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(processInstanceSuspended.getIntent()).isEqualTo(ProcessInstanceIntent.SUSPENDED);
  }

  /** Returns the element instance keys of user tasks suspended until the given one is suspended. */
  private static List<Long> suspendedUserTasks(final long processInstanceKey) {
    return RecordingExporter.records()
        .limit(r -> r.getIntent() == SUSPENDED && r.getKey() == processInstanceKey)
        .filter(r -> r.getIntent() == UserTaskIntent.SUSPENDED)
        .filter(
            r -> ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey() == processInstanceKey)
        .map(r -> ((UserTaskRecordValue) r.getValue()).getElementInstanceKey())
        .toList();
  }

  /** Returns the walk commands (elementInstanceKey, parentKey, index) until it is suspended. */
  private static List<Tuple> suspensionWalk(final long processInstanceKey) {
    return RecordingExporter.records()
        .limit(r -> r.getIntent() == SUSPENDED && r.getKey() == processInstanceKey)
        .filter(r -> r.getValueType() == ValueType.SUSPENSION_BATCH)
        .filter(r -> r.getRecordType() == RecordType.COMMAND)
        .filter(
            r ->
                ((SuspensionBatchRecordValue) r.getValue()).getProcessInstanceKey()
                    == processInstanceKey)
        .map(
            r -> {
              final var value = (SuspensionBatchRecordValue) r.getValue();
              return tuple(value.getElementInstanceKey(), value.getParentKey(), value.getIndex());
            })
        .toList();
  }
}
