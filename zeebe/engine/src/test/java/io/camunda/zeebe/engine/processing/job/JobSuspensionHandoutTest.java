/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

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

    // then - the child instance's job is still handed out. This holds because the behavior never
    // reaches the child instance at all (its root has no parent link in the element instance
    // tree), not because of the processInstanceKey filter - see
    // ProcessInstanceSuspensionJobBehavior's class javadoc.
    final Record<JobBatchRecordValue> batch = ENGINE.jobs().withType(jobType).activate();
    assertThat(batch.getValue().getJobKeys()).containsExactly(childJob.getKey());
  }

  @Test
  public void shouldNotSuspendActivatedJob() {
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

    // then - of the two jobs of this instance, only the still-activatable sibling is parked; the
    // activated one is not. Read the already-exported records directly (no further wait) so the
    // check covers every Job.SUSPENDED record the suspend command produced, not just the first one
    // a blocking record stream happens to hand back.
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
