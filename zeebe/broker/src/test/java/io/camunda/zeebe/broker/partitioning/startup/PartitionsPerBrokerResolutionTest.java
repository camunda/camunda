/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PartitionsPerBrokerResolutionTest {

  private static final MemberId MEMBER_ID = MemberId.from("1");

  /**
   * Tests for {@link ZeebePartitionFactory#getPartitionsPerBroker}, which determines how many
   * partitions this broker owns — used to size the shared RocksDB cache.
   */
  @Nested
  class GetPartitionsPerBroker {

    @Test
    void shouldReturnActivePartitionCountWhenBrokerHasActivePartitions() {
      // given
      final var configService = mock(ClusterConfigurationService.class);
      final var partition1 = mock(PartitionMetadata.class);
      final var partition2 = mock(PartitionMetadata.class);
      when(configService.getMemberPartitions(MEMBER_ID))
          .thenReturn(List.of(partition1, partition2));

      // when
      final int result =
          ZeebePartitionFactory.getPartitionsPerBroker(configService, MEMBER_ID, null);

      // then
      assertThat(result).isEqualTo(2);
    }

    @Test
    void shouldFallBackToJoiningCountWhenBrokerIsJoiningPartitions() {
      // given — broker not yet in the partition distribution (partition in JOINING state),
      // so getMemberPartitions returns empty; only getJoiningMemberPartitionCount has the answer
      final var configService = mock(ClusterConfigurationService.class);
      when(configService.getMemberPartitions(MEMBER_ID)).thenReturn(List.of());
      when(configService.getJoiningMemberPartitionCount(MEMBER_ID)).thenReturn(1);

      // when
      final int result =
          ZeebePartitionFactory.getPartitionsPerBroker(configService, MEMBER_ID, null);

      // then
      assertThat(result).isEqualTo(1);
    }

    @Test
    void shouldEstimateFromConfigWhenBrokerHasNeitherActiveNorJoiningPartitions() {
      // given — broker is joining its first partition but the cluster config hasn't been updated
      // yet (e.g. join called before the dynamic config reflects the JOINING state).
      // 3 partitions × RF 2 across 3 brokers → ceil(6/3) = 2 partitions per broker.
      final var configService = mock(ClusterConfigurationService.class);
      when(configService.getMemberPartitions(MEMBER_ID)).thenReturn(List.of());
      when(configService.getJoiningMemberPartitionCount(MEMBER_ID)).thenReturn(0);

      final var brokerCfg = new BrokerCfg();
      brokerCfg.getCluster().setPartitionsCount(3);
      brokerCfg.getCluster().setReplicationFactor(2);
      brokerCfg.getCluster().setClusterSize(3);

      // when
      final int result =
          ZeebePartitionFactory.getPartitionsPerBroker(configService, MEMBER_ID, brokerCfg);

      // then — ceil(3 * 2 / 3) = 2
      assertThat(result).isEqualTo(2);
    }
  }

  /**
   * Tests for the {@link ClusterConfigurationService#getJoiningMemberPartitionCount} default
   * method, which counts partitions in JOINING state from the live cluster configuration.
   */
  @Nested
  class GetJoiningMemberPartitionCount {

    private ClusterConfigurationService serviceWithConfig(final ClusterConfiguration config) {
      final var service = mock(ClusterConfigurationService.class, Mockito.CALLS_REAL_METHODS);
      when(service.getCurrentClusterConfiguration()).thenReturn(config);
      return service;
    }

    @Test
    void shouldCountJoiningPartitions() {
      // given
      final var config =
          ClusterConfiguration.init()
              .addMember(
                  MEMBER_ID,
                  MemberState.initializeAsActive(
                      Map.of(
                          1, PartitionState.joining(1, DynamicPartitionConfig.init()),
                          2, PartitionState.joining(1, DynamicPartitionConfig.init()))));
      final var service = serviceWithConfig(config);

      // when
      final int result = service.getJoiningMemberPartitionCount(MEMBER_ID);

      // then
      assertThat(result).isEqualTo(2);
    }

    @Test
    void shouldIgnoreNonJoiningPartitions() {
      // given — member has one ACTIVE and one LEAVING partition; none are JOINING
      final var config =
          ClusterConfiguration.init()
              .addMember(
                  MEMBER_ID,
                  MemberState.initializeAsActive(
                      Map.of(
                          1, PartitionState.active(1, DynamicPartitionConfig.init()),
                          2, PartitionState.active(1, DynamicPartitionConfig.init()).toLeaving())));
      final var service = serviceWithConfig(config);

      // when
      final int result = service.getJoiningMemberPartitionCount(MEMBER_ID);

      // then
      assertThat(result).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroWhenMemberNotInConfiguration() {
      // given
      final var config = ClusterConfiguration.init();
      final var service = serviceWithConfig(config);

      // when
      final int result = service.getJoiningMemberPartitionCount(MEMBER_ID);

      // then
      assertThat(result).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroWhenConfigurationIsNull() {
      // given
      final var service = serviceWithConfig(null);

      // when
      final int result = service.getJoiningMemberPartitionCount(MEMBER_ID);

      // then
      assertThat(result).isEqualTo(0);
    }
  }
}
