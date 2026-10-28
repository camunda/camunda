/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectJobCompleteWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).expectRejection().complete();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.COMPLETE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectJobCompleteWhileSuspending() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    seedSuspending(processInstanceKey);

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).expectRejection().complete();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.COMPLETE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectJobFailWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).withRetries(0).expectRejection().fail();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.FAIL)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectJobThrowErrorWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).withErrorCode("ERR").expectRejection().throwError();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.THROW_ERROR)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectJobUpdateWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).expectRejection().update();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.UPDATE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectJobUpdateRetriesWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).withRetries(5).expectRejection().updateRetries();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.UPDATE_RETRIES)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectJobUpdateTimeoutWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).withTimeout(60_000L).expectRejection().updateTimeout();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.UPDATE_TIMEOUT)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldBufferAndDrainJobYieldWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();

    final Record<JobBatchRecordValue> batch = ENGINE.jobs().withType(jobType).activate();
    final JobRecordValue activatedJob = batch.getValue().getJobs().getFirst();
    final long jobKey = batch.getValue().getJobKeys().getFirst();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE.writeRecords(RecordToWrite.command().job(JobIntent.YIELD, activatedJob).key(jobKey));

    // then
    assertThat(bufferedCommandIntent(processInstanceKey)).isEqualTo(JobIntent.YIELD);

    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.YIELDED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBufferAndDrainJobRecurAfterBackoffWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();

    ENGINE.job().withKey(job.getKey()).withRetries(3).withBackOff(Duration.ofSeconds(1)).fail();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE.increaseTime(Duration.ofSeconds(2));

    // then
    assertThat(bufferedCommandIntent(processInstanceKey)).isEqualTo(JobIntent.RECUR_AFTER_BACKOFF);

    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.RECURRED_AFTER_BACKOFF)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldCompleteJobWhileResuming() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    seedResuming(processInstanceKey);

    // when
    ENGINE.job().withKey(job.getKey()).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldFailJobWhileResuming() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    seedResuming(job.getValue().getProcessInstanceKey());

    // when
    final Record<JobRecordValue> failed = ENGINE.job().withKey(job.getKey()).withRetries(0).fail();

    // then
    Assertions.assertThat(failed).hasIntent(JobIntent.FAILED);
  }

  @Test
  public void shouldThrowErrorFromJobWhileResuming() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .boundaryEvent("error", b -> b.error("ERR"))
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    seedResuming(processInstanceKey);

    // when
    ENGINE.job().withKey(job.getKey()).withErrorCode("ERR").throwError();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("error")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectTaskListenerJobCompleteWhileResuming() {
    // given - the listener's follow-up user task command would be rejected while resuming
    final String listenerType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask(
                    "task",
                    t -> t.zeebeUserTask().zeebeTaskListener(l -> l.creating().type(listenerType)))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(listenerType)
            .getFirst();
    seedResuming(processInstanceKey);

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).expectRejection().complete();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.COMPLETE)
        .hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldCompleteEndListenerJobWhenInstanceEndsWhileResuming() {
    // given - a due timer is buffered while suspended; draining it ends the instance before the
    // resume finishes, so the end listener job is handed out while still resuming
    final String processId = Strings.newRandomValidBpmnId();
    final String listenerType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .zeebeEndExecutionListener(listenerType)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask("task", t -> t.zeebeJobType(Strings.newRandomValidBpmnId()))
                .endEvent()
                .moveToNode("fork")
                .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT1S"))
                .endEvent("terminate", e -> e.terminate())
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.increaseTime(Duration.ofSeconds(1));
    RecordingExporter.timerRecords(TimerIntent.SUSPENDED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    // write RESUME directly: the client's resume() waits for RESUMED, which this flow never writes
    ENGINE.writeRecords(
        RecordToWrite.command()
            .processInstance(
                ProcessInstanceIntent.RESUME,
                new ProcessInstanceRecord().setProcessInstanceKey(processInstanceKey))
            .key(processInstanceKey));

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(listenerType).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  private static JobIntent bufferedCommandIntent(final long processInstanceKey) {
    return (JobIntent)
        ((BufferedCommandRecordValue) bufferedCommand(processInstanceKey).getValue()).getIntent();
  }

  private static Record<RecordValue> bufferedCommand(final long processInstanceKey) {
    return RecordingExporter.records()
        .withValueType(ValueType.BUFFERED_COMMAND)
        .withIntent(BufferedCommandIntent.BUFFERED)
        .filter(
            r ->
                ((BufferedCommandRecordValue) r.getValue()).getProcessInstanceKey()
                    == processInstanceKey)
        .getFirst();
  }

  private static void seedSuspending(final long processInstanceKey) {
    await().until(ENGINE::hasReachedEnd);
    ((MutableProcessingState) ENGINE.getProcessingState())
        .getSuspensionState()
        .setSuspensionState(processInstanceKey, State.SUSPENDING);
  }

  private static void seedResuming(final long processInstanceKey) {
    await().until(ENGINE::hasReachedEnd);
    ((MutableProcessingState) ENGINE.getProcessingState())
        .getSuspensionState()
        .setSuspensionState(processInstanceKey, State.RESUMING);
  }
}
