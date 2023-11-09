/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class JobUpdateTimeoutTest {
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
  public void shouldIncreaseJobTimeout() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final var batchRecord =
        ENGINE.jobs().withType(jobType).withTimeout(Duration.ofMinutes(5).toMillis()).activate();
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    final long timeout = Duration.ofMinutes(10).toMillis();

    // when
    final var updatedRecord = ENGINE.job().withKey(jobKey).withTimeout(timeout).updateTimeout();

    // then
    Assertions.assertThat(updatedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.TIMEOUT_UPDATED);
    assertThat(updatedRecord.getKey()).isEqualTo(jobKey);

    assertThat(updatedRecord.getValue().getDeadline()).isNotEqualTo(job.getDeadline());

    assertThat(updatedRecord.getValue().getDeadline())
        .isCloseTo(
            ActorClock.currentTimeMillis() + timeout, within(Duration.ofMillis(100).toMillis()));
  }

  @Test
  public void shouldDecreaseJobTimeout() {}

  @Test
  public void shouldRejectUpdateTimoutIfJobNotFound() {}

  @Test
  public void shouldRejectUpdateTimoutIfDeadlineNotFound() {}
}
