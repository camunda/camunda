/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossipState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.util.ClusterTopologyDomain;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.domains.Domain;
import net.jqwik.api.domains.DomainContext;

final class ProtoBufSerializerPropertyTest {

  private static final String COLLECTION_EQUALITY_HINT =
      """
      Decoded configuration must be equal to the initial one.
      If this fails even though both objects look the same, make sure to take defensive copies of collections in the config and its nested types.
      jqwik tends to generate sorted sets and maps which are not equal to the unsorted collections created on deserialization.""";

  @Property(tries = 100)
  @Domain(ClusterTopologyDomain.class)
  @Domain(DomainContext.Global.class)
  void shouldEncodeAndDecode(@ForAll final ClusterConfiguration clusterConfiguration) {
    // given
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

  @Property(tries = 100)
  @Domain(ClusterTopologyDomain.class)
  @Domain(DomainContext.Global.class)
  void shouldEncodeAndDecodeCurrentClusterConfiguration(
      @ForAll final CurrentClusterConfiguration configuration) {
    // given
    final var protoBufSerializer = new ProtoBufSerializer();

    // when
    final var decoded =
        protoBufSerializer.decodeCurrentClusterConfiguration(
            protoBufSerializer.encodeCurrentClusterConfiguration(configuration));

    // then
    assertThat(decoded).describedAs(COLLECTION_EQUALITY_HINT).isEqualTo(configuration);
  }
}
