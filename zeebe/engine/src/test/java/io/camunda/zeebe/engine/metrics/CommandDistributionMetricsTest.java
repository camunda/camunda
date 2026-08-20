/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.collection.Tuple;
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
        .satisfies(
            metrics -> assertThat(metrics.count).isOne(),
            metrics -> assertThat(metrics.active).isOne(),
            metrics -> assertThat(metrics.pending).isOne(),
            metrics -> assertThat(metrics.inflight).isOne());

    // when
    engine.resumeProcessing(2);

    // then
    waitUntilCommandDistributionIs(CommandDistributionIntent.FINISHED, key);
    assertThat(snapshotMetrics())
        .satisfies(
            metrics -> assertThat(metrics.count).isOne(),
            metrics -> assertThat(metrics.active).isZero(),
            metrics -> assertThat(metrics.pending).isZero(),
            metrics -> assertThat(metrics.inflight).isZero());
  }

  @Test
  public void shouldTrackRetriedDistribution() {
    // given
    engine.pauseProcessing(2);

    // when
    final var key = triggerUnqueuedDistribution();

    // then
    waitUntilDistributionIsRetried(key);
    assertThat(snapshotMetrics())
        .satisfies(
            metrics -> assertThat(metrics.count).isOne(),
            metrics -> assertThat(metrics.active).isOne(),
            metrics -> assertThat(metrics.pending).isOne(),
            metrics -> assertThat(metrics.inflight).isOne(),
            metrics -> assertThat(metrics.retries).isGreaterThanOrEqualTo(1));

    // when
    engine.resumeProcessing(2);

    // then
    // wait for second (retried) acknowledgement to be processed
    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.ACKNOWLEDGE)
        .withRejectionType(RejectionType.NOT_FOUND)
        .withRecordKey(key)
        .await();

    assertThat(snapshotMetrics())
        .satisfies(
            metrics -> assertThat(metrics.count).isOne(),
            metrics -> assertThat(metrics.active).isZero(),
            metrics -> assertThat(metrics.pending).isZero(),
            metrics -> assertThat(metrics.inflight).isZero(),
            metrics -> assertThat(metrics.retries).isGreaterThanOrEqualTo(1),
            metrics -> assertThat(metrics.ackSent).isGreaterThanOrEqualTo(2),
            metrics -> assertThat(metrics.ackReceived).isGreaterThanOrEqualTo(2));
  }

  @Test
  public void shouldTrackQueuedDistribution() {
    // given
    engine.pauseProcessing(2);

    // when
    final var distributions = new Tuple<>(triggerQueuedDistribution(), triggerQueuedDistribution());

    // then
    assertThat(snapshotMetrics())
        .satisfies(
            metrics -> assertThat(metrics.count).isEqualTo(2),
            metrics -> assertThat(metrics.active).isEqualTo(2),
            metrics -> assertThat(metrics.pending).isEqualTo(2),
            metrics -> assertThat(metrics.inflight).isEqualTo(1));

    // when
    engine.resumeProcessing(2);

    // then
    waitUntilCommandDistributionIs(CommandDistributionIntent.FINISHED, distributions.getRight());
    assertThat(snapshotMetrics())
        .satisfies(
            metrics -> assertThat(metrics.count).isEqualTo(2),
            metrics -> assertThat(metrics.active).isZero(),
            metrics -> assertThat(metrics.pending).isZero(),
            metrics -> assertThat(metrics.inflight).isZero(),
            metrics -> assertThat(metrics.retries).isGreaterThanOrEqualTo(0),
            metrics -> assertThat(metrics.ackSent).isGreaterThanOrEqualTo(2),
            metrics -> assertThat(metrics.ackReceived).isGreaterThanOrEqualTo(2));
  }

  @Test
  public void shouldRepopulateGaugesAfterRecovery() {
    // given
    engine.pauseProcessing(2);

    // when
    final var key = triggerUnqueuedDistribution();

    // then
    assertThat(snapshotMetrics())
        .satisfies(
            metrics -> assertThat(metrics.count).isOne(),
            metrics -> assertThat(metrics.active).isOne(),
            metrics -> assertThat(metrics.pending).isOne(),
            metrics -> assertThat(metrics.inflight).isOne());

    // when
    engine.stop();
    engine.start(); // starts processing partition 2 again

    // then
    // we need to wait for processing to be done, to have deterministic assertions
    // thus we can only assert that after recovery the gauges were repopulated to 1
    // and then through the continued processing decremented back to zero
    // (signaled by the ackSent/Received)
    waitUntilCommandDistributionIs(CommandDistributionIntent.FINISHED, key);
    assertThat(snapshotMetrics())
        .satisfies(
            metrics ->
                assertThat(metrics.count)
                    .describedAs("Counters will not be recovered and are expected to reset to zero")
                    .isZero(),
            metrics -> assertThat(metrics.active).isZero(),
            metrics -> assertThat(metrics.pending).isZero(),
            metrics -> assertThat(metrics.inflight).isZero());
  }

  private long triggerUnqueuedDistribution() {
    // clock reset is used to trigger a command within the engine that requires distribution
    // Note: this is not affecting the actual actor clock!
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
    RecordingExporter.await()
        .untilAsserted(
            () -> {
              // wait for retry mechanism to trigger second distribution
              // (while we intercepted the first acknowledgement)
              engine
                  .getClock()
                  .addTime(EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL);

              // Make sure we have two records on the target partition
              assertThat(RecordingExporter.records().withPartitionId(2).withRecordKey(key).limit(2))
                  .hasSizeGreaterThan(1);
            });
  }

  private MetricSnapshot snapshotMetrics() {
    return new MetricSnapshot(
        getCounterValue(1, "zeebe.command.distributions"),
        getGaugeValue(1, "zeebe.command.distributions.active"),
        getGaugeValue(1, "zeebe.command.distributions.pending"),
        getGaugeValue(1, "zeebe.command.distributions.inflight"),
        getCounterValue(1, "zeebe.command.distributions.inflight.retries"),
        getCounterValue(1, "zeebe.command.distributions.acknowledged.received"),
        getCounterValue(2, "zeebe.command.distributions.acknowledged.sent"));
  }

  private double getCounterValue(final int partition, final String metricName) {
    final var counter = engine.getMeterRegistry(partition).find(metricName).counter();
    return counter != null ? counter.count() : 0.0;
  }

  private double getGaugeValue(final int partition, final String metricName) {
    final var gauge = engine.getMeterRegistry(partition).find(metricName).gauge();
    return gauge != null ? gauge.value() : 0.0;
  }

  record MetricSnapshot(
      double count,
      double active,
      double pending,
      double inflight,
      double retries,
      double ackReceived,
      double ackSent) {}
}
