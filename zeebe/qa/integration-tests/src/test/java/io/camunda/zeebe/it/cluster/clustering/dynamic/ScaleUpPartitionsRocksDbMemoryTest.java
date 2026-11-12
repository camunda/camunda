/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.dynamic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.FeignException;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.management.cluster.RequestHandlingAllPartitions;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.util.unit.DataSize;

@ZeebeIntegration
@Timeout(value = 5, unit = TimeUnit.MINUTES)
final class ScaleUpPartitionsRocksDbMemoryTest {

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(1)
          .withPartitionsCount(1)
          .withReplicationFactor(1)
          .withBrokerConfig(
              b ->
                  b.withUnifiedConfig(
                      cfg -> {
                        // leaves exactly the 32 MiB minimum for each of 2 partitions
                        final var rocksDb = cfg.getData().getPrimaryStorage().getRocksDb();
                        rocksDb.setMemoryAllocationStrategy(MemoryAllocationStrategy.BROKER);
                        rocksDb.setMemoryLimit(DataSize.ofMegabytes(64));
                      }))
          .build();

  private ClusterActuator clusterActuator;

  @BeforeEach
  void setUp() {
    clusterActuator = ClusterActuator.of(cluster.availableGateway());
  }

  @Test
  void shouldRejectScaleUpThatWouldPreventBrokerRestart() {
    // when
    assertThatThrownBy(() -> scaleToPartitions(3))
        // then
        .asInstanceOf(InstanceOfAssertFactories.type(FeignException.class))
        .satisfies(
            e -> {
              assertThat(e.status()).isEqualTo(409);
              assertThat(e.contentUTF8()).contains("broker 0").contains("3 partitions");
            });
    assertThat(clusterActuator.getTopology().getPendingChange()).isNull();
  }

  @Test
  void shouldRestartBrokerAfterScaleUpWithinRocksDbMemory() {
    // given
    scaleToPartitions(2);
    awaitPartitionCount(2);

    // when
    cluster.brokers().values().forEach(broker -> broker.stop().start());

    // then
    cluster.await(TestHealthProbe.READY);
    cluster.awaitHealthyTopology();
  }

  private PlannedOperationsResponse scaleToPartitions(final int partitionCount) {
    return clusterActuator.patchCluster(
        new ClusterConfigPatchRequest()
            .partitions(
                new ClusterConfigPatchRequestPartitions()
                    .count(partitionCount)
                    .replicationFactor(1)),
        false,
        false);
  }

  private void awaitPartitionCount(final int partitionCount) {
    Awaitility.await("until scaling to %d partitions is done".formatted(partitionCount))
        .atMost(Duration.ofMinutes(2))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var requestHandling =
                  clusterActuator.getTopology().getRouting().getRequestHandling();
              assertThat(requestHandling)
                  .asInstanceOf(InstanceOfAssertFactories.type(RequestHandlingAllPartitions.class))
                  .extracting(RequestHandlingAllPartitions::getPartitionCount)
                  .isEqualTo(partitionCount);
            });
  }
}
