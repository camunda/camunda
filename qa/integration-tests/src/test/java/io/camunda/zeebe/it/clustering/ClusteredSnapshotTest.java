/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;
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
    Awaitility.await()
        .pollInterval(Duration.ofSeconds(1))
        .timeout(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              snapshotTrigger.accept(clusteringRule);
              assertThatSnapshotExists();
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
    assertThat(clusteringRule.getSnapshot(followerId)).isPresent();
  }

  @Test
  public void shouldIncludeExportedPositionInSnapshot() {
    // given
    ControllableExporter.updatePosition(true);

    publishMessages();
    ControllableExporter.updatePosition(false);
    publishMessages();

    // when - then
    Awaitility.await()
        .pollInterval(Duration.ofSeconds(1))
        .timeout(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              snapshotTrigger.accept(clusteringRule);
              assertThatSnapshotContainsExporterPosition();
            });
  }

  private void assertThatSnapshotContainsExporterPosition() {
    clusteringRule
        .getBrokers()
        .forEach(
            broker -> {
              final var exportedPosition = ControllableExporter.lastUpdatedPosition;
              final var snapshotAtFollower = clusteringRule.getSnapshot(broker).orElseThrow();
              assertThat(snapshotAtFollower.getExportedPosition()).isEqualTo(exportedPosition);
            });
  }

  private void assertThatSnapshotExists() {
    clusteringRule
        .getBrokers()
        .forEach(broker -> assertThat(clusteringRule.getSnapshot(broker)).isNotEmpty());
  }

  private void configureBroker(final BrokerCfg brokerCfg) {
    brokerCfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(4));

    final DataCfg data = brokerCfg.getData();
    data.setLogSegmentSize(DataSize.ofKilobytes(4));
    data.setLogIndexDensity(5);
    data.setSnapshotPeriod(SNAPSHOT_INTERVAL);

    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setClassName(ControllableExporter.class.getName());
    brokerCfg.setExporters(Collections.singletonMap("snapshot-test-exporter", exporterCfg));
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
}
