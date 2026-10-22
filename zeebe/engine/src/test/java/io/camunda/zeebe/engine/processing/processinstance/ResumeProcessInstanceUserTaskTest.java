/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Covers resuming the suspended user tasks of an instance one at a time via the {@code
 * RESUME_USER_TASKS} loop in {@link ProcessInstanceResumeUserTasksProcessor}, after the buffered
 * commands are drained and the parked jobs are resumed.
 */
public final class ResumeProcessInstanceUserTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final int USER_TASK_COUNT = 3;

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldResumeUserTasksOnePerCycleAfterJobs() {
    // given - user tasks and a job, all suspended
    final String processId = Strings.newRandomValidBpmnId();
    deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask("job", t -> t.zeebeJobType(Strings.newRandomValidBpmnId()))
            .endEvent()
            .moveToNode("fork")
            .userTask("a", t -> t.zeebeUserTask())
            .endEvent()
            .moveToNode("fork")
            .userTask("b", t -> t.zeebeUserTask())
            .endEvent()
            .done());
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final List<Long> userTaskKeys = createdUserTaskKeys(processInstanceKey, 2);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final long resumedPosition =
        ENGINE.processInstance().withInstanceKey(processInstanceKey).resume().getPosition();

    // then - the resume steps run in order: jobs, then one user task per cycle, then completion
    final List<Record<RecordValue>> resumeRecords =
        RecordingExporter.records()
            .limit(r -> r.getPosition() >= resumedPosition)
            .filter(r -> r.getKey() == processInstanceKey || isUserTaskOf(r, processInstanceKey))
            .filter(
                r ->
                    r.getIntent() == ProcessInstanceIntent.RESUME_JOBS
                        || r.getIntent() == ProcessInstanceIntent.RESUME_USER_TASKS
                        || r.getIntent() == ProcessInstanceIntent.COMPLETE_RESUMING
                        || r.getIntent() == UserTaskIntent.RESUMED)
            .filter(r -> r.getRecordType() != RecordType.COMMAND_REJECTION)
            .toList();
    assertThat(resumeRecords)
        .extracting(Record::getIntent)
        .containsExactly(
            ProcessInstanceIntent.RESUME_JOBS,
            ProcessInstanceIntent.RESUME_JOBS,
            ProcessInstanceIntent.RESUME_USER_TASKS,
            UserTaskIntent.RESUMED,
            ProcessInstanceIntent.RESUME_USER_TASKS,
            UserTaskIntent.RESUMED,
            ProcessInstanceIntent.RESUME_USER_TASKS,
            ProcessInstanceIntent.COMPLETE_RESUMING);
    assertThat(resumeRecords)
        .filteredOn(r -> r.getIntent() == UserTaskIntent.RESUMED)
        .extracting(Record::getKey)
        .containsExactlyElementsOf(userTaskKeys.stream().sorted().toList());
  }

  @Test
  public void shouldNotBufferUserTaskResumeCommands() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    deploy(singleUserTaskProcess(processId));
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    createdUserTaskKeys(processInstanceKey, 1);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    final long resumedPosition =
        ENGINE.processInstance().withInstanceKey(processInstanceKey).resume().getPosition();

    // then - the task is resumed without a RESUME command
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= resumedPosition)
                .filter(r -> isUserTaskOf(r, processInstanceKey))
                .map(Record::getIntent)
                .toList())
        .contains(UserTaskIntent.RESUMED)
        .doesNotContain(UserTaskIntent.RESUME);
  }

  @Test
  public void shouldNotResumeUserTaskCanceledDuringDrain() {
    // given - a user task with an interrupting timer that becomes due while suspended, and a task
    // after the timer so the instance is still active when the resume finishes
    final String processId = Strings.newRandomValidBpmnId();
    deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .userTask("task", t -> t.zeebeUserTask())
            .boundaryEvent("timer", b -> b.cancelActivity(true).timerWithDuration("PT1S"))
            .userTask("after", t -> t.zeebeUserTask())
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done());
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long userTaskKey = createdUserTaskKeys(processInstanceKey, 1).getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.increaseTime(Duration.ofSeconds(1));
    RecordingExporter.timerRecords(TimerIntent.SUSPENDED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when - the drain fires the buffered timer, which cancels the task before user tasks resume
    final long resumedPosition =
        ENGINE.processInstance().withInstanceKey(processInstanceKey).resume().getPosition();

    // then - the task was canceled and never resumed
    final List<UserTaskIntent> taskIntents =
        RecordingExporter.records()
            .limit(r -> r.getPosition() >= resumedPosition)
            .filter(r -> r.getKey() == userTaskKey && r.getIntent() instanceof UserTaskIntent)
            .map(r -> (UserTaskIntent) r.getIntent())
            .toList();
    assertThat(taskIntents)
        .contains(UserTaskIntent.CANCELED)
        .doesNotContain(UserTaskIntent.RESUMED);
  }

  @Test
  public void shouldResumeEachUserTaskOnceAfterRestartMidResume() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final var builder =
        Bpmn.createExecutableProcess(processId).startEvent().parallelGateway("fork");
    for (int i = 0; i < USER_TASK_COUNT; i++) {
      builder.moveToNode("fork").userTask("task" + i, t -> t.zeebeUserTask()).endEvent();
    }
    deploy(builder.done());
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final List<Long> userTaskKeys = createdUserTaskKeys(processInstanceKey, USER_TASK_COUNT);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - the broker restarts after the first user task is resumed, without waiting for the
    // resume to finish
    ENGINE.writeRecords(resumeCommand(processInstanceKey));
    RecordingExporter.userTaskRecords(UserTaskIntent.RESUMED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.stop();
    ENGINE.start();

    // then - every task is resumed exactly once; records replayed on restart are exported again
    // at their original position, so count distinct positions
    final long resumedPosition =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.RESUMED)
            .withRecordKey(processInstanceKey)
            .getFirst()
            .getPosition();
    final Map<Long, Long> resumedCounts =
        RecordingExporter.records()
            .limit(r -> r.getPosition() >= resumedPosition)
            .filter(r -> r.getIntent() == UserTaskIntent.RESUMED)
            .filter(r -> isUserTaskOf(r, processInstanceKey))
            .collect(
                Collectors.groupingBy(
                    Record::getKey,
                    Collectors.collectingAndThen(
                        Collectors.mapping(Record::getPosition, Collectors.toSet()),
                        positions -> (long) positions.size())));
    assertThat(resumedCounts)
        .containsOnlyKeys(userTaskKeys)
        .allSatisfy((key, count) -> assertThat(count).isOne());
  }

  @Test
  public void shouldResumeEachUserTaskOnceWithTwoOverlappingResumeChains() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final var builder =
        Bpmn.createExecutableProcess(processId).startEvent().parallelGateway("fork");
    for (int i = 0; i < USER_TASK_COUNT; i++) {
      builder.moveToNode("fork").userTask("task" + i, t -> t.zeebeUserTask()).endEvent();
    }
    deploy(builder.done());
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final List<Long> userTaskKeys = createdUserTaskKeys(processInstanceKey, USER_TASK_COUNT);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - the second RESUME arrives while RESUMING and starts a second chain
    ENGINE.writeRecords(resumeCommand(processInstanceKey), resumeCommand(processInstanceKey));

    // then - both chains started resuming user tasks before the instance was resumed
    final long resumedPosition =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.RESUMED)
            .withRecordKey(processInstanceKey)
            .getFirst()
            .getPosition();
    final List<Record<RecordValue>> records =
        RecordingExporter.records().limit(r -> r.getPosition() >= resumedPosition).toList();
    assertThat(records)
        .filteredOn(r -> r.getKey() == processInstanceKey)
        .filteredOn(r -> r.getIntent() == ProcessInstanceIntent.RESUME_USER_TASKS)
        .filteredOn(r -> r.getRecordType() == RecordType.COMMAND)
        .filteredOn(
            r -> ((ProcessInstanceRecordValue) r.getValue()).getResumeFromUserTaskKey() == -1L)
        .hasSize(2);

    // and - each task is still resumed exactly once
    assertThat(records)
        .filteredOn(r -> r.getIntent() == UserTaskIntent.RESUMED)
        .filteredOn(r -> isUserTaskOf(r, processInstanceKey))
        .extracting(Record::getKey)
        .containsExactlyInAnyOrderElementsOf(userTaskKeys);
  }

  private static RecordToWrite resumeCommand(final long processInstanceKey) {
    return RecordToWrite.command()
        .key(processInstanceKey)
        .processInstance(
            ProcessInstanceIntent.RESUME,
            new ProcessInstanceRecord().setProcessInstanceKey(processInstanceKey));
  }

  private static void deploy(final BpmnModelInstance process) {
    ENGINE.deployment().withXmlResource(process).deploy();
  }

  private static BpmnModelInstance singleUserTaskProcess(final String processId) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .userTask("task", t -> t.zeebeUserTask())
        .endEvent()
        .done();
  }

  private static List<Long> createdUserTaskKeys(final long processInstanceKey, final int count) {
    return RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(count)
        .map(Record::getKey)
        .toList();
  }

  private static boolean isUserTaskOf(final Record<?> record, final long processInstanceKey) {
    return record.getValue() instanceof final UserTaskRecordValue userTask
        && userTask.getProcessInstanceKey() == processInstanceKey;
  }
}
