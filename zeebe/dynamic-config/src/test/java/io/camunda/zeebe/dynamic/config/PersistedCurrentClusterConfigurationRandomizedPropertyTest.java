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
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.util.ClusterTopologyDomain;
import java.io.IOException;
import java.nio.file.Files;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.domains.Domain;
import net.jqwik.api.domains.DomainContext;

final class PersistedCurrentClusterConfigurationRandomizedPropertyTest {

  @Property(tries = 100)
  @Domain(ClusterTopologyDomain.class)
  @Domain(DomainContext.Global.class)
  void shouldUpdatePersistedFile(
      @ForAll final CurrentClusterConfiguration initialConfiguration,
      @ForAll final CurrentClusterConfiguration updatedConfiguration)
      throws IOException {
    // given
    final var tmp = Files.createTempDirectory("topology");
    final var configurationFile = tmp.resolve("config.meta");
    final var serializer = new ProtoBufSerializer();
    final var persisted =
        PersistedCurrentClusterConfiguration.ofFile(configurationFile, serializer);

    // when
    persisted.update(initialConfiguration);
    persisted.update(updatedConfiguration);

    // then
    assertThat(persisted.getConfiguration()).isEqualTo(updatedConfiguration);
    assertThat(
            PersistedCurrentClusterConfiguration.ofFile(configurationFile, serializer)
                .getConfiguration())
        .isEqualTo(updatedConfiguration);
  }
}
