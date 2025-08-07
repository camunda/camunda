/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.util.unit.DataSize;

@RunWith(Parameterized.class)
public final class ClusteredDataDeletionTest {

  private static final Duration SNAPSHOT_PERIOD = Duration.ofMinutes(1);
  private static final int SEGMENT_COUNT = 10;

  @Rule public final ClusteringRule clusteringRule;

  public ClusteredDataDeletionTest(final Consumer<BrokerCfg> configurator, final String name) {
    clusteringRule = new ClusteringRule(1, 3, 3, configurator);
  }

  @Parameters(name = "{index}: {1}")
  public static Object[][] configurators() {
    return new Object[][] {
      new Object[] {
        (Consumer<BrokerCfg>) ClusteredDataDeletionTest::configureNoExporters, "no-exporter"
      },
      new Object[] {
        (Consumer<BrokerCfg>) ClusteredDataDeletionTest::configureCustomExporter,
        "updating-exporter"
      }
    };
  }

  private static void configureNoExporters(final BrokerCfg brokerCfg) {
    final DataCfg data = brokerCfg.getData();
    data.setSnapshotPeriod(SNAPSHOT_PERIOD);
    data.setLogSegmentSize(DataSize.ofKilobytes(32));
    data.setLogIndexDensity(50);
    brokerCfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(32));
    brokerCfg.getExperimental().getFeatures().setEnableIdentitySetup(false);

    brokerCfg.setExporters(Collections.emptyMap());
  }

  private static void configureCustomExporter(final BrokerCfg brokerCfg) {
    final DataCfg data = brokerCfg.getData();
    data.setSnapshotPeriod(SNAPSHOT_PERIOD);
    data.setLogSegmentSize(DataSize.ofKilobytes(32));
    data.setLogIndexDensity(50);
    brokerCfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(32));
    brokerCfg.getExperimental().getFeatures().setEnableIdentitySetup(false);

    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setClassName(TestExporter.class.getName());

    // overwrites RecordingExporter on purpose because since it doesn't update its position
    // we wouldn't be able to delete data
    brokerCfg.setExporters(Collections.singletonMap("data-delete-test-exporter", exporterCfg));
  }

  @Test
  public void shouldDeleteDataOnBrokers() {
    // given
    final var brokers = clusteringRule.getBrokers();
    clusteringRule.runUntilSegmentsFilled(brokers, SEGMENT_COUNT, this::writeRecord);

    // when
    final var segmentCountsBeforeSnapshot = getSegmentCountByNodeId(brokers);
    clusteringRule.triggerAndWaitForSnapshots();

    // then
    await()
        .untilAsserted(
            () ->
                assertThat(getSegmentCountByNodeId(brokers))
                    .describedAs("Expected less segments after a snapshot is taken")
                    .allSatisfy(
                        (nodeId, segmentCount) ->
                            assertThat(segmentCount)
                                .isLessThan(segmentCountsBeforeSnapshot.get(nodeId))));
  }

  private Map<Integer, Integer> getSegmentCountByNodeId(final Collection<Broker> brokers) {
    return brokers.stream()
        .collect(
            Collectors.toMap(
                follower -> follower.getConfig().getCluster().getNodeId(),
                clusteringRule::getSegmentsCount));
  }

  private void writeRecord() {
    clusteringRule
        .getClient()
        .newPublishMessageCommand()
        .messageName("msg")
        .correlationKey("key")
        .send()
        .join();
  }

  public static class TestExporter implements Exporter {
    static final List<Record<?>> RECORDS = new CopyOnWriteArrayList<>();
    private Controller controller;

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
    }

    @Override
    public void export(final Record<?> record) {
      RECORDS.add(record);
      controller.updateLastExportedRecordPosition(record.getPosition());
    }
  }
}
