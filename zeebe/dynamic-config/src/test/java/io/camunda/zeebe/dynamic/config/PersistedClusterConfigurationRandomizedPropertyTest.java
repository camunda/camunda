/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hegel.Generator;
import dev.hegel.HegelTest;
import dev.hegel.OptBoolean;
import dev.hegel.TestCase;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.util.ClusterTopologyDomain;
import java.io.IOException;
import java.nio.file.Files;

final class PersistedClusterConfigurationRandomizedPropertyTest {

  private static final Generator<ClusterConfiguration> CLUSTER_CONFIGURATIONS =
      ClusterTopologyDomain.clusterTopologies();

  @HegelTest(testCases = 100, derandomize = OptBoolean.FALSE)
  void shouldUpdatePersistedFile(final TestCase tc) throws IOException {
    // given
    final var initialTopology = tc.draw(CLUSTER_CONFIGURATIONS, "initialTopology");
    final var updatedTopology = tc.draw(CLUSTER_CONFIGURATIONS, "updatedTopology");
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
        .isEqualTo(updatedTopology);
  }
}
