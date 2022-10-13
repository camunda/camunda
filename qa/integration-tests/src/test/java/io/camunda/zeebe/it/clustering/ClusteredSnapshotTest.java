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
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

@RunWith(Parameterized.class)
public class ClusteredSnapshotTest {

  public static final Logger LOG = LoggerFactory.getLogger("ClusteredSnapshotTest");
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
        (Consumer<ClusteringRule>)
            (rule) -> {
              LOG.info("Triggerring snapshots using admin api");
              rule.triggerAndWaitForSnapshots();
            },
        "explicit trigger snapshot"
      },
      new Object[] {
        (Consumer<ClusteringRule>)
            (rule) -> {
              LOG.info("Increasing clock by snapshot interval {}", SNAPSHOT_INTERVAL);
              rule.getClock().addTime(SNAPSHOT_INTERVAL);
            },
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

  private void awaitUntilAsserted(final Consumer<Broker> consumer) {
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

  private void configureExporter(final BrokerCfg brokerConfig) {
    final ExporterCfg exporterConfig = new ExporterCfg();
    exporterConfig.setClassName(ControllableExporter.class.getName());
    brokerConfig.setExporters(Collections.singletonMap("snapshot-test-exporter", exporterConfig));
  }

  private void triggerSnapshotRoutine() {
    snapshotTrigger.accept(clusteringRule);
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
