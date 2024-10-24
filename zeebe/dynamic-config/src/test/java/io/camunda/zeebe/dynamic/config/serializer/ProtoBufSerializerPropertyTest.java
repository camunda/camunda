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
import io.camunda.zeebe.dynamic.config.util.ClusterTopologyDomain;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.domains.Domain;
import net.jqwik.api.domains.DomainContext;

final class ProtoBufSerializerPropertyTest {
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
        .describedAs("Decoded clusterTopology must be equal to initial one")
        .usingRecursiveComparison()
        // The type of generated `activePartitions` is `LinkedHashSet`, which AssertJ treats as an
        // ordered collection. We are not interested in the order of the partitions, so we ignore
        // it here.
        .ignoringCollectionOrderInFields(
            "routingState.value.requestHandling.additionalActivePartitions")
        .ignoringCollectionOrderInFields("routingState.value.requestHandling.inactivePartitions")
        .isEqualTo(clusterConfiguration);
  }
}
