/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.Timestamp;
import io.camunda.zeebe.dynamic.config.protocol.Topology;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Edge cases for the {@code CurrentClusterConfiguration} serialization that the property test
 * ({@code ProtoBufSerializerPropertyTest#shouldEncodeAndDecodeCurrentClusterConfiguration}) cannot
 * exercise: decoding invalid bytes and decoding a proto that carries a broker lifecycle state which
 * the domain model cannot represent. The happy-path round-trips are all covered by the property
 * test.
 */
final class CurrentClusterConfigurationSerializerTest {

  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  @Test
  void shouldFailToDecodeInvalidBytes() {
    // given — a truncated protobuf message (tag for field 1 with no value)
    final byte[] invalid = {0x08};

    // when / then
    assertThatThrownBy(() -> serializer.decodeCurrentClusterConfiguration(invalid))
        .isInstanceOf(DecodingFailed.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = Topology.State.class,
      names = {"BOOTSTRAPPING", "RECOVERING"})
  void shouldRejectBrokerStateWithNonLifecycleState(final Topology.State state) {
    // given — a global configuration whose broker carries a state that is not a broker lifecycle
    // state (BrokerState.State has no BOOTSTRAPPING/RECOVERING)
    final var proto =
        Topology.GlobalConfiguration.newBuilder()
            .setVersion(1)
            .putMembers(
                "0",
                Topology.BrokerState.newBuilder()
                    .setVersion(0)
                    .setLastUpdated(Timestamp.newBuilder().build())
                    .setState(state)
                    .build())
            .build();

    // when / then
    assertThatThrownBy(() -> serializer.decodeGlobalConfiguration(proto))
        .isInstanceOf(IllegalStateException.class);
  }
}
