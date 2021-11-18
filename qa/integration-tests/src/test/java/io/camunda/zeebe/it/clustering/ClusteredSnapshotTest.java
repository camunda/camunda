/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.snapshots.SnapshotId;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.util.unit.DataSize;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@RunWith(Parameterized.class)
public class ClusteredSnapshotTest {

  private static final Duration SNAPSHOT_INTERVAL = Duration.ofMinutes(5);

  @Rule
  public final ClusteringRule clusteringRule = new ClusteringRule(1, 3, 3, this::configureBroker);

  @Parameter(0)
  public Consumer<ClusteringRule> snapshotTrigger;

  @Parameter(1)
  public String description;

  @Parameters(name = "{index}: {1}")
  public static Object[][] snapshotTriggers() {
    return new Object[][] {
      new Object[] {
        (Consumer<ClusteringRule>) ClusteringRule::triggerAndWaitForSnapshots,
        "explicit trigger snapshot"
      },
      new Object[] {
        (Consumer<ClusteringRule>) (rule) -> rule.getClock().addTime(SNAPSHOT_INTERVAL),
        "implicit snapshot by advancing the clock"
      }
    };
  }

  @After
  public void cleanUp() {
    ControllableExporter.updatePosition(true);
    ControllableExporter.EXPORTED_RECORDS.set(0);
    ControllableExporter.RECORD_TYPE_FILTER.set(r -> true);
    ControllableExporter.VALUE_TYPE_FILTER.set(r -> true);
  }

  @Test
  public void shouldTakeSnapshotsOnAllNodes() {
    // given
    ControllableExporter.updatePosition(true);

    publishMessages();
    ControllableExporter.updatePosition(false);
    publishMessages();

    // when - then
    awaitUntilAsserted(
        (broker) -> {
          triggerSnapshotRoutine();
          assertThat(broker).havingSnapshot();
        });
  }

  @Test
  public void shouldSendSnapshotOnReconnect() {
    // given
    final var followerId = clusteringRule.stopAnyFollower();
    final var leaderId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var adminService =
        clusteringRule.getBroker(leaderId).getBrokerContext().getBrokerAdminService();
    ControllableExporter.updatePosition(true);
    clusteringRule.fillSegment();

    Awaitility.await("Wait until all events are exported before taking snapshot")
        .until(
            () -> {
              final var partitionStatus = adminService.getPartitionStatus().get(1);
              return partitionStatus.getExportedPosition()
                  >= partitionStatus.getProcessedPosition();
            });

    snapshotTrigger.accept(clusteringRule);

    // when
    clusteringRule.startBroker(followerId);

    // then
    clusteringRule.waitForSnapshotAtBroker(clusteringRule.getBroker(followerId));
    assertThat(clusteringRule.getBroker(followerId)).havingSnapshot();
  }

  @Test
  public void shouldIncludeExportedPositionInSnapshot() {
    // given
    ControllableExporter.updatePosition(true);

    publishMessages();
    ControllableExporter.updatePosition(false);
    publishMessages();

    // when - then
    awaitUntilAsserted(
        (broker) -> {
          triggerSnapshotRoutine();
          assertThat(broker)
              .havingSnapshot()
              .withExportedPosition(ControllableExporter.lastUpdatedPosition);
        });
  }

  @Test
  public void shouldTakeSnapshotWhenExporterPositionIsMinusOne() {
    // given
    // an exporter is configured, but nothing gets exported
    ControllableExporter.updatePosition(false);
    publishMessages();

    // when
    triggerSnapshotRoutine();

    // then
    awaitUntilAsserted(
        (broker) -> {
          assertThat(broker).havingSnapshot().withIndex(0).withTerm(0).withExportedPosition(0);
        });
  }

  @Test
  public void shouldKeepIndexAndTerm() {
    // given
    ControllableExporter.updatePosition(false);
    removeExporters();
    restartCluster();
    publishMessages();
    triggerSnapshotRoutine();

    // expect - each broker has created a snapshot
    awaitUntilAsserted(
        (broker) -> {
          assertThat(broker).havingSnapshot().withExportedPosition(Long.MAX_VALUE);
        });

    final Map<Integer, SnapshotId> snapshotsByBroker =
        clusteringRule.getTopologyFromClient().getBrokers().stream()
            .collect(Collectors.toMap(BrokerInfo::getNodeId, this::getSnapshot));

    // when
    configureExporters();
    restartCluster();
    publishMessages();
    triggerSnapshotRoutine();

    // then
    awaitUntilAsserted(
        (broker) -> {
          final SnapshotId expectedSnapshot =
              snapshotsByBroker.get(broker.getConfig().getCluster().getNodeId());
          assertThat(broker)
              .havingSnapshot()
              .withIndex(expectedSnapshot.getIndex())
              .withTerm(expectedSnapshot.getTerm());
        });
  }

  @Test
  public void shouldNotTakeNewSnapshot() {
    // given
    ControllableExporter.updatePosition(false);
    removeExporters();
    restartCluster();
    publishMessages();

    final var leaderId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var leaderAdminService =
        clusteringRule.getBroker(leaderId).getBrokerContext().getBrokerAdminService();
    final var expectedProcessedPosition =
        leaderAdminService.getPartitionStatus().get(1).getProcessedPosition();

    // expect
    awaitUntilAsserted(
        (broker) -> {
          triggerSnapshotRoutine();
          assertThat(broker)
              .havingSnapshot()
              .withProcessedPosition(expectedProcessedPosition)
              .withExportedPosition(Long.MAX_VALUE);
        });

    final Map<Integer, SnapshotId> snapshotsByBroker =
        clusteringRule.getTopologyFromClient().getBrokers().stream()
            .collect(Collectors.toMap(BrokerInfo::getNodeId, this::getSnapshot));

    // when
    configureExporters();
    restartCluster();
    triggerSnapshotRoutine();

    // then
    awaitUntilAsserted(
        (broker) -> {
          final SnapshotId expectedSnapshot =
              snapshotsByBroker.get(broker.getConfig().getCluster().getNodeId());
          assertThat(broker).havingSnapshot().isEqualTo(expectedSnapshot);
        });
  }

  private void awaitUntilAsserted(Consumer<Broker> consumer) {
    Awaitility.await()
        .pollInterval(Duration.ofSeconds(1))
        .timeout(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              Assertions.assertThat(clusteringRule.getBrokers()).allSatisfy(consumer);
            });
  }

  private void configureBroker(final BrokerCfg brokerCfg) {
    brokerCfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(4));

    final DataCfg data = brokerCfg.getData();
    data.setLogSegmentSize(DataSize.ofKilobytes(4));
    data.setLogIndexDensity(5);
    data.setSnapshotPeriod(SNAPSHOT_INTERVAL);

    configureExporter(brokerCfg);
  }

  private void configureExporters() {
    clusteringRule.getBrokers().stream().map(Broker::getConfig).forEach(this::configureExporter);
  }

  private void configureExporter(final BrokerCfg brokerConfig) {
    final ExporterCfg exporterConfig = new ExporterCfg();
    exporterConfig.setClassName(ControllableExporter.class.getName());
    brokerConfig.setExporters(Collections.singletonMap("snapshot-test-exporter", exporterConfig));
  }

  private void removeExporters() {
    clusteringRule.getBrokers().forEach(this::removeExporter);
  }

  private void removeExporter(final Broker broker) {
    final BrokerCfg brokerConfig = broker.getConfig();
    brokerConfig.setExporters(Collections.emptyMap());
  }

  private void restartCluster() {
    clusteringRule.restartCluster();
  }

  private void triggerSnapshotRoutine() {
    snapshotTrigger.accept(clusteringRule);
  }

  private SnapshotId getSnapshot(final BrokerInfo brokerInfo) {
    return clusteringRule.getSnapshot(brokerInfo.getNodeId()).get();
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

  private BrokerAssert assertThat(Broker broker) {
    return new BrokerAssert(broker, clusteringRule);
  }

  public static class ControllableExporter implements Exporter {
    static volatile boolean shouldExport = true;
    static volatile long lastUpdatedPosition = -1;

    static final AtomicLong EXPORTED_RECORDS = new AtomicLong(0);
    static final AtomicReference<Predicate<RecordType>> RECORD_TYPE_FILTER =
        new AtomicReference<>(r -> true);
    static final AtomicReference<Predicate<ValueType>> VALUE_TYPE_FILTER =
        new AtomicReference<>(r -> true);

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
              return RECORD_TYPE_FILTER.get().test(recordType);
            }

            @Override
            public boolean acceptValue(final ValueType valueType) {
              return VALUE_TYPE_FILTER.get().test(valueType);
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
        lastUpdatedPosition = record.getPosition();
        controller.updateLastExportedRecordPosition(lastUpdatedPosition);
      }

      EXPORTED_RECORDS.incrementAndGet();
    }
  }

  private static class BrokerAssert extends AbstractAssert<BrokerAssert, Broker> {

    private final ClusteringRule rule;

    protected BrokerAssert(Broker actual, ClusteringRule rule) {
      super(actual, BrokerAssert.class);
      this.rule = rule;
    }

    public SnapshotAssert havingSnapshot() {
      final var snapshot = rule.getSnapshot(actual);
      Assertions.assertThat(snapshot.isPresent())
          .withFailMessage("No snapshot exists for broker <%s>", actual)
          .isTrue();
      return new SnapshotAssert(snapshot.get());
    }
  }

  private static class SnapshotAssert extends AbstractAssert<SnapshotAssert, SnapshotId> {

    protected SnapshotAssert(SnapshotId actual) {
      super(actual, SnapshotAssert.class);
    }

    public SnapshotAssert withIndex(long expected) {
      Assertions.assertThat(actual.getIndex())
          .withFailMessage(
              "Expecting snapshot index <%s> but was <%s>", expected, actual.getIndex())
          .isEqualTo(expected);
      return myself;
    }

    public SnapshotAssert withTerm(long expected) {
      Assertions.assertThat(actual.getTerm())
          .withFailMessage("Expecting snapshot term <%s> but was <%s>", expected, actual.getTerm())
          .isEqualTo(expected);
      return myself;
    }

    public SnapshotAssert withProcessedPosition(long expected) {
      Assertions.assertThat(actual.getProcessedPosition())
          .withFailMessage(
              "Expecting snapshot processed position <%s> but was <%s>",
              expected, actual.getProcessedPosition())
          .isEqualTo(expected);
      return myself;
    }

    public SnapshotAssert withExportedPosition(long expected) {
      Assertions.assertThat(actual.getExportedPosition())
          .withFailMessage(
              "Expecting snapshot exported position <%s> but was <%s>",
              expected, actual.getExportedPosition())
          .isEqualTo(expected);
      return myself;
    }

    @Override
    public SnapshotAssert isEqualTo(Object expected) {
      return super.isEqualTo(expected);
    }
  }
}
