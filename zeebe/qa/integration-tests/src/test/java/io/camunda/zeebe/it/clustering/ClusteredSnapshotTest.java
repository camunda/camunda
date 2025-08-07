/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering;

import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Duration;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

final class ClusteredSnapshotTest {

  public static final Logger LOG = LoggerFactory.getLogger("ClusteredSnapshotTest");
  private static final Duration SNAPSHOT_INTERVAL = Duration.ofMinutes(5);

  @RegisterExtension
  private final ClusteringRuleExtension clusteringRule =
      new ClusteringRuleExtension(1, 3, 3, this::configureBroker);

  public static Stream<Arguments> snapshotTriggers() {
    return Stream.of(
        Arguments.of(
            Named.of(
                "explicit trigger snapshot",
                (Consumer<ClusteringRule>)
                    (rule) -> {
                      LOG.info("Triggering snapshots using admin api");
                      rule.triggerAndWaitForSnapshots();
                    })),
        Arguments.of(
            Named.of(
                "implicit snapshot by advancing the clock",
                (Consumer<ClusteringRule>)
                    (rule) -> {
                      LOG.info("Increasing clock by snapshot interval {}", SNAPSHOT_INTERVAL);
                      rule.getClock().addTime(SNAPSHOT_INTERVAL);
                    })));
  }

  @AfterEach
  void cleanUp() {
    ControllableExporter.updatePosition(true);
  }

  @ParameterizedTest
  @MethodSource("snapshotTriggers")
  void shouldTakeSnapshotsOnAllNodes(final Consumer<ClusteringRule> snapshotTrigger) {
    // given
    ControllableExporter.updatePosition(true);

    publishMessages();
    ControllableExporter.updatePosition(false);
    publishMessages();

    // when - then
    awaitUntilAsserted(
        (broker) -> {
          snapshotTrigger.accept(clusteringRule);
          assertThat(broker).havingSnapshot();
        });
  }

  @ParameterizedTest()
  @MethodSource("snapshotTriggers")
  void shouldSendSnapshotOnReconnect(final Consumer<ClusteringRule> snapshotTrigger) {
    // given
    final var followerId = clusteringRule.stopAnyFollower();
    final var leaderId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var adminService =
        clusteringRule.getBroker(leaderId).getBrokerContext().getBrokerAdminService();
    ControllableExporter.updatePosition(true);
    clusteringRule.fillSegments(
        2,
        clusteringRule
            .getBrokerCfg(0)
            .getExperimental()
            .getRaft()
            .getPreferSnapshotReplicationThreshold());

    Awaitility.await("Wait until all events are exported before taking snapshot")
        .until(
            () -> {
              final var partitionStatus = adminService.getPartitionStatus().get(1);
              return partitionStatus.exportedPosition() >= partitionStatus.processedPosition();
            });

    snapshotTrigger.accept(clusteringRule);

    // when
    clusteringRule.startBroker(followerId);

    // then
    clusteringRule.waitForSnapshotAtBroker(clusteringRule.getBroker(followerId));
    assertThat(clusteringRule.getBroker(followerId)).havingSnapshot();
  }

  private void awaitUntilAsserted(final Consumer<Broker> consumer) {
    Awaitility.await()
        .pollInterval(Duration.ofSeconds(1))
        .timeout(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> Assertions.assertThat(clusteringRule.getBrokers()).allSatisfy(consumer));
  }

  private void configureBroker(final BrokerCfg brokerCfg) {
    brokerCfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(32));

    // would otherwise exceed the max message size
    brokerCfg.getExperimental().getFeatures().setEnableIdentitySetup(false);

    final DataCfg data = brokerCfg.getData();
    data.setLogSegmentSize(DataSize.ofKilobytes(32));
    data.setLogIndexDensity(5);
    data.setSnapshotPeriod(SNAPSHOT_INTERVAL);

    configureExporter(brokerCfg);
  }

  private void configureExporter(final BrokerCfg brokerConfig) {
    final ExporterCfg exporterConfig = new ExporterCfg();
    exporterConfig.setClassName(ControllableExporter.class.getName());
    brokerConfig.setExporters(Collections.singletonMap("snapshot-test-exporter", exporterConfig));
  }

  private void publishMessages() {
    IntStream.range(0, 10).forEach(this::publishMaxMessageSizeMessage);
  }

  private void publishMaxMessageSizeMessage(final int key) {
    clusteringRule
        .getClient()
        .newPublishMessageCommand()
        .messageName("msg")
        .correlationKey("msg-" + key)
        .send()
        .join();
  }

  private BrokerAssert assertThat(final Broker broker) {
    return new BrokerAssert(broker, clusteringRule);
  }

  public static class ControllableExporter implements Exporter {
    static volatile boolean shouldExport = true;
    private Controller controller;

    static void updatePosition(final boolean flag) {
      shouldExport = flag;
    }

    @Override
    public void configure(final Context context) {
      context.setFilter(
          new RecordFilter() {
            @Override
            public boolean acceptType(final RecordType recordType) {
              return true;
            }

            @Override
            public boolean acceptValue(final ValueType valueType) {
              return true;
            }
          });
    }

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
    }

    @Override
    public void export(final Record<?> record) {
      if (shouldExport) {
        controller.updateLastExportedRecordPosition(record.getPosition());
      }
    }
  }

  private static class BrokerAssert extends AbstractAssert<BrokerAssert, Broker> {

    private final ClusteringRule rule;

    protected BrokerAssert(final Broker actual, final ClusteringRule rule) {
      super(actual, BrokerAssert.class);
      this.rule = rule;
    }

    public void havingSnapshot() {
      final var snapshot = rule.getSnapshot(actual);
      Assertions.assertThat(snapshot)
          .withFailMessage("No snapshot exists for broker <%s>", actual)
          .isPresent();
    }
  }
}
