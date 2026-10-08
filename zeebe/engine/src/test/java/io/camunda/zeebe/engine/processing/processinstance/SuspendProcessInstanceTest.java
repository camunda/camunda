/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.SUSPENDED;
import static io.camunda.zeebe.protocol.record.intent.SuspensionBatchIntent.COMPLETE_SUSPENDING_ELEMENT_INSTANCE;
import static io.camunda.zeebe.protocol.record.intent.SuspensionBatchIntent.SUSPEND_ELEMENT_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.SuspensionBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
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
        .containsExactly(
            tuple(SUSPEND_ELEMENT_INSTANCE, processInstanceKey, -1L),
            tuple(SUSPEND_ELEMENT_INSTANCE, subProcessKey, processInstanceKey),
            tuple(SUSPEND_ELEMENT_INSTANCE, taskKeys.get(0), subProcessKey),
            tuple(SUSPEND_ELEMENT_INSTANCE, taskKeys.get(1), subProcessKey),
            tuple(COMPLETE_SUSPENDING_ELEMENT_INSTANCE, subProcessKey, processInstanceKey),
            tuple(COMPLETE_SUSPENDING_ELEMENT_INSTANCE, processInstanceKey, -1L));
    assertThat(suspendedUserTasks(processInstanceKey))
        .describedAs("Expect one SUSPENDED per task, whatever its lifecycle state")
        .containsExactlyElementsOf(taskKeys);
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
            tuple(SUSPEND_ELEMENT_INSTANCE, processInstanceKey, -1L),
            tuple(SUSPEND_ELEMENT_INSTANCE, callActivityKey, processInstanceKey),
            tuple(COMPLETE_SUSPENDING_ELEMENT_INSTANCE, processInstanceKey, -1L));
    assertThat(suspendedUserTasks(processInstanceKey)).isEmpty();
  }

  /** Returns the element instance keys of user tasks suspended until the given one is suspended. */
  private static List<Long> suspendedUserTasks(final long processInstanceKey) {
    return RecordingExporter.records()
        .limit(r -> r.getIntent() == SUSPENDED && r.getKey() == processInstanceKey)
        .filter(r -> r.getIntent() == UserTaskIntent.SUSPENDED)
        .map(r -> ((UserTaskRecordValue) r.getValue()).getElementInstanceKey())
        .toList();
  }

  /** Returns the walk commands of all process instances until the given one is suspended. */
  private static List<Tuple> suspensionWalk(final long processInstanceKey) {
    return RecordingExporter.records()
        .limit(r -> r.getIntent() == SUSPENDED && r.getKey() == processInstanceKey)
        .filter(r -> r.getValueType() == ValueType.SUSPENSION_BATCH)
        .filter(r -> r.getRecordType() == RecordType.COMMAND)
        .map(
            r -> {
              final var value = (SuspensionBatchRecordValue) r.getValue();
              return tuple(r.getIntent(), value.getIndexKey(), value.getParentKey());
            })
        .toList();
  }
}
