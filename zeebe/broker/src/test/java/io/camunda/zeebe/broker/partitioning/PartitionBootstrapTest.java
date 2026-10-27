/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl.PartitionAlreadyExistsException;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class PartitionBootstrapTest {

  @Rule
  public final EmbeddedBrokerRule brokerRule =
      new EmbeddedBrokerRule(
          brokerCfg -> {
            final var clusterCfg = brokerCfg.getCluster();
            clusterCfg.setClusterSize(1);
            clusterCfg.setNodeId(0);
            clusterCfg.setPartitionsCount(1); // Start with 1 partition
            clusterCfg.setReplicationFactor(1);
          });

  private PartitionManagerImpl partitionManager;

  @Before
  public void setUp() {
    partitionManager =
        (PartitionManagerImpl) brokerRule.getBroker().getBrokerContext().getPartitionManager();
  }

  @Test
  public void shouldBootstrapNewPartitionSuccessfully() {
    // given
    final var partitionId = 2; // Partition 1 already exists from startup
    final var priority = 1;
    final var config = DynamicPartitionConfig.init();

    // when
    final var result = partitionManager.bootstrap(partitionId, priority, config, false);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(10));
    assertThat(partitionManager.getRaftPartition(partitionId)).isNotNull();
    assertThat(partitionManager.getRaftPartitions()).hasSize(2); // Original 1 + new 1
  }

  @Test
  public void shouldBootstrapNewPartitionFromSnapshotSuccessfully() {
    // given
    final var partitionId = 2;
    final var priority = 1;
    final var config = DynamicPartitionConfig.init();
    partitionManager.initiateScaleUp(2);

    // when
    final var result = partitionManager.bootstrap(partitionId, priority, config, true);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(10));
    assertThat(partitionManager.getRaftPartition(partitionId)).isNotNull();
    assertThat(partitionManager.getRaftPartitions()).hasSize(2);
    assertThat(partitionManager.getZeebePartitions()).hasSize(2);
  }

  @Test
  public void bootstrapNewPartitionFromSnapshotIsIdempotent() {
    // given
    final var partitionId = 2;
    final var priority = 1;
    final var config = DynamicPartitionConfig.init();
    partitionManager.initiateScaleUp(3);
    partitionManager.bootstrap(partitionId, priority, config, true).join();

    // When the operation retried after broker restart
    brokerRule.restartBroker();
    partitionManager =
        (PartitionManagerImpl) brokerRule.getBroker().getBrokerContext().getPartitionManager();

    final var result = partitionManager.bootstrap(partitionId, priority, config, true);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(10));
    assertThat(partitionManager.getRaftPartition(partitionId)).isNotNull();
    assertThat(partitionManager.getRaftPartitions()).hasSize(2);
    assertThat(partitionManager.getZeebePartitions()).hasSize(2);
  }

  @Test
  public void shouldFailWhenBootstrappingExistingPartition() {
    // given
    final var partitionId = 1; // This partition already exists from startup
    final var priority = 1;
    final var config = DynamicPartitionConfig.init();

    // when/then
    assertThatThrownBy(
            () -> partitionManager.bootstrap(partitionId, priority, config, false).join())
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(PartitionAlreadyExistsException.class);
  }

  @Test
  public void shouldBootstrapWithCustomPriority() {
    // given
    final var partitionId = 2;
    final var customPriority = 5;
    final var config = DynamicPartitionConfig.init();

    // when
    final var result = partitionManager.bootstrap(partitionId, customPriority, config, false);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(10));

    final var raftPartition = partitionManager.getRaftPartition(partitionId);
    assertThat(raftPartition).isNotNull();
    assertThat(
            raftPartition.partitionMetadata().getPriority(raftPartition.getServer().getMemberId()))
        .isEqualTo(customPriority);
  }

  @Test
  public void shouldBootstrapContiguousPartitionsSequentially() {
    // given
    final var config = DynamicPartitionConfig.init();

    // when - bootstrap partitions 2 and 3 sequentially (contiguous with existing partition 1)
    final var result2 = partitionManager.bootstrap(2, 1, config, false);
    assertThat(result2).succeedsWithin(Duration.ofSeconds(10));

    final var result3 = partitionManager.bootstrap(3, 1, config, false);
    assertThat(result3).succeedsWithin(Duration.ofSeconds(10));

    // then
    assertThat(partitionManager.getRaftPartitions()).hasSize(3); // Original 1 + 2 new ones
    assertThat(partitionManager.getRaftPartition(2)).isNotNull();
    assertThat(partitionManager.getRaftPartition(3)).isNotNull();
  }
}
