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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Covers resuming the parked jobs of an instance one at a time via the {@code RESUME_JOBS} loop in
 * {@link ProcessInstanceResumeJobsProcessor}, instead of writing every job's {@code Job.RESUMED}
 * (and, for a stream-activatable job, its {@code JobBatch.ACTIVATED}) into the single batch that
 * finishes the drain. One job per cycle bounds each cycle's batch to a single job's own activation
 * regardless of how many jobs the instance parked at suspend time.
 */
public final class ResumeProcessInstanceJobDrainTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final int JOB_COUNT = 5;

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldResumeEveryParkedJobOneCycleAtATime() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    deploy(processId, jobType);
    final long processInstanceKey = start(processId, JOB_COUNT);
    final var jobKeys = createdJobKeys(processInstanceKey, JOB_COUNT);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    assertThat(ENGINE.jobs().withType(jobType).activate().getValue().getJobKeys()).isEmpty();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - one RESUME_JOBS cycle un-parks exactly one job, plus a final cycle that finds none
    // left and hands off to COMPLETE_RESUMING
    final var resumeJobsCount =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.RESUME_JOBS)
            .withProcessInstanceKey(processInstanceKey)
            .limit(JOB_COUNT + 1)
            .count();
    assertThat(resumeJobsCount).isEqualTo(JOB_COUNT + 1);

    final var resumedJobKeys =
        RecordingExporter.jobRecords(JobIntent.RESUMED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(JOB_COUNT)
            .map(Record::getKey)
            .toList();
    assertThat(resumedJobKeys).containsExactlyInAnyOrderElementsOf(jobKeys);

    // every RESUMED job is handed out again with the priority it had before suspend
    final Record<JobBatchRecordValue> batch =
        ENGINE.jobs().withType(jobType).withMaxJobsToActivate(JOB_COUNT).activate();
    assertThat(batch.getValue().getJobKeys()).containsExactlyInAnyOrderElementsOf(jobKeys);
    assertThat(batch.getValue().getJobs())
        .allSatisfy(job -> assertThat(job.getPriority()).isEqualTo(42));
  }

  @Test
  public void shouldNotResumeParkedJobOfAnotherSuspendedInstance() {
    // given - two instances of the same process; only one is resumed
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    deploy(processId, jobType);
    final long resumedInstanceKey = start(processId, 1);
    final long resumedJobKey = createdJobKeys(resumedInstanceKey, 1).getFirst();
    final long otherInstanceKey = start(processId, 1);
    createdJobKeys(otherInstanceKey, 1);
    ENGINE.processInstance().withInstanceKey(resumedInstanceKey).suspend();
    ENGINE.processInstance().withInstanceKey(otherInstanceKey).suspend();

    // when
    ENGINE.processInstance().withInstanceKey(resumedInstanceKey).resume();

    // then - only the resumed instance's job is un-parked and handed out again; the other
    // instance's job stays parked, so it does not appear in this activation
    assertThat(
            RecordingExporter.jobRecords(JobIntent.RESUMED).withRecordKey(resumedJobKey).exists())
        .isTrue();
    assertThat(ENGINE.jobs().withType(jobType).activate().getValue().getJobKeys())
        .containsExactly(resumedJobKey);
  }

  private static void deploy(final String processId, final String jobType) {
    ENGINE.deployment().withXmlResource(parallelMultiInstanceProcess(processId, jobType)).deploy();
  }

  private static long start(final String processId, final int jobCount) {
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVariable("items", IntStream.range(0, jobCount).boxed().toList())
        .create();
  }

  private static BpmnModelInstance parallelMultiInstanceProcess(
      final String processId, final String jobType) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .serviceTask(
            "task",
            t ->
                t.zeebeJobType(jobType)
                    .zeebeJobPriority("42")
                    .multiInstance(
                        m -> m.zeebeInputCollectionExpression("items").zeebeInputElement("item")))
        .endEvent()
        .done();
  }

  private static List<Long> createdJobKeys(final long processInstanceKey, final int jobCount) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(jobCount)
        .map(Record::getKey)
        .toList();
  }
}
