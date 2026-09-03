/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert;
import io.camunda.zeebe.dynamic.config.PartitionStateAssert;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigurationUtilTest {

  private static final String GROUP_NAME = "test";
  private final DynamicPartitionConfig partitionConfig =
      new DynamicPartitionConfig(
          new ExportingConfig(
              ExportingState.EXPORTING,
              Map.of("expA", new ExporterState(1, ExporterState.State.ENABLED, Optional.empty()))));

  @Test
  void shouldGenerateTopologyFromPartitionDistribution() {
    // given
    final PartitionMetadata partitionOne =
        new PartitionMetadata(
            new PartitionId(GROUP_NAME, 1),
            Set.of(member(1), member(2), member(0)),
            Map.of(member(0), 1, member(1), 2, member(2), 3),
            3,
            member(2));
    final PartitionMetadata partitionTwo =
        new PartitionMetadata(
            new PartitionId(GROUP_NAME, 2),
            Set.of(member(1), member(2), member(0)),
            Map.of(member(2), 1, member(1), 2, member(0), 3),
            3,
            member(0));

    final var partitionDistribution = Set.of(partitionTwo, partitionOne);

    // when
    final var topology =
        ConfigurationUtil.getClusterConfigFrom(partitionDistribution, partitionConfig, "clusterId");

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(0, State.ACTIVE)
        .member(0)
        .hasPartitionSatisfying(
            1,
            partition -> {
              PartitionStateAssert.assertThat(partition)
                  .hasPriority(1)
                  .hasState(PartitionState.State.ACTIVE)
                  .hasConfig(partitionConfig);
            })
        .hasPartitionSatisfying(
            2,
            partition ->
                PartitionStateAssert.assertThat(partition)
                    .hasPriority(3)
                    .hasState(PartitionState.State.ACTIVE)
                    .hasConfig(partitionConfig));

    ClusterConfigurationAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(1, State.ACTIVE)
        .member(1)
        .hasPartitionSatisfying(
            1,
            partition ->
                PartitionStateAssert.assertThat(partition)
                    .hasPriority(2)
                    .hasState(PartitionState.State.ACTIVE)
                    .hasConfig(partitionConfig))
        .hasPartitionSatisfying(
            2,
            partition -> {
              PartitionStateAssert.assertThat(partition)
                  .hasPriority(2)
                  .hasState(PartitionState.State.ACTIVE)
                  .hasConfig(partitionConfig);
            });

    ClusterConfigurationAssert.assertThatClusterTopology(topology)
        .hasMemberWithState(2, State.ACTIVE)
        .member(2)
        .hasPartitionSatisfying(
            1,
            partition ->
                PartitionStateAssert.assertThat(partition)
                    .hasPriority(3)
                    .hasState(PartitionState.State.ACTIVE)
                    .hasConfig(partitionConfig))
        .hasPartitionSatisfying(
            2,
            partition ->
                PartitionStateAssert.assertThat(partition)
                    .hasPriority(1)
                    .hasState(PartitionState.State.ACTIVE)
                    .hasConfig(partitionConfig));
  }

  @Test
  void shouldGeneratePartitionDistributionFromTopology() {
    // given
    final PartitionMetadata partitionOne =
        new PartitionMetadata(
            new PartitionId(GROUP_NAME, 1),
            Set.of(member(1), member(2), member(0)),
            Map.of(member(0), 1, member(1), 2, member(2), 3),
            3,
            member(2));
    final PartitionMetadata partitionTwo =
        new PartitionMetadata(
            new PartitionId(GROUP_NAME, 2),
            Set.of(member(1), member(2), member(0)),
            Map.of(member(2), 1, member(1), 2, member(0), 3),
            3,
            member(0));

    final var expected = Set.of(partitionTwo, partitionOne);

    final var configuration =
        CurrentClusterConfiguration.init()
            .updateGlobalConfiguration(
                globalConfiguration ->
                    globalConfiguration
                        .addMember(member(0), BrokerState.initializeAsActive())
                        .addMember(member(1), BrokerState.initializeAsActive())
                        .addMember(member(2), BrokerState.initializeAsActive()))
            .initPartitionGroup(GROUP_NAME)
            .updatePartitionGroupConfig(
                GROUP_NAME,
                partitionGroupConfiguration ->
                    partitionGroupConfiguration
                        .addMember(
                            member(0),
                            BrokerPartitionState.initialize(
                                Map.of(
                                    1,
                                    PartitionState.active(1, partitionConfig),
                                    2,
                                    PartitionState.active(3, partitionConfig),
                                    // A joining member should not be included in the partition
                                    // distribution
                                    3,
                                    PartitionState.joining(4, partitionConfig))))
                        .addMember(
                            member(1),
                            BrokerPartitionState.initialize(
                                Map.of(
                                    1,
                                    PartitionState.active(2, partitionConfig),
                                    // A leaving member should be included in the partition
                                    // distribution
                                    2,
                                    PartitionState.active(2, partitionConfig).toLeaving())))
                        .addMember(
                            member(2),
                            BrokerPartitionState.initialize(
                                Map.of(
                                    1,
                                    PartitionState.active(3, partitionConfig),
                                    2,
                                    PartitionState.active(1, partitionConfig)))));

    // when
    final var partitionDistribution =
        ConfigurationUtil.getPartitionDistributionFrom(configuration, GROUP_NAME);

    // then
    assertThat(partitionDistribution).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void shouldGeneratePartitionDistributionFromTopologyWithMemberWithNoPartitions() {
    // given
    final PartitionMetadata partitionOne =
        new PartitionMetadata(
            new PartitionId(GROUP_NAME, 1),
            Set.of(member(1), member(0)),
            Map.of(member(0), 1, member(1), 2),
            2,
            member(1));

    final PartitionMetadata partitionTwo =
        new PartitionMetadata(
            new PartitionId(GROUP_NAME, 2),
            Set.of(member(1), member(0)),
            Map.of(member(1), 2, member(0), 3),
            3,
            member(0));

    final var expected = Set.of(partitionTwo, partitionOne);
    final var configuration =
        CurrentClusterConfiguration.init()
            .updateGlobalConfiguration(
                globalConfiguration ->
                    globalConfiguration
                        .addMember(member(0), BrokerState.initializeAsActive())
                        .addMember(member(1), BrokerState.initializeAsActive()))
            .initPartitionGroup(GROUP_NAME)
            .updatePartitionGroupConfig(
                GROUP_NAME,
                partitionGroupConfiguration ->
                    partitionGroupConfiguration
                        .addMember(
                            member(0),
                            BrokerPartitionState.initialize(
                                Map.of(
                                    1,
                                    PartitionState.active(1, partitionConfig),
                                    2,
                                    PartitionState.active(3, partitionConfig))))
                        .addMember(
                            member(1),
                            BrokerPartitionState.initialize(
                                Map.of(
                                    1,
                                    PartitionState.active(2, partitionConfig),
                                    2,
                                    PartitionState.active(2, partitionConfig)))));
    // when
    final var partitionDistribution =
        ConfigurationUtil.getPartitionDistributionFrom(configuration, GROUP_NAME);

    // then
    assertThat(partitionDistribution).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void shouldInitializeRoutingState() {
    // given
    final PartitionMetadata partitionOne =
        new PartitionMetadata(
            new PartitionId(GROUP_NAME, 1),
            Set.of(member(1), member(2), member(0)),
            Map.of(member(0), 1, member(1), 2, member(2), 3),
            3,
            member(2));
    final PartitionMetadata partitionTwo =
        new PartitionMetadata(
            new PartitionId(GROUP_NAME, 2),
            Set.of(member(1), member(2), member(0)),
            Map.of(member(2), 1, member(1), 2, member(0), 3),
            3,
            member(0));

    final var partitionDistribution = Set.of(partitionTwo, partitionOne);

    // when
    final var topology =
        ConfigurationUtil.getClusterConfigFrom(partitionDistribution, partitionConfig, "clusterId");

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(topology)
        .hasRoutingState()
        .routingState()
        .hasVersion(1)
        .hasActivatedPartitions(2)
        .correlatesMessagesToPartitions(2);
  }

  @Test
  void shouldIncludeRecoveringPartitionsInDistribution() {
    // given
    final PartitionMetadata partitionOne =
        new PartitionMetadata(
            new PartitionId(GROUP_NAME, 1), Set.of(member(0)), Map.of(member(0), 1), 1, member(0));

    final var topology =
        CurrentClusterConfiguration.init()
            .updateGlobalConfiguration(
                globalConfiguration ->
                    globalConfiguration.addMember(member(0), BrokerState.initializeAsActive()))
            .initPartitionGroup(GROUP_NAME)
            .updatePartitionGroupConfig(
                GROUP_NAME,
                partitionGroupConfiguration ->
                    partitionGroupConfiguration.addMember(
                        member(0),
                        BrokerPartitionState.initialize(
                            Map.of(1, PartitionState.active(1, partitionConfig).toRecovering()))));

    // when
    final var partitionDistribution =
        ConfigurationUtil.getPartitionDistributionFrom(topology, GROUP_NAME);

    // then
    assertThat(partitionDistribution).containsExactly(partitionOne);
  }

  @Test
  void shouldIncludeLearnerPartitionsInDistribution() {
    // given - a learner must be part of the distribution so that it starts its partition on boot
    // and recovers its raft state, otherwise a pending promotion could never complete
    final PartitionMetadata partitionOne =
        new PartitionMetadata(
            new PartitionId(GROUP_NAME, 1),
            Set.of(member(0), member(1)),
            Map.of(member(0), 2, member(1), 1),
            2,
            member(0));

    final ClusterConfiguration topology =
        ClusterConfiguration.init()
            .addMember(
                member(0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2, partitionConfig))))
            .addMember(
                member(1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.joining(1, partitionConfig).toLearner())));

    // when
    final var partitionDistribution =
        ConfigurationUtil.getPartitionDistributionFrom(topology, GROUP_NAME);

    // then
    assertThat(partitionDistribution).containsExactly(partitionOne);
  }

  @Test
  void shouldIncludeLearnerPartitionsInPerTenantDistribution() {
    // given - same as above, but through the per-partition-group distribution
    final ClusterConfiguration topology =
        ClusterConfiguration.init()
            .addMember(
                member(0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2, partitionConfig))))
            .addMember(
                member(1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.joining(1, partitionConfig).toLearner())));
    final var configuration = CurrentClusterConfiguration.fromLegacy(topology);

    // when
    final var distribution =
        ConfigurationUtil.getPartitionDistributionPerPhysicalTenant(configuration);

    // then
    final var defaultGroup = CurrentClusterConfiguration.DEFAULT_GROUP;
    assertThat(distribution).containsOnlyKeys(defaultGroup);
    assertThat(distribution.get(defaultGroup))
        .containsExactly(
            new PartitionMetadata(
                new PartitionId(defaultGroup, 1),
                Set.of(member(0), member(1)),
                Map.of(member(0), 2, member(1), 1),
                2,
                member(0)));
  }

  @Test
  void shouldGenerateCurrentClusterConfigurationSplitByGroup() {
    // given — two groups, "default" and "tenantA", each with their own partitions
    final PartitionMetadata defaultPartitionOne =
        new PartitionMetadata(
            new PartitionId("default", 1),
            Set.of(member(0), member(1)),
            Map.of(member(0), 2, member(1), 1),
            2,
            member(0));
    final PartitionMetadata tenantAPartitionOne =
        new PartitionMetadata(
            new PartitionId("tenantA", 1), Set.of(member(1)), Map.of(member(1), 1), 1, member(1));
    final var partitionDistribution = Set.of(defaultPartitionOne, tenantAPartitionOne);
    // member(2) is part of the cluster but replicates no partition in any group
    final var clusterMembers = Set.of(member(0), member(1), member(2));

    // when
    final var tenantConfigs =
        Map.of(
            "default", partitionConfig,
            "tenantA", partitionConfig);
    final var configuration =
        ConfigurationUtil.getCurrentClusterConfigurationFrom(
            clusterMembers, partitionDistribution, tenantConfigs, "clusterId");

    // then — every cluster member is ACTIVE in the global configuration, regardless of partitions
    assertThat(configuration.globalConfiguration().members().keySet())
        .containsExactlyInAnyOrder(member(0), member(1), member(2));
    assertThat(configuration.globalConfiguration().getMember(member(2)).state())
        .isEqualTo(BrokerState.State.ACTIVE);
    assertThat(configuration.globalConfiguration().clusterId()).contains("clusterId");

    // and — the partition groups are split by PartitionId.group()
    assertThat(configuration.partitionGroups()).containsOnlyKeys("default", "tenantA");

    final var defaultGroup = configuration.partitionGroup("default");
    assertThat(defaultGroup.members().keySet()).containsExactlyInAnyOrder(member(0), member(1));
    assertThat(defaultGroup.getMember(member(0)).partitions().get(1).priority()).isEqualTo(2);
    assertThat(defaultGroup.routingState()).isPresent();
    assertThat(defaultGroup.routingState().orElseThrow().requestHandling().activePartitions())
        .containsExactly(1);

    final var tenantAGroup = configuration.partitionGroup("tenantA");
    assertThat(tenantAGroup.members().keySet()).containsExactly(member(1));
    assertThat(tenantAGroup.getMember(member(1)).partitions().get(1).priority()).isEqualTo(1);
  }

  @Test
  void shouldGenerateClusterIdWhenNoneIsConfigured() {
    // given
    final PartitionMetadata partitionOne =
        new PartitionMetadata(
            new PartitionId(GROUP_NAME, 1), Set.of(member(0)), Map.of(member(0), 1), 1, member(0));

    // when
    final var configuration =
        ConfigurationUtil.getCurrentClusterConfigurationFrom(
            Set.of(member(0)), Set.of(partitionOne), Map.of(GROUP_NAME, partitionConfig), null);

    // then
    assertThat(configuration.globalConfiguration().clusterId()).isPresent().get().isNotEqualTo("");
  }

  @Test
  void shouldGenerateDistinctClusterIdPerConfiguration() {
    // given
    final PartitionMetadata partitionOne =
        new PartitionMetadata(
            new PartitionId(GROUP_NAME, 1), Set.of(member(0)), Map.of(member(0), 1), 1, member(0));

    // when
    final var first =
        ConfigurationUtil.getCurrentClusterConfigurationFrom(
            Set.of(member(0)), Set.of(partitionOne), Map.of(GROUP_NAME, partitionConfig), null);
    final var second =
        ConfigurationUtil.getCurrentClusterConfigurationFrom(
            Set.of(member(0)), Set.of(partitionOne), Map.of(GROUP_NAME, partitionConfig), null);

    // then
    assertThat(first.globalConfiguration().clusterId())
        .isNotEqualTo(second.globalConfiguration().clusterId());
  }

  private MemberId member(final int id) {
    return MemberId.from(String.valueOf(id));
  }
}
