/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobBatchRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobBatchDeliveryAckTest {

  private static final Duration DELIVERY_ACK_TIMEOUT = Duration.ofMillis(100);
  private static final String PROCESS_ID = "process";
  private static final int PARTITION_ID = 1;

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setJobsDeliveryAckTimeout(DELIVERY_ACK_TIMEOUT));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String jobType;
  private long deliveryAttemptKey;

  @Before
  public void setup() {
    jobType = Strings.newRandomValidBpmnId();
    deliveryAttemptKey = System.nanoTime();
  }

  @Test
  public void shouldStampDeliveryDeadlineWhenAttemptKeyPresent() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final long before = ENGINE.getClock().getCurrentTimeInMillis();

    // when
    final Record<JobBatchRecordValue> activated =
        ENGINE.jobs().withType(jobType).withDeliveryAttemptKey(deliveryAttemptKey).activate();

    // then
    assertThat(activated.getValue().getDeliveryAttemptKey()).isEqualTo(deliveryAttemptKey);
    assertThat(activated.getValue().getDeliveryDeadline())
        .isGreaterThanOrEqualTo(before + DELIVERY_ACK_TIMEOUT.toMillis());
    assertThat(activated.getValue().getJobKeys()).isNotEmpty();
  }

  @Test
  public void shouldNotStampDeliveryDeadlineWithoutAttemptKey() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);

    // when
    final Record<JobBatchRecordValue> activated = ENGINE.jobs().withType(jobType).activate();

    // then
    assertThat(activated.getValue().getDeliveryAttemptKey()).isZero();
    assertThat(activated.getValue().getDeliveryDeadline()).isZero();
  }

  @Test
  public void shouldAcknowledgePendingDelivery() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> activated =
        ENGINE.jobs().withType(jobType).withDeliveryAttemptKey(deliveryAttemptKey).activate();
    final long jobKey = activated.getValue().getJobKeys().getFirst();

    // when
    writeAcknowledge(deliveryAttemptKey, jobType);

    // then
    final Record<JobBatchRecordValue> acknowledged =
        jobBatchRecords(JobBatchIntent.ACKNOWLEDGED)
            .withType(jobType)
            .filter(r -> r.getValue().getDeliveryAttemptKey() == deliveryAttemptKey)
            .getFirst();
    assertThat(acknowledged.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(acknowledged.getValue().getDeliveryAttemptKey()).isEqualTo(deliveryAttemptKey);

    // job stays activated (ACK does not yield)
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                ignored -> jobRecords(JobIntent.YIELDED).withRecordKey(jobKey).exists()))
        .isFalse();
  }

  @Test
  public void shouldRejectPendingDeliveryAndYieldJobs() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> activated =
        ENGINE.jobs().withType(jobType).withDeliveryAttemptKey(deliveryAttemptKey).activate();
    final long jobKey = activated.getValue().getJobKeys().getFirst();

    // when
    writeReject(deliveryAttemptKey, jobType);

    // then
    final Record<JobBatchRecordValue> rejected =
        jobBatchRecords(JobBatchIntent.REJECTED)
            .withType(jobType)
            .filter(r -> r.getValue().getDeliveryAttemptKey() == deliveryAttemptKey)
            .getFirst();
    assertThat(rejected.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(rejected.getValue().getJobKeys()).containsExactly(jobKey);

    final Record<JobRecordValue> yielded =
        jobRecords(JobIntent.YIELDED).withRecordKey(jobKey).getFirst();
    assertThat(yielded.getValue().getType()).isEqualTo(jobType);

    // and the job can be activated again
    final Record<JobBatchRecordValue> reactivated = ENGINE.jobs().withType(jobType).activate();
    assertThat(reactivated.getValue().getJobKeys()).containsExactly(jobKey);
  }

  @Test
  public void shouldBeIdempotentOnDoubleAcknowledge() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    ENGINE.jobs().withType(jobType).withDeliveryAttemptKey(deliveryAttemptKey).activate();
    writeAcknowledge(deliveryAttemptKey, jobType);
    jobBatchRecords(JobBatchIntent.ACKNOWLEDGED)
        .filter(r -> r.getValue().getDeliveryAttemptKey() == deliveryAttemptKey)
        .await();

    // when
    writeAcknowledge(deliveryAttemptKey, jobType);
    jobBatchRecords(JobBatchIntent.ACKNOWLEDGED)
        .filter(r -> r.getValue().getDeliveryAttemptKey() == deliveryAttemptKey)
        .skip(1)
        .await();

    // then
    assertThat(
            jobBatchRecords(JobBatchIntent.ACKNOWLEDGED)
                .withType(jobType)
                .filter(r -> r.getValue().getDeliveryAttemptKey() == deliveryAttemptKey)
                .limit(2)
                .count())
        .isEqualTo(2);
  }

  @Test
  public void shouldBeIdempotentOnDoubleReject() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> activated =
        ENGINE.jobs().withType(jobType).withDeliveryAttemptKey(deliveryAttemptKey).activate();
    final long jobKey = activated.getValue().getJobKeys().getFirst();
    writeReject(deliveryAttemptKey, jobType);
    jobRecords(JobIntent.YIELDED).withRecordKey(jobKey).await();
    jobBatchRecords(JobBatchIntent.REJECTED)
        .filter(r -> r.getValue().getDeliveryAttemptKey() == deliveryAttemptKey)
        .await();

    // when
    writeReject(deliveryAttemptKey, jobType);
    jobBatchRecords(JobBatchIntent.REJECTED)
        .filter(r -> r.getValue().getDeliveryAttemptKey() == deliveryAttemptKey)
        .skip(1)
        .await();

    // then — second reject does not yield again
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                ignored -> jobRecords(JobIntent.YIELDED).withRecordKey(jobKey).skip(1).exists()))
        .isFalse();
    assertThat(
            jobBatchRecords(JobBatchIntent.REJECTED)
                .withType(jobType)
                .filter(r -> r.getValue().getDeliveryAttemptKey() == deliveryAttemptKey)
                .limit(2)
                .count())
        .isEqualTo(2);
  }

  @Test
  public void shouldYieldOnDeliveryAckTimeout() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> activated =
        ENGINE.jobs().withType(jobType).withDeliveryAttemptKey(deliveryAttemptKey).activate();
    final long jobKey = activated.getValue().getJobKeys().getFirst();
    final long deliveryDeadline = activated.getValue().getDeliveryDeadline();
    assertThat(deliveryDeadline).isPositive();

    // when — advance past delivery deadline + checker poll interval (same pattern as
    // JobTimeOutTest)
    ENGINE.increaseTime(
        DELIVERY_ACK_TIMEOUT.plus(EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL));

    // then
    final Record<JobRecordValue> yielded =
        jobRecords(JobIntent.YIELDED).withRecordKey(jobKey).getFirst();
    assertThat(yielded.getValue().getType()).isEqualTo(jobType);

    final Record<JobBatchRecordValue> rejected =
        jobBatchRecords(JobBatchIntent.REJECTED)
            .withType(jobType)
            .filter(r -> r.getValue().getDeliveryAttemptKey() == deliveryAttemptKey)
            .getFirst();
    assertThat(rejected.getValue().getJobKeys()).containsExactly(jobKey);

    final Record<JobBatchRecordValue> reactivated = ENGINE.jobs().withType(jobType).activate();
    assertThat(reactivated.getValue().getJobKeys()).containsExactly(jobKey);
  }

  @Test
  public void shouldNotTimeoutAfterAcknowledge() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> activated =
        ENGINE.jobs().withType(jobType).withDeliveryAttemptKey(deliveryAttemptKey).activate();
    final long jobKey = activated.getValue().getJobKeys().getFirst();
    writeAcknowledge(deliveryAttemptKey, jobType);
    jobBatchRecords(JobBatchIntent.ACKNOWLEDGED)
        .filter(r -> r.getValue().getDeliveryAttemptKey() == deliveryAttemptKey)
        .await();

    // when
    ENGINE.increaseTime(
        DELIVERY_ACK_TIMEOUT.plus(
            EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL.multipliedBy(2)));

    // then — job remains activated (no yield from delivery checker)
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                ignored -> jobRecords(JobIntent.YIELDED).withRecordKey(jobKey).exists()))
        .isFalse();
  }

  private void writeAcknowledge(final long attemptKey, final String type) {
    final var command = new JobBatchRecord();
    command.setType(type);
    command.setDeliveryAttemptKey(attemptKey);
    ENGINE.writeCommandOnPartition(PARTITION_ID, attemptKey, JobBatchIntent.ACKNOWLEDGE, command);
  }

  private void writeReject(final long attemptKey, final String type) {
    final var command = new JobBatchRecord();
    command.setType(type);
    command.setDeliveryAttemptKey(attemptKey);
    ENGINE.writeCommandOnPartition(PARTITION_ID, attemptKey, JobBatchIntent.REJECT, command);
  }
}
