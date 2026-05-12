/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class YieldJobTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static String jobType;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Before
  public void setup() {
    jobType = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldYield() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    final Record<JobRecordValue> yieldRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .withType(jobType)
            .ofInstance(job.getProcessInstanceKey())
            .yield();

    // then
    Assertions.assertThat(yieldRecord).hasRecordType(RecordType.EVENT).hasIntent(JobIntent.YIELDED);
    Assertions.assertThat(yieldRecord.getValue()).hasWorker(job.getWorker()).hasType(job.getType());
  }

  @Test
  public void shouldRejectYieldIfJobNotActivated() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    ENGINE.job().withKey(jobKey).fail();

    // when
    final Record<JobRecordValue> yieldRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .ofInstance(job.getProcessInstanceKey())
            .expectRejection()
            .yield();

    // then
    Assertions.assertThat(yieldRecord)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                "Expected to yield job with key '%d', but it is in state '%s'",
                jobKey, State.FAILED));
  }

  @Test
  public void shouldRejectJobYieldForBannedProcessInstance() {
    // given
    final Record<JobRecordValue> jobCreated = ENGINE.createJob(jobType, PROCESS_ID);
    final long processInstanceKey = jobCreated.getValue().getProcessInstanceKey();
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();
    final Long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // ban the process instance
    ENGINE.banInstanceInNewTransaction(1, processInstanceKey);
    RecordingExporter.errorRecords().withRecordKey(processInstanceKey).await();

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(jobKey).ofInstance(processInstanceKey).expectRejection().yield();

    // then
    Assertions.assertThat(jobRecord)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to process command for process instance with key '%d', but the process instance is banned due to previous errors. The process instance can't be recovered, but it can be cancelled."
                .formatted(processInstanceKey));
  }
}
