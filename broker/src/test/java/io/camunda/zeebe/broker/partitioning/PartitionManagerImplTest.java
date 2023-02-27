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
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.raft.partition.RaftPartitionGroupConfig;
import io.atomix.raft.storage.log.DelayedFlusher;
import io.atomix.raft.storage.log.RaftLogFlusher.DirectFlusher;
import io.atomix.raft.storage.log.RaftLogFlusher.NoopFlusher;
import io.atomix.utils.concurrent.Scheduled;
import io.atomix.utils.concurrent.ThreadContext;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.RaftCfg.FlushConfig;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.util.Environment;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class PartitionManagerImplTest {
  private @TempDir Path tempDir;
  private Environment environment;

  @Mock private ClusterServices mockClusterServices;
  @Mock private ClusterMembershipService mockMembershipService;

  @BeforeEach
  public void setUp() {
    environment = new Environment();

    when(mockClusterServices.getMembershipService()).thenReturn(mockMembershipService);
  }

  @Test
  void shouldUseDelayedFlushStrategy() {
    // given
    final var brokerConfig = newConfig();
    brokerConfig.getCluster().getRaft().setFlush(new FlushConfig(true, Duration.ofSeconds(5)));

    // when
    final var partitionManager =
        new PartitionManagerImpl(
            mock(ActorSchedulingService.class),
            brokerConfig,
            new BrokerInfo(1, "dummy"),
            mockClusterServices,
            mock(BrokerHealthCheckService.class),
            null,
            new ArrayList<>(),
            null,
            mock(ExporterRepository.class),
            null);

    // then
    final var config = getPartitionGroupConfig(partitionManager);
    assertThat(config.getStorageConfig().flusherFactory().createFlusher(NoopContext::new))
        .isInstanceOf(DelayedFlusher.class)
        .asInstanceOf(InstanceOfAssertFactories.type(DelayedFlusher.class))
        .hasFieldOrPropertyWithValue("delayTime", Duration.ofSeconds(5));
  }

  @Test
  void shouldUseDirectFlushStrategy() {
    // given
    final var brokerConfig = newConfig();
    brokerConfig.getCluster().getRaft().setFlush(new FlushConfig(true, Duration.ZERO));

    // when
    final var partitionManager =
        new PartitionManagerImpl(
            mock(ActorSchedulingService.class),
            brokerConfig,
            new BrokerInfo(1, "dummy"),
            mockClusterServices,
            mock(BrokerHealthCheckService.class),
            null,
            new ArrayList<>(),
            null,
            mock(ExporterRepository.class),
            null);

    // then
    final var config = getPartitionGroupConfig(partitionManager);
    assertThat(config.getStorageConfig().flusherFactory().createFlusher(() -> null))
        .isInstanceOf(DirectFlusher.class);
  }

  @Test
  void shouldUseNoOpFlushStrategy() {
    // given
    final var brokerConfig = newConfig();
    brokerConfig.getCluster().getRaft().setFlush(new FlushConfig(false, Duration.ofSeconds(5)));

    // when
    final var partitionManager =
        new PartitionManagerImpl(
            mock(ActorSchedulingService.class),
            brokerConfig,
            new BrokerInfo(1, "dummy"),
            mockClusterServices,
            mock(BrokerHealthCheckService.class),
            null,
            new ArrayList<>(),
            null,
            mock(ExporterRepository.class),
            null);

    // then
    final var config = getPartitionGroupConfig(partitionManager);
    assertThat(config.getStorageConfig().flusherFactory().createFlusher(() -> null))
        .isInstanceOf(NoopFlusher.class);
  }

  @Test
  void shouldDisableExplicitFlush() {
    // given
    final var brokerConfig = newConfig();
    brokerConfig.getExperimental().setDisableExplicitRaftFlush(true);
    brokerConfig.getCluster().getRaft().setFlush(new FlushConfig(true, Duration.ofSeconds(5)));

    // when
    final var partitionManager =
        new PartitionManagerImpl(
            mock(ActorSchedulingService.class),
            brokerConfig,
            new BrokerInfo(1, "dummy"),
            mockClusterServices,
            mock(BrokerHealthCheckService.class),
            null,
            new ArrayList<>(),
            null,
            mock(ExporterRepository.class),
            null);

    // then
    final var config = getPartitionGroupConfig(partitionManager);
    assertThat(config.getStorageConfig().flusherFactory().createFlusher(() -> null))
        .isInstanceOf(NoopFlusher.class);
  }

  private RaftPartitionGroupConfig getPartitionGroupConfig(
      final PartitionManager partitionManager) {
    return (RaftPartitionGroupConfig) partitionManager.getPartitionGroup().config();
  }

  private BrokerCfg newConfig() {
    final var config = new BrokerCfg();
    config.init(tempDir.toAbsolutePath().toString(), environment);

    return config;
  }

  private static final class NoopContext implements ThreadContext {
    @Override
    public Scheduled schedule(
        final Duration initialDelay, final Duration interval, final Runnable callback) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void execute(final Runnable command) {
      throw new UnsupportedOperationException();
    }
  }
}
