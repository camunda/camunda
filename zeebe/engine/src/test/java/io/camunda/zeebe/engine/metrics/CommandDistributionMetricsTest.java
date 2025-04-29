/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.metrics.JobProcessingMetricsTest.JobMetricsTestScenario;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestWatcher;
import org.junit.runners.Parameterized.Parameter;

public class CommandDistributionMetricsTest {

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String JOB_TYPE = "job";

  @Parameter public JobMetricsTestScenario scenario;

  private final TestWatcher watcher = new RecordingExporterTestWatcher();
  private final EngineRule engine = EngineRule.multiplePartition(2);

  @Rule public final RuleChain ruleChain = RuleChain.outerRule(engine).around(watcher);

  @Test
  public void shouldTrackNormalDistribution() {
    // given
    engine.pauseProcessing(2);

    // when
    final var startMetrics = snapshotMetrics();
    final var key = triggerUnqueuedDistribution();

    // then
    final var distributingMetrics = startMetrics.diff(snapshotMetrics());
    assertThat(distributingMetrics)
        .isEqualTo(
            MetricSnapshot.empty()
                .withActive(+1.0)
                .withPending(+1.0)
                .withInflight(+1.0)
                .withRetries(0.0)
                .withAcknowledged(0.0));

    // when
    engine.resumeProcessing(2);
    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
        .withRecordKey(key)
        .await();

    // then
    final var finishedMetrics = distributingMetrics.diff(snapshotMetrics());
    assertThat(finishedMetrics)
        .isEqualTo(
            MetricSnapshot.empty()
                .withActive(-1.0)
                .withPending(-1.0)
                .withInflight(-1.0)
                .withRetries(0.0)
                .withAcknowledged(+1.0));
  }

  @Test
  public void shouldTrackQueuedDistribution() {
    // given
    engine.pauseProcessing(2);

    // when
    final var startMetrics = snapshotMetrics();
    final var firstDistribution = triggerQueuedDistribution("First");
    final var secondDistribution = triggerQueuedDistribution("Second");

    // then
    final var distributingMetrics = startMetrics.diff(snapshotMetrics());
    assertThat(distributingMetrics)
        .isEqualTo(
            MetricSnapshot.empty()
                .withActive(+2.0)
                .withPending(+2.0)
                .withInflight(+1.0)
                .withRetries(0.0)
                .withAcknowledged(0.0));

    // when
    engine.resumeProcessing(2);
    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
        .withRecordKey(secondDistribution)
        .await();

    // then
    final var finishedMetrics = distributingMetrics.diff(snapshotMetrics());
    assertThat(finishedMetrics)
        .isEqualTo(
            MetricSnapshot.empty()
                .withActive(-2.0)
                .withPending(-2.0)
                .withInflight(-1.0)
                .withRetries(0.0)
                .withAcknowledged(+2.0));
  }

  @Test
  public void shouldTrackRetriedDistribution() {
    // we need to trigger a distribution and pause the second partition long enough or accelerate
    // time to trigger a redistribution on the origin partition
  }

  @Test
  public void shouldRepopulateGaugesAfterRecovery() {
    // trigger distribution

    // hopefully triggers the recovery
    // engine.stop();
    // engine.start();
  }

  private long triggerUnqueuedDistribution() {
    final var reset = engine.clock().reset();
    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.DISTRIBUTING)
        .withRecordKey(reset.getKey())
        .await();
    return reset.getKey();
  }

  private long triggerQueuedDistribution(final String name) {
    final var deploy =
        engine
            .deployment()
            .withXmlResource(
                "process.bpmn", Bpmn.createExecutableProcess().startEvent().endEvent().done())
            .expectCreated()
            .deploy();

    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.ENQUEUED)
        .withRecordKey(deploy.getKey())
        .await();

    return deploy.getKey();
  }

  private MetricSnapshot snapshotMetrics() {
    final var active = getMeterRegistry(1).find("zeebe.command.distributions.active").gauge();
    final var pending = getMeterRegistry(1).find("zeebe.command.distributions.pending").gauge();
    final var inflight = getMeterRegistry(1).find("zeebe.command.distributions.inflight").gauge();
    final var retries =
        getMeterRegistry(1).find("zeebe.command.distributions.inflight.retries").counter();
    final var acked =
        getMeterRegistry(2).find("zeebe.command.distributions.acknowledged").counter();

    return new MetricSnapshot(
        active != null ? active.value() : 0.0,
        pending != null ? pending.value() : 0.0,
        inflight != null ? inflight.value() : 0.0,
        retries != null ? retries.count() : 0.0,
        acked != null ? acked.count() : 0.0);
  }

  private MeterRegistry getMeterRegistry(final int partition) {
    return engine
        .getProcessingState(partition)
        .getDistributionState()
        .getMetrics()
        .getMeterRegistry();
  }

  record MetricSnapshot(
      double active, double pending, double inflight, double retries, double acknowledged) {

    public MetricSnapshot diff(final MetricSnapshot other) {
      return new MetricSnapshot(
          other.active - active,
          other.pending - pending,
          other.inflight - inflight,
          other.retries - retries,
          other.acknowledged - acknowledged);
    }

    public static MetricSnapshot empty() {
      return new MetricSnapshot(0, 0, 0, 0.0, 0.0);
    }

    public MetricSnapshot withActive(final double active) {
      return new MetricSnapshot(active, pending, inflight, retries, acknowledged);
    }

    public MetricSnapshot withPending(final double pending) {
      return new MetricSnapshot(active, pending, inflight, retries, acknowledged);
    }

    public MetricSnapshot withInflight(final double inflight) {
      return new MetricSnapshot(active, pending, inflight, retries, acknowledged);
    }

    public MetricSnapshot withRetries(final double retries) {
      return new MetricSnapshot(active, pending, inflight, retries, acknowledged);
    }

    public MetricSnapshot withAcknowledged(final double acknowledged) {
      return new MetricSnapshot(active, pending, inflight, retries, acknowledged);
    }
  }
}
