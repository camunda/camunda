/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.distribution.CommandRedistributor;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.micrometer.core.instrument.MeterRegistry;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestWatcher;

public class CommandDistributionMetricsTest {

  private final TestWatcher watcher = new RecordingExporterTestWatcher();
  private final EngineRule engine = EngineRule.multiplePartition(2);

  @Rule public final RuleChain ruleChain = RuleChain.outerRule(engine).around(watcher);

  @Test
  public void shouldTrackNormalDistribution() {
    // given
    engine.pauseProcessing(2);

    // when
    final var key = triggerUnqueuedDistribution();

    // then
    assertThat(snapshotMetrics())
        .isEqualTo(
            MetricSnapshot.empty()
                .withCount(1.0)
                .withActive(1.0)
                .withPending(1.0)
                .withInflight(1.0)
                .withRetries(0.0)
                .withAcknowledged(0.0));

    // when
    engine.resumeProcessing(2);

    // then
    waitUntilCommandDistributionIs(CommandDistributionIntent.FINISHED, key);
    assertThat(snapshotMetrics())
        .isEqualTo(
            MetricSnapshot.empty()
                .withCount(1.0)
                .withActive(0.0)
                .withPending(0.0)
                .withInflight(0.0)
                .withRetries(0.0)
                .withAcknowledged(1.0));
  }

  @Test
  public void shouldTrackRetriedDistribution() throws InterruptedException {
    // given
    engine.pauseProcessing(2);

    // when
    final var key = triggerUnqueuedDistribution();

    // then
    waitUntilDistributionIsRetried(key);
    assertThat(snapshotMetrics())
        .isEqualTo(
            MetricSnapshot.empty()
                .withCount(1.0)
                .withActive(1.0)
                .withPending(1.0)
                .withInflight(1.0)
                .withRetries(1.0)
                .withAcknowledged(0.0));

    // when
    engine.resumeProcessing(2);

    // then
    waitUntilCommandDistributionIs(CommandDistributionIntent.FINISHED, key);
    assertThat(snapshotMetrics())
        .isEqualTo(
            MetricSnapshot.empty()
                .withCount(1.0)
                .withActive(0.0)
                .withPending(0.0)
                .withInflight(0.0)
                .withRetries(1.0)
                .withAcknowledged(2.0));
  }

  @Test
  public void shouldTrackQueuedDistribution() {
    // given
    engine.pauseProcessing(2);

    // when
    final var firstDistribution = triggerQueuedDistribution();
    final var secondDistribution = triggerQueuedDistribution();

    // then
    assertThat(snapshotMetrics())
        .isEqualTo(
            MetricSnapshot.empty()
                .withCount(2.0)
                .withActive(2.0)
                .withPending(2.0)
                .withInflight(1.0)
                .withRetries(0.0)
                .withAcknowledged(0.0));

    // when
    engine.resumeProcessing(2);

    // then
    waitUntilCommandDistributionIs(CommandDistributionIntent.FINISHED, secondDistribution);
    assertThat(snapshotMetrics())
        .isEqualTo(
            MetricSnapshot.empty()
                .withCount(2.0)
                .withActive(0.0)
                .withPending(0.0)
                .withInflight(0.0)
                .withRetries(0.0)
                .withAcknowledged(2.0));
  }

  @Test
  public void shouldRepopulateGaugesAfterRecovery() {
    // given
    engine.pauseProcessing(2);

    // when
    final var key = triggerUnqueuedDistribution();

    // then
    assertThat(snapshotMetrics())
        .isEqualTo(
            MetricSnapshot.empty()
                .withCount(1.0)
                .withActive(1.0)
                .withPending(1.0)
                .withInflight(1.0)
                .withRetries(0.0)
                .withAcknowledged(0.0));

    // when
    engine.stop();
    engine.start();
    waitUntilCommandDistributionIs(CommandDistributionIntent.FINISHED, key);

    // then
    assertThat(snapshotMetrics())
        .isEqualTo(
            MetricSnapshot.empty()
                .withCount(1.0)
                .withActive(0.0)
                .withPending(0.0)
                .withInflight(0.0)
                .withRetries(0.0)
                .withAcknowledged(1.0));
  }

  private long triggerUnqueuedDistribution() {
    final var reset = engine.clock().reset();
    waitUntilCommandDistributionIs(CommandDistributionIntent.DISTRIBUTING, reset.getKey());
    return reset.getKey();
  }

  private long triggerQueuedDistribution() {
    final var deploy =
        engine
            .deployment()
            .withXmlResource(
                "process.bpmn", Bpmn.createExecutableProcess().startEvent().endEvent().done())
            .expectCreated()
            .deploy();

    waitUntilCommandDistributionIs(CommandDistributionIntent.ENQUEUED, deploy.getKey());

    return deploy.getKey();
  }

  private static void waitUntilCommandDistributionIs(
      final CommandDistributionIntent state, final long key) {
    RecordingExporter.commandDistributionRecords(state).withRecordKey(key).await();
  }

  private void waitUntilDistributionIsRetried(final long key) {
    RecordingExporter.setMaximumWaitTime(100);
    Awaitility.await()
        .untilAsserted(
            () -> {
              // wait for retry mechanism to trigger second distribution
              // (while we intercepted the first acknowledgement)
              engine.getClock().addTime(CommandRedistributor.COMMAND_REDISTRIBUTION_INTERVAL);

              // Make sure we have two records on the target partition
              assertThat(RecordingExporter.records().withPartitionId(2).withRecordKey(key).limit(2))
                  .hasSize(2);
            });
    RecordingExporter.setMaximumWaitTime(5000);
  }

  private MetricSnapshot snapshotMetrics() {
    final var count = getMeterRegistry(1).find("zeebe.command.distributions").counter();
    final var active = getMeterRegistry(1).find("zeebe.command.distributions.active").gauge();
    final var pending = getMeterRegistry(1).find("zeebe.command.distributions.pending").gauge();
    final var inflight = getMeterRegistry(1).find("zeebe.command.distributions.inflight").gauge();
    final var retries =
        getMeterRegistry(1).find("zeebe.command.distributions.inflight.retries").counter();
    final var acked =
        getMeterRegistry(2).find("zeebe.command.distributions.acknowledged").counter();

    return new MetricSnapshot(
        count != null ? count.count() : 0.0,
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
      double count,
      double active,
      double pending,
      double inflight,
      double retries,
      double acknowledged) {

    public static MetricSnapshot empty() {
      return new MetricSnapshot(0, 0, 0, 0, 0, 0);
    }

    public MetricSnapshot withCount(final double count) {
      return new MetricSnapshot(count, active, pending, inflight, retries, acknowledged);
    }

    public MetricSnapshot withActive(final double active) {
      return new MetricSnapshot(count, active, pending, inflight, retries, acknowledged);
    }

    public MetricSnapshot withPending(final double pending) {
      return new MetricSnapshot(count, active, pending, inflight, retries, acknowledged);
    }

    public MetricSnapshot withInflight(final double inflight) {
      return new MetricSnapshot(count, active, pending, inflight, retries, acknowledged);
    }

    public MetricSnapshot withRetries(final double retries) {
      return new MetricSnapshot(count, active, pending, inflight, retries, acknowledged);
    }

    public MetricSnapshot withAcknowledged(final double acknowledged) {
      return new MetricSnapshot(count, active, pending, inflight, retries, acknowledged);
    }
  }
}
