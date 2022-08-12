/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CompleteJobTest {

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
  public void shouldCompleteJob() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);

    // when
    final Record<JobRecordValue> jobCompletedRecord =
        ENGINE.job().withKey(batchRecord.getValue().getJobKeys().get(0)).complete();

    // then
    final JobRecordValue recordValue = jobCompletedRecord.getValue();

    Assertions.assertThat(jobCompletedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);

    Assertions.assertThat(recordValue)
        .hasWorker(batchRecord.getValue().getWorker())
        .hasType(job.getType())
        .hasRetries(job.getRetries())
        .hasDeadline(job.getDeadline());
  }

  @Test
  public void shouldCompleteJobWithTenant() {
    // given
    final String tenantId = "foo";
    ENGINE.createJob(jobType, PROCESS_ID, tenantId);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).withTenantId(tenantId).activate();
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);

    // when
    final Record<JobRecordValue> jobCompletedRecord =
        ENGINE
            .job()
            .withKey(batchRecord.getValue().getJobKeys().get(0))
            .withTenantId(tenantId)
            .complete();

    // then
    final JobRecordValue recordValue = jobCompletedRecord.getValue();

    Assertions.assertThat(jobCompletedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);

    Assertions.assertThat(recordValue)
        .hasWorker(batchRecord.getValue().getWorker())
        .hasType(job.getType())
        .hasTenantId(tenantId)
        .hasRetries(job.getRetries())
        .hasDeadline(job.getDeadline());
  }

  @Test
  public void shouldRejectCompletionIfDifferentTenant() {
    // given
    final String tenantIdA = "foo";
    final String tenantIdB = "bar";
    ENGINE.createJob(jobType, PROCESS_ID, tenantIdA);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).withTenantId(tenantIdA).activate();

    final Long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(jobKey).withTenantId(tenantIdB).complete();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.NOT_FOUND);
    Assertions.assertThat(jobRecord.getValue()).hasTenantId(tenantIdB);
  }

  @Test
  public void shouldRejectCompletionIfJobNotFound() {
    // given
    final int key = 123;

    // when
    final Record<JobRecordValue> jobRecord = ENGINE.job().withKey(key).expectRejection().complete();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldCompleteJobWithVariables() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();

    // when
    final Record<JobRecordValue> completedRecord =
        ENGINE
            .job()
            .withKey(batchRecord.getValue().getJobKeys().get(0))
            .withVariables("{'foo':'bar'}")
            .complete();

    // then
    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);
    assertThat(completedRecord.getValue().getVariables()).containsExactly(entry("foo", "bar"));
  }

  @Test
  public void shouldCompleteJobWithNilVariables() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();

    // when
    final Record<JobRecordValue> completedRecord =
        ENGINE
            .job()
            .withKey(batchRecord.getValue().getJobKeys().get(0))
            .withVariables(new UnsafeBuffer(MsgPackHelper.NIL))
            .complete();

    // then
    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);
    assertThat(completedRecord.getValue().getVariables()).isEmpty();
  }

  @Test
  public void shouldCompleteJobWithZeroLengthVariables() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();

    // when
    final Record<JobRecordValue> completedRecord =
        ENGINE
            .job()
            .withKey(batchRecord.getValue().getJobKeys().get(0))
            .withVariables(new UnsafeBuffer(new byte[0]))
            .complete();

    // then
    Assertions.assertThat(completedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);
    assertThat(completedRecord.getValue().getVariables()).isEmpty();
  }

  @Test
  public void shouldThrowExceptionOnCompletionIfVariablesAreInvalid() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();

    final byte[] invalidVariables = new byte[] {1}; // positive fixnum, i.e. no object

    // when
    final Throwable throwable =
        catchThrowable(
            () ->
                ENGINE
                    .job()
                    .withKey(batchRecord.getValue().getJobKeys().get(0))
                    .withVariables(new UnsafeBuffer(invalidVariables))
                    .expectRejection()
                    .complete());

    // then
    assertThat(throwable).isInstanceOf(RuntimeException.class);
    assertThat(throwable.getMessage()).contains("Property 'variables' is invalid");
    assertThat(throwable.getMessage())
        .contains("Expected document to be a root level object, but was 'INTEGER'");
  }

  @Test
  public void shouldRejectCompletionIfJobIsCompleted() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();

    final Long jobKey = batchRecord.getValue().getJobKeys().get(0);
    ENGINE.job().withKey(jobKey).complete();

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(jobKey).expectRejection().complete();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectCompletionIfJobIsFailed() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);

    // when
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();
    final Long jobKey = batchRecord.getValue().getJobKeys().get(0);
    ENGINE.job().withKey(jobKey).fail();

    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(jobKey).expectRejection().complete();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.INVALID_STATE);
  }
}
