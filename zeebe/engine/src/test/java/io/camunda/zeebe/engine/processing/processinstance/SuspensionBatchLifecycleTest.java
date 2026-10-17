/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SuspensionBatchIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.protocol.record.value.SuspensionBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordStream;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.Rule;
import org.junit.Test;

/**
 * Asserts the full record-order lifecycle of the post-order suspension walk ({@link
 * SuspensionBatchSuspendElementInstanceProcessor}), from {@code ProcessInstance.SUSPEND} through
 * {@code ProcessInstance.SUSPENDED}, across the element instance tree shapes the walk must handle.
 */
public final class SuspensionBatchLifecycleTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldSuspendSiblingsInPostOrder() {
    // given - two user tasks in parallel, both direct children of the process instance
    final String processId = Strings.newRandomValidBpmnId();
    engine
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
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(2)
        .await();
    final List<Long> taskKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.USER_TASK)
            .limit(2)
            .map(Record::getKey)
            .sorted()
            .toList();

    // when
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then - post-order: both tasks suspended (in key order) before the process instance, with
    // exactly one UserTask.SUSPENDED and one buffered RESUME per task. Only four
    // SUSPEND_ELEMENT_INSTANCE commands: descend into the first task, suspend it and move
    // straight to the second (no detour back through the root), suspend it and ascend, suspend
    // the root.
    assertThat(walkRecordKinds(processInstanceKey))
        .containsExactly(
            tuple(RecordType.COMMAND, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPEND),
            tuple(RecordType.EVENT, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPENDING),
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // descend into lower-keyed task
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // lower-keyed task is a leaf
            tuple(RecordType.EVENT, ValueType.USER_TASK, UserTaskIntent.SUSPENDED),
            tuple(RecordType.EVENT, ValueType.BUFFERED_COMMAND, BufferedCommandIntent.BUFFERED),
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // lower-keyed task suspended
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent
                    .SUSPEND_ELEMENT_INSTANCE), // move straight to the higher-keyed task
            tuple(RecordType.EVENT, ValueType.USER_TASK, UserTaskIntent.SUSPENDED),
            tuple(RecordType.EVENT, ValueType.BUFFERED_COMMAND, BufferedCommandIntent.BUFFERED),
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // higher-keyed task suspended
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // no sibling left, ascend to root
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // root suspended
            tuple(RecordType.EVENT, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPENDED));

    // every SUSPEND_ELEMENT_INSTANCE command and ELEMENT_INSTANCE_SUSPENDED event in the walk
    // shares the key of the first command, which is the process instance key
    assertThat(suspensionBatchRecordKeys(processInstanceKey))
        .describedAs("Expect every walk record to share the process instance key")
        .containsOnly(processInstanceKey);

    // the cursor sequence: descend into the lower-keyed task, suspend it, move straight to the
    // other task (no detour back through the root), suspend it, ascend to the root, suspend it
    final var cursors = suspensionBatchCursors(processInstanceKey);
    assertThat(cursors)
        .containsExactly(
            tuple(SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE, processInstanceKey, -1L, -1L),
            tuple(
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE,
                taskKeys.get(0),
                processInstanceKey,
                -1L),
            tuple(
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED,
                taskKeys.get(0),
                processInstanceKey,
                -1L),
            tuple(
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE,
                taskKeys.get(1),
                processInstanceKey,
                -1L),
            tuple(
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED,
                taskKeys.get(1),
                processInstanceKey,
                -1L),
            tuple(
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE,
                processInstanceKey,
                -1L,
                taskKeys.get(1)),
            tuple(
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED,
                processInstanceKey,
                -1L,
                taskKeys.get(1)));

    // regression guard: on the old "ascend then redescend" behavior, the command right after the
    // first task's ELEMENT_INSTANCE_SUSPENDED would target the root, not the second task
    assertThat(cursors.get(3))
        .describedAs(
            "Expect the walk command right after the first task's suspension to target the"
                + " second task directly, not the root")
        .isEqualTo(
            tuple(
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE,
                taskKeys.get(1),
                processInstanceKey,
                -1L));
  }

  @Test
  public void shouldDescendAndAscendThroughNestedSubprocess() {
    // given - a subprocess containing a single user task
    final String processId = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess("sub")
                .embeddedSubProcess()
                .startEvent()
                .userTask("task", t -> t.zeebeUserTask())
                .endEvent()
                .subProcessDone()
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then - descend root->sub->task, suspend the task, then ascend sub, then root
    assertThat(walkRecordKinds(processInstanceKey))
        .containsExactly(
            tuple(RecordType.COMMAND, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPEND),
            tuple(RecordType.EVENT, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPENDING),
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // descend into sub
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // descend into task
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // task is a leaf
            tuple(RecordType.EVENT, ValueType.USER_TASK, UserTaskIntent.SUSPENDED),
            tuple(RecordType.EVENT, ValueType.BUFFERED_COMMAND, BufferedCommandIntent.BUFFERED),
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // task suspended
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // continue at sub
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // sub suspended
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // continue at root
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // root suspended
            tuple(RecordType.EVENT, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPENDED));
  }

  @Test
  public void shouldSuspendMultiInstanceUserTaskInstances() {
    // given - a parallel multi-instance user task with two instances; the MI body is a container
    final String processId = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", t -> t.zeebeUserTask())
                .multiInstance(m -> m.parallel().zeebeInputCollectionExpression("=[1,2]"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(2)
        .await();

    // when
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then - descend into the MI body, then into the first inner instance; once it is suspended,
    // move straight to the second (no detour back through the MI body); then ascend
    assertThat(walkRecordKinds(processInstanceKey))
        .containsExactly(
            tuple(RecordType.COMMAND, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPEND),
            tuple(RecordType.EVENT, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPENDING),
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // descend into the MI body
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent
                    .SUSPEND_ELEMENT_INSTANCE), // descend into the first inner instance
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // first instance is a leaf
            tuple(RecordType.EVENT, ValueType.USER_TASK, UserTaskIntent.SUSPENDED),
            tuple(RecordType.EVENT, ValueType.BUFFERED_COMMAND, BufferedCommandIntent.BUFFERED),
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // first instance suspended
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // move to the second instance
            tuple(RecordType.EVENT, ValueType.USER_TASK, UserTaskIntent.SUSPENDED),
            tuple(RecordType.EVENT, ValueType.BUFFERED_COMMAND, BufferedCommandIntent.BUFFERED),
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // second instance suspended
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent
                    .SUSPEND_ELEMENT_INSTANCE), // no sibling left, ascend to the MI body
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // MI body suspended
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // no sibling left, ascend to root
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // root suspended
            tuple(RecordType.EVENT, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPENDED));
  }

  @Test
  public void shouldSuspendAdHocSubProcessActiveInstance() {
    // given - an ad-hoc subprocess with one active user task; both the subprocess and its inner
    // instance wrapper are containers
    final String processId = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .adHocSubProcess(
                    "ahsp",
                    ahsp -> {
                      ahsp.zeebeActiveElementsCollectionExpression("[\"task\"]");
                      ahsp.userTask("task", t -> t.zeebeUserTask());
                    })
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then - descend root->ad-hoc subprocess->inner instance->task, suspend the task, then ascend
    assertThat(walkRecordKinds(processInstanceKey))
        .containsExactly(
            tuple(RecordType.COMMAND, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPEND),
            tuple(RecordType.EVENT, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPENDING),
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // descend into ad-hoc subprocess
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent
                    .SUSPEND_ELEMENT_INSTANCE), // descend into the inner instance wrapper
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // descend into the task
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // task is a leaf
            tuple(RecordType.EVENT, ValueType.USER_TASK, UserTaskIntent.SUSPENDED),
            tuple(RecordType.EVENT, ValueType.BUFFERED_COMMAND, BufferedCommandIntent.BUFFERED),
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // task suspended
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent
                    .SUSPEND_ELEMENT_INSTANCE), // continue at the inner instance wrapper
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent
                    .ELEMENT_INSTANCE_SUSPENDED), // inner instance wrapper suspended
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent
                    .SUSPEND_ELEMENT_INSTANCE), // continue at the ad-hoc subprocess
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // ad-hoc subprocess suspended
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // continue at root
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // root suspended
            tuple(RecordType.EVENT, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPENDED));
  }

  @Test
  public void shouldNotSuspendCalledProcessInstanceUserTask() {
    // given - a call activity whose called process instance has an active user task; the call
    // activity element instance is a leaf for this walk, since the called instance is not its
    // child in the element instance tree
    final String processId = Strings.newRandomValidBpmnId();
    final String childProcessId = Strings.newRandomValidBpmnId();
    engine
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
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withParentProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.USER_TASK)
        .await();

    // when
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then - no UserTask.SUSPENDED or BufferedCommand.BUFFERED events at all: the call activity is
    // treated as a leaf and the called instance's user task is untouched
    assertThat(walkRecordKinds(processInstanceKey))
        .containsExactly(
            tuple(RecordType.COMMAND, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPEND),
            tuple(RecordType.EVENT, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPENDING),
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // descend into the call activity
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // call activity is a leaf
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // call activity suspended
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // continue at root
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // root suspended
            tuple(RecordType.EVENT, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPENDED));
  }

  @Test
  public void shouldSuspendNonUserTaskLeafWithoutUserTaskEvents() {
    // given - a process waiting on a timer catch event, not a user task
    final String processId = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT1H"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("timer")
        .await();

    // when
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then - the timer is a leaf, suspended with no UserTask or BufferedCommand events, same shape
    // as the call-activity case
    assertThat(walkRecordKinds(processInstanceKey))
        .containsExactly(
            tuple(RecordType.COMMAND, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPEND),
            tuple(RecordType.EVENT, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPENDING),
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // descend into the timer
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // timer is a leaf
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // timer suspended
            tuple(
                RecordType.COMMAND,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE), // continue at root
            tuple(
                RecordType.EVENT,
                ValueType.SUSPENSION_BATCH,
                SuspensionBatchIntent.ELEMENT_INSTANCE_SUSPENDED), // root suspended
            tuple(RecordType.EVENT, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SUSPENDED));
  }

  /**
   * Returns the (recordType, valueType, intent) of every record of the suspension walk, from {@code
   * ProcessInstance.SUSPEND} through {@code ProcessInstance.SUSPENDED}.
   */
  private static List<Tuple> walkRecordKinds(final long processInstanceKey) {
    return RecordingExporter.records()
        .limit(
            r ->
                r.getValueType() == ValueType.PROCESS_INSTANCE
                    && r.getIntent() == ProcessInstanceIntent.SUSPENDED
                    && r.getKey() == processInstanceKey)
        .filter(r -> belongsToWalk(r, processInstanceKey))
        .map(r -> tuple(r.getRecordType(), r.getValueType(), r.getIntent()))
        .toList();
  }

  private static List<Long> suspensionBatchRecordKeys(final long processInstanceKey) {
    return suspensionBatchRecords(processInstanceKey).map(Record::getKey).toList();
  }

  /** Returns the (intent, elementInstanceKey, parentKey, index) cursor of each SB record. */
  private static List<Tuple> suspensionBatchCursors(final long processInstanceKey) {
    return suspensionBatchRecords(processInstanceKey)
        .map(
            r -> {
              final var value = (SuspensionBatchRecordValue) r.getValue();
              return tuple(
                  r.getIntent(),
                  value.getElementInstanceKey(),
                  value.getParentKey(),
                  value.getIndex());
            })
        .toList();
  }

  private static RecordStream suspensionBatchRecords(final long processInstanceKey) {
    return RecordingExporter.records()
        .limit(
            r ->
                r.getValueType() == ValueType.PROCESS_INSTANCE
                    && r.getIntent() == ProcessInstanceIntent.SUSPENDED
                    && r.getKey() == processInstanceKey)
        .filter(r -> r.getValueType() == ValueType.SUSPENSION_BATCH)
        .filter(
            r ->
                ((SuspensionBatchRecordValue) r.getValue()).getProcessInstanceKey()
                    == processInstanceKey);
  }

  private static boolean belongsToWalk(final Record<?> record, final long processInstanceKey) {
    return switch (record.getValueType()) {
      case PROCESS_INSTANCE ->
          record.getKey() == processInstanceKey
              && (record.getIntent() == ProcessInstanceIntent.SUSPEND
                  || record.getIntent() == ProcessInstanceIntent.SUSPENDING
                  || record.getIntent() == ProcessInstanceIntent.SUSPENDED);
      case SUSPENSION_BATCH ->
          ((SuspensionBatchRecordValue) record.getValue()).getProcessInstanceKey()
              == processInstanceKey;
      case USER_TASK ->
          record.getIntent() == UserTaskIntent.SUSPENDED
              && ((UserTaskRecordValue) record.getValue()).getProcessInstanceKey()
                  == processInstanceKey;
      case BUFFERED_COMMAND ->
          ((BufferedCommandRecordValue) record.getValue()).getProcessInstanceKey()
              == processInstanceKey;
      default -> false;
    };
  }
}
