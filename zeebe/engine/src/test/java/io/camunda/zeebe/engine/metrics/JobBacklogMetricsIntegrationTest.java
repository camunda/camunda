/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Reproduces the scenario of the issue: jobs exist but no worker polls for them. The backlog gauge
 * must show them and the lag gauge must grow; once a worker activates them both must go back to 0.
 */
public final class JobBacklogMetricsIntegrationTest {

  private static final String BACKLOG_METRIC = "zeebe.job.activatable.count";
  private static final String LAG_METRIC = "zeebe.job.activation.lag";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldExposeBacklogAndGrowingLagWhileNoWorkerActivates() {
    // given
    final var jobType = Strings.newRandomValidBpmnId();

    // when - jobs are created and no worker ever polls
    createJobs(jobType, 3);

    // then
    Awaitility.await("backlog shows all created jobs")
        .untilAsserted(() -> assertThat(backlog(jobType)).isEqualTo(3.0));
    Awaitility.await("lag grows while the backlog is not drained")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(lag(jobType)).isPositive());
  }

  @Test
  public void shouldDropBacklogAndLagToZeroOnceJobsAreActivated() {
    // given - a backlog with a positive lag
    final var jobType = Strings.newRandomValidBpmnId();
    createJobs(jobType, 2);
    Awaitility.await("backlog shows all created jobs")
        .untilAsserted(() -> assertThat(backlog(jobType)).isEqualTo(2.0));
    Awaitility.await("lag grows while the backlog is not drained")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(lag(jobType)).isPositive());

    // when
    engine.jobs().withType(jobType).withMaxJobsToActivate(2).activate();

    // then
    Awaitility.await("backlog drains after activation")
        .untilAsserted(() -> assertThat(backlog(jobType)).isZero());
    assertThat(lag(jobType)).isZero();
  }

  @Test
  public void shouldKeepBacklogPerJobType() {
    // given
    final var busyType = Strings.newRandomValidBpmnId();
    final var servedType = Strings.newRandomValidBpmnId();
    createJobs(busyType, 2);
    createJobs(servedType, 1);

    // when - only one type is served
    engine.jobs().withType(servedType).withMaxJobsToActivate(1).activate();

    // then
    Awaitility.await("served type drains")
        .untilAsserted(() -> assertThat(backlog(servedType)).isZero());
    assertThat(backlog(busyType)).isEqualTo(2.0);
  }

  @Test
  public void shouldDecrementBacklogWhenActivatableJobIsCanceled() {
    // given
    final var jobType = Strings.newRandomValidBpmnId();
    final var processInstanceKey = createJobs(jobType, 1);
    Awaitility.await("backlog shows the created job")
        .untilAsserted(() -> assertThat(backlog(jobType)).isEqualTo(1.0));

    // when
    engine.processInstance().withInstanceKey(processInstanceKey).cancel();
    RecordingExporter.jobRecords(JobIntent.CANCELED).withType(jobType).await();

    // then
    Awaitility.await("backlog drains after cancel")
        .untilAsserted(() -> assertThat(backlog(jobType)).isZero());
  }

  /** Creates the given number of jobs of one type and returns the last process instance key. */
  private long createJobs(final String jobType, final int count) {
    long lastProcessInstanceKey = -1L;
    for (int i = 0; i < count; i++) {
      final var record = engine.createJob(jobType, Strings.newRandomValidBpmnId());
      lastProcessInstanceKey = record.getValue().getProcessInstanceKey();
    }
    return lastProcessInstanceKey;
  }

  private double backlog(final String jobType) {
    return registry().get(BACKLOG_METRIC).tag("type", jobType).gauge().value();
  }

  private double lag(final String jobType) {
    return registry().get(LAG_METRIC).tag("type", jobType).gauge().value();
  }

  private MeterRegistry registry() {
    return engine.getMeterRegistry();
  }
}
