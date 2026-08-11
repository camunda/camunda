/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/** Covers job hand-out for jobs of a suspended process instance. */
public final class JobSuspensionHandoutTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldNotActivateJobOfSuspendedProcessInstance() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final long processInstanceKey = createInstanceWithJob(jobType);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then
    final Record<JobBatchRecordValue> batch = ENGINE.jobs().withType(jobType).activate();
    assertThat(batch.getValue().getJobKeys()).isEmpty();
  }

  @Test
  public void shouldSuspendEveryActivatableJobOfTheInstance() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask("taskA", t -> t.zeebeJobType(jobType))
                .moveToNode("fork")
                .serviceTask("taskB", t -> t.zeebeJobType(jobType))
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(2)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.SUSPENDED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2)
                .count())
        .isEqualTo(2);
  }

  @Test
  public void shouldNotSuspendJobOfCalledChildInstance() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String childProcessId = Strings.newRandomValidBpmnId();
    final String parentProcessId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask("childTask", t -> t.zeebeJobType(jobType))
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childProcessId))
                .done())
        .deploy();
    final long parentInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();
    final Record<JobRecordValue> childJob =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).getFirst();

    // when
    ENGINE.processInstance().withInstanceKey(parentInstanceKey).suspend();

    // then - the child instance's job is still handed out. getChildren never returns the child
    // instance root, so the suspend walk does not reach this job.
    final Record<JobBatchRecordValue> batch = ENGINE.jobs().withType(jobType).activate();
    assertThat(batch.getValue().getJobKeys()).containsExactly(childJob.getKey());
  }

  @Test
  public void shouldNotSuspendParentJobWhenChildInstanceIsSuspended() {
    // given
    final String parentJobType = Strings.newRandomValidBpmnId();
    final String childJobType = Strings.newRandomValidBpmnId();
    final String childProcessId = Strings.newRandomValidBpmnId();
    final String parentProcessId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask("childTask", t -> t.zeebeJobType(childJobType))
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask("parentTask", t -> t.zeebeJobType(parentJobType))
                .moveToNode("fork")
                .callActivity("call", c -> c.zeebeProcessId(childProcessId))
                .done())
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();
    final Record<JobRecordValue> parentJob =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(parentJobType).getFirst();
    final Record<JobRecordValue> childJob =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(childJobType).getFirst();

    // when
    ENGINE.processInstance().withInstanceKey(childJob.getValue().getProcessInstanceKey()).suspend();

    // then - only the suspended child parks its job; the parent's job is still handed out
    final Record<JobBatchRecordValue> parentBatch =
        ENGINE.jobs().withType(parentJobType).activate();
    assertThat(parentBatch.getValue().getJobKeys()).containsExactly(parentJob.getKey());
    final Record<JobBatchRecordValue> childBatch = ENGINE.jobs().withType(childJobType).activate();
    assertThat(childBatch.getValue().getJobKeys()).isEmpty();
  }

  @Test
  public void shouldNotSuspendActivatedJobAtSuspendTime() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String otherJobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask("activated", t -> t.zeebeJobType(jobType))
                .moveToNode("fork")
                .serviceTask("parked", t -> t.zeebeJobType(otherJobType))
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long parkedJobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(otherJobType)
            .getFirst()
            .getKey();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(2)
        .await();
    final Record<JobBatchRecordValue> batch = ENGINE.jobs().withType(jobType).activate();
    assertThat(batch.getValue().getJobKeys()).hasSize(1);
    final long activatedJobKey = batch.getValue().getJobKeys().getFirst();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // then - of the two jobs of this instance, only the still-activatable sibling is parked at
    // suspend time; the activated one is not. It is parked later on time-out (see
    // shouldParkActivatedJobWhenItTimesOutWhileSuspended).
    final List<Long> suspendedJobKeys =
        RecordingExporter.getRecords().stream()
            .filter(record -> record.getIntent() == JobIntent.SUSPENDED)
            .filter(
                record ->
                    ((JobRecordValue) record.getValue()).getProcessInstanceKey()
                        == processInstanceKey)
            .map(Record::getKey)
            .collect(Collectors.toList());
    assertThat(suspendedJobKeys).containsExactly(parkedJobKey).doesNotContain(activatedJobKey);
  }

  @Test
  public void shouldParkActivatedJobWhenItTimesOutWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final long processInstanceKey = createInstanceWithJob(jobType);
    final Record<JobBatchRecordValue> batch =
        ENGINE.jobs().withType(jobType).withTimeout(10L).activate();
    assertThat(batch.getValue().getJobKeys()).hasSize(1);
    final long activatedJobKey = batch.getValue().getJobKeys().getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - the activated job times out while the instance is still suspended
    ENGINE.increaseTime(EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL);

    // then - timed out, then parked in the same processing path; not handed out again
    assertThat(
            RecordingExporter.jobRecords(JobIntent.TIMED_OUT)
                .withRecordKey(activatedJobKey)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.jobRecords(JobIntent.SUSPENDED)
                .withRecordKey(activatedJobKey)
                .exists())
        .isTrue();
    final Record<JobBatchRecordValue> reactivated = ENGINE.jobs().withType(jobType).activate();
    assertThat(reactivated.getValue().getJobKeys()).isEmpty();
  }

  @Test
  public void shouldDeleteParkedJobWhenInstanceIsTerminated() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final long processInstanceKey = createInstanceWithJob(jobType);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  private long createInstanceWithJob(final String jobType) {
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    return processInstanceKey;
  }
}
