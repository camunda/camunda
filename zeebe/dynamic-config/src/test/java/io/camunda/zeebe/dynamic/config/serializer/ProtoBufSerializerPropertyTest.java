/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hegel.Generator;
import dev.hegel.HegelTest;
import dev.hegel.TestCase;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossipState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.util.ClusterTopologyDomain;

final class ProtoBufSerializerPropertyTest {

  private static final Generator<ClusterConfiguration> CLUSTER_CONFIGURATIONS =
      ClusterTopologyDomain.clusterTopologies();
  private static final Generator<CurrentClusterConfiguration> CURRENT_CLUSTER_CONFIGURATIONS =
      ClusterTopologyDomain.currentClusterConfigurations();

  private static final String COLLECTION_EQUALITY_HINT =
      """
      Decoded configuration must be equal to the initial one.
      If this fails even though both objects look the same, make sure to take defensive copies of collections in the config and its nested types.
      The generators build sorted sets and maps which are not equal to the unsorted collections created on deserialization.""";

  @HegelTest(testCases = 100)
  void shouldEncodeAndDecode(final TestCase tc) {
    // given
    final var clusterConfiguration = tc.draw(CLUSTER_CONFIGURATIONS, "clusterConfiguration");
    final ClusterConfigurationGossipState gossipState = new ClusterConfigurationGossipState();
    gossipState.setClusterConfiguration(clusterConfiguration);
    final var protoBufSerializer = new ProtoBufSerializer();

    // when
    final var decodedState = protoBufSerializer.decode(protoBufSerializer.encode(gossipState));

    // then
    assertThat(decodedState.getClusterConfiguration())
        .describedAs(COLLECTION_EQUALITY_HINT)
        .isEqualTo(clusterConfiguration);
  }

  @HegelTest(testCases = 100)
  void shouldEncodeAndDecodeCurrentClusterConfiguration(final TestCase tc) {
    // given
    final var configuration = tc.draw(CURRENT_CLUSTER_CONFIGURATIONS, "configuration");
    final var protoBufSerializer = new ProtoBufSerializer();

    // when
    final var decoded =
        protoBufSerializer.decodeCurrentClusterConfiguration(
            protoBufSerializer.encodeCurrentClusterConfiguration(configuration));

    // then
    assertThat(decoded).describedAs(COLLECTION_EQUALITY_HINT).isEqualTo(configuration);
  }
}
