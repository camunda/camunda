/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.util.ClusterTopologyDomain;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.domains.Domain;
import net.jqwik.api.domains.DomainContext;
import org.junit.jupiter.api.Test;

/**
 * Round-tripping is what makes the JSON trustworthy, because it cannot hold while anything is
 * missing: a value the writer renders as an empty object, a component dropped by a misconfigured
 * mapper, an identifier flattened to the wrong shape, or a sealed variant that cannot be told apart
 * from its siblings all fail here, rather than in front of whoever is reading a dump during an
 * incident. Generated configurations reach corners hand-written examples do not.
 */
final class ClusterConfigurationJsonSerializerPropertyTest {

  private static final String EQUALITY_HINT =
      """
      Configuration read back from JSON must equal the one written.
      If both look the same, compare the collection types: jqwik generates sorted sets and maps, and \
      a reader that builds unsorted ones yields an unequal-but-identical-looking configuration.""";

  @Property(tries = 100)
  @Domain(ClusterTopologyDomain.class)
  @Domain(DomainContext.Global.class)
  void shouldWriteAndReadBackAnyConfiguration(
      @ForAll final CurrentClusterConfiguration configuration) {
    // when
    final var json = ClusterConfigurationJsonSerializer.toJson(configuration);

    // then
    assertThat(ClusterConfigurationJsonSerializer.fromJson(json))
        .describedAs(EQUALITY_HINT)
        .isEqualTo(configuration);
  }

  @Test
  void shouldWriteAndReadBackTheNeverUpdatedSentinel() {
    // given — brokers that have not been updated yet carry Instant.MIN, which is what a freshly
    // started or uninitialized cluster looks like. The generated configurations above use ordinary
    // timestamps, so this is the one corner they never reach, and it is the corner most likely to
    // be dumped: it is not a valid RFC3339 or protobuf timestamp, and formats have refused it
    // before (see ClusterApiUtils#mapInstantToDateTime and camunda/camunda#16256).
    final var configuration =
        CurrentClusterConfiguration.fromLegacy(
            ClusterConfiguration.init()
                .addMember(
                    MemberId.from("0"),
                    MemberState.initializeAsActive(
                        Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())))));

    // when
    final var json = ClusterConfigurationJsonSerializer.toJson(configuration);

    // then
    assertThat(ClusterConfigurationJsonSerializer.fromJson(json)).isEqualTo(configuration);
  }
}
