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
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;

public final class JobRecurAfterBackoffTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final String jobType = Strings.newRandomValidBpmnId();

  @Test
  public void shouldRecurJobWhenClockReadsExactlyItsRecurringTime() {
    // given - the clock only moves when the test moves it, so the backoff check and the processing
    // of its command both observe the job's recurring time
    engine.getClock().pinCurrentTime();
    final Duration backOff = Duration.ofSeconds(30);
    final Record<JobRecordValue> job = engine.createJob(jobType, Strings.newRandomValidBpmnId());
    final long jobKey = job.getKey();
    engine.jobs().withType(jobType).activate();
    engine
        .job()
        .withKey(jobKey)
        .ofInstance(job.getValue().getProcessInstanceKey())
        .withRetries(3)
        .withBackOff(backOff)
        .fail();

    // when
    engine.increaseTime(backOff);

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.RECURRED_AFTER_BACKOFF)
                .withRecordKey(jobKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldExplainRejectionWhenBackoffHasNotEnded() {
    // given
    final Record<JobRecordValue> job = engine.createJob(jobType, Strings.newRandomValidBpmnId());
    final long jobKey = job.getKey();
    engine.jobs().withType(jobType).activate();
    final Record<JobRecordValue> failed =
        engine
            .job()
            .withKey(jobKey)
            .ofInstance(job.getValue().getProcessInstanceKey())
            .withRetries(3)
            .withBackOff(Duration.ofDays(1))
            .fail();

    // when
    engine.writeRecords(
        RecordToWrite.command().job(JobIntent.RECUR_AFTER_BACKOFF, failed.getValue()).key(jobKey));

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.RECUR_AFTER_BACKOFF)
                .onlyCommandRejections()
                .withRecordKey(jobKey)
                .getFirst()
                .getRejectionReason())
        .isEqualTo(
            "Expected to back off failed job with key '%d', but its backoff has not ended yet"
                .formatted(jobKey));
  }
}
