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
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
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
    final int retries = 5;
    final long timeout = Duration.ofMinutes(5).toMillis();

    final var batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    ENGINE.job().withKey(jobKey).withRetries(retries).withTimeout(timeout).update();

    // then
    assertThat(RecordingExporter.jobRecords().limit(4))
        .extracting(Record::getIntent)
        .containsSubsequence(
            JobIntent.CREATED,
            JobIntent.UPDATE,
            JobIntent.RETRIES_UPDATED,
            JobIntent.TIMEOUT_UPDATED);
  }

  @Test
  public void shouldUpdateJobWithOnlyRetries() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final int retries = 5;

    final var batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    ENGINE.job().withKey(jobKey).withRetries(retries).update();

    // then
    assertThat(RecordingExporter.jobRecords().limit(4))
        .extracting(Record::getIntent)
        .containsSubsequence(JobIntent.CREATED, JobIntent.UPDATE, JobIntent.RETRIES_UPDATED)
        .doesNotContain(JobIntent.TIMEOUT_UPDATED);
  }

  @Test
  public void shouldUpdateJobWithOnlyTimeout() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final long timeout = Duration.ofMinutes(5).toMillis();

    final var batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    ENGINE.job().withKey(jobKey).withTimeout(timeout).update();

    // then
    assertThat(RecordingExporter.jobRecords().limit(4))
        .extracting(Record::getIntent)
        .containsSubsequence(JobIntent.CREATED, JobIntent.UPDATE, JobIntent.TIMEOUT_UPDATED)
        .doesNotContain(JobIntent.RETRIES_UPDATED);
  }

  @Test
  public void shouldRejectUpdateTimoutIfJobNotFound() {
    // given
    final long jobKey = 123L;
    final int retries = 3;
    final long timeout = Duration.ofMinutes(10).toMillis();

    // when
    final var jobRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .withRetries(retries)
            .withTimeout(timeout)
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(jobRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update job with key '%d', but no such job was found".formatted(jobKey));
  }
}
