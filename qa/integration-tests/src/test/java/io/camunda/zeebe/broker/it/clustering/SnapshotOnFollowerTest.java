/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;

public class SnapshotOnFollowerTest {

  @Rule
  public final ClusteringRule clusteringRule = new ClusteringRule(1, 2, 2, this::configureBroker);

  @After
  public void cleanUp() {
    ControllableExporter.updatePosition(true);
    ControllableExporter.EXPORTED_RECORDS.set(0);
    ControllableExporter.RECORD_TYPE_FILTER.set(r -> true);
    ControllableExporter.VALUE_TYPE_FILTER.set(r -> true);
  }

  @Test
  public void shouldIncludeExportedPositionInSnapshotOnFollower() {
    // given
    ControllableExporter.updatePosition(true);
    final var leader = clusteringRule.getLeaderForPartition(1);
    final var follower = clusteringRule.getOtherBrokerObjects(leader.getNodeId()).get(0);

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
              clusteringRule.takeSnapshot(follower);
              assertThatSnapshotContainsExporterPosition(follower);
            });
  }

  private void assertThatSnapshotContainsExporterPosition(final Broker follower) {
    final var exportedPosition = ControllableExporter.lastUpdatedPosition;
    final var snapshotAtFollower = clusteringRule.getSnapshot(follower).orElseThrow();
    assertThat(snapshotAtFollower.getExportedPosition()).isEqualTo(exportedPosition);
  }

  private void configureBroker(final BrokerCfg brokerCfg) {
    final DataCfg data = brokerCfg.getData();
    data.setLogIndexDensity(5);

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
