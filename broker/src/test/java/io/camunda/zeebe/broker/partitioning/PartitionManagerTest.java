/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.atomix.raft.partition.RaftPartitionGroupConfig;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.util.Environment;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class PartitionManagerTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private Environment environment;

  @Before
  public void setUp() {
    environment = new Environment();
  }

  @Test
  public void shouldDisableExplicitFlush() {
    // given
    final var brokerConfig = newConfig();
    brokerConfig.getExperimental().setDisableExplicitRaftFlush(true);

    // when
    final var partitionManager =
        PartitionManagerFactory.fromBrokerConfiguration(
            brokerConfig,
            mock(ClusterServicesImpl.class),
            mock(FileBasedSnapshotStoreFactory.class));

    // then
    final var config = getPartitionGroupConfig(partitionManager);
    assertThat(config.getStorageConfig().shouldFlushExplicitly()).isFalse();
  }

  @Test
  public void shouldEnableExplicitFlush() {
    // given
    final var brokerConfig = newConfig();
    brokerConfig.getExperimental().setDisableExplicitRaftFlush(false);

    // when
    final var partitionManager =
        PartitionManagerFactory.fromBrokerConfiguration(
            brokerConfig,
            mock(ClusterServicesImpl.class),
            mock(FileBasedSnapshotStoreFactory.class));
    // then
    final var config = getPartitionGroupConfig(partitionManager);
    assertThat(config.getStorageConfig().shouldFlushExplicitly()).isTrue();
  }

  private RaftPartitionGroupConfig getPartitionGroupConfig(
      final PartitionManager partitionManager) {
    return (RaftPartitionGroupConfig) partitionManager.getPartitionGroup().config();
  }

  private BrokerCfg newConfig() {
    final var config = new BrokerCfg();
    config.init(temporaryFolder.getRoot().getAbsolutePath(), environment);

    return config;
  }
}
