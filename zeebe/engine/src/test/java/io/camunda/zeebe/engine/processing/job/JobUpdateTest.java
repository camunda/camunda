/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Set;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class JobUpdateTest {

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
  public void shouldUpdateJob() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);

    final var batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    ENGINE
        .job()
        .withKey(jobKey)
        .withRetries(5)
        .withTimeout(Duration.ofMinutes(5).toMillis())
        .withChangeset(Set.of("retries", "timeout"))
        .update();

    // then
    assertThat(RecordingExporter.jobRecords().limit(3))
        .extracting(
            Record::getIntent,
            record -> record.getValue().getRetries(),
            record -> record.getValue().getTimeout())
        .containsSubsequence(
            tuple(JobIntent.CREATED, 3, -1L),
            tuple(JobIntent.UPDATE, 5, 300000L),
            tuple(JobIntent.UPDATED, 5, -1L));
  }

  @Test
  public void shouldUpdateJobWithNoChanges() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);

    final var batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    ENGINE.job().withKey(jobKey).update();

    // then
    assertThat(RecordingExporter.jobRecords().limit(3))
        .extracting(Record::getIntent)
        .containsSubsequence(JobIntent.CREATED, JobIntent.UPDATE, JobIntent.UPDATED);
  }

  @Test
  public void shouldUpdateJobWithOnlyRetries() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);

    final var batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    ENGINE.job().withKey(jobKey).withRetries(5).withChangeset(Set.of("retries")).update();

    // then
    assertThat(RecordingExporter.jobRecords().limit(3))
        .extracting(Record::getIntent, record -> record.getValue().getRetries())
        .containsSubsequence(
            tuple(JobIntent.CREATED, 3), tuple(JobIntent.UPDATE, 5), tuple(JobIntent.UPDATED, 5));
  }

  @Test
  public void shouldUpdateJobWithOnlyTimeout() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);

    final var batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    ENGINE
        .job()
        .withKey(jobKey)
        .withTimeout(Duration.ofMinutes(5).toMillis())
        .withChangeset(Set.of("timeout"))
        .update();

    // then
    assertThat(RecordingExporter.jobRecords().limit(3))
        .extracting(Record::getIntent, record -> record.getValue().getTimeout())
        .containsSubsequence(
            tuple(JobIntent.CREATED, -1L),
            tuple(JobIntent.UPDATE, 300000L),
            tuple(JobIntent.UPDATED, -1L));
  }

  @Test
  public void shouldRejectUpdateTimoutIfJobNotFound() {
    // given
    final long jobKey = 123L;

    // when
    final var jobRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .withRetries(5)
            .withTimeout(Duration.ofMinutes(5).toMillis())
            .withChangeset(Set.of("retries", "timeout"))
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(jobRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update job with key '%d', but no such job was found".formatted(jobKey));
  }
}
