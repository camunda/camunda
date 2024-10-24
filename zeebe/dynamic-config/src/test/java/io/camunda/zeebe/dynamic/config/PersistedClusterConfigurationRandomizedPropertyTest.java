/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.util.ClusterTopologyDomain;
import java.io.IOException;
import java.nio.file.Files;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.domains.Domain;
import net.jqwik.api.domains.DomainContext;

final class PersistedClusterConfigurationRandomizedPropertyTest {

  @Property(tries = 100)
  @Domain(ClusterTopologyDomain.class)
  @Domain(DomainContext.Global.class)
  void shouldUpdatePersistedFile(
      @ForAll final ClusterConfiguration initialTopology,
      @ForAll final ClusterConfiguration updatedTopology)
      throws IOException {
    // given
    final var tmp = Files.createTempDirectory("topology");
    final var topologyFile = tmp.resolve("topology.meta");
    final var serializer = new ProtoBufSerializer();
    final var persistedClusterTopology =
        PersistedClusterConfiguration.ofFile(topologyFile, serializer);

    // when
    persistedClusterTopology.update(initialTopology);
    persistedClusterTopology.update(updatedTopology);

    // then
    assertThat(updatedTopology).isEqualTo(persistedClusterTopology.getConfiguration());
    assertThat(PersistedClusterConfiguration.ofFile(topologyFile, serializer).getConfiguration())
        .usingRecursiveComparison()
        // The type of generated `activePartitions` is `LinkedHashSet`, which AssertJ treats as an
        // ordered collection. We are not interested in the order of the partitions, so we ignore
        // it here.
        .ignoringCollectionOrderInFields(
            "routingState.value.requestHandling.additionalActivePartitions")
        .ignoringCollectionOrderInFields("routingState.value.requestHandling.inactivePartitions")
        .isEqualTo(updatedTopology);
  }
}
