/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class GossipKeyTest {

  private static final GossipCodec<String> CODEC =
      new GossipCodec<>() {
        @Override
        public byte[] encode(final String value) {
          return value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String decode(final byte[] bytes) {
          return new String(bytes, StandardCharsets.UTF_8);
        }
      };

  @Test
  void shouldDeclareGlobalKey() {
    // when
    final GlobalKey<String> key =
        GossipKey.global("flags", CODEC, MergeStrategy.lastWriterWins(), "default");

    // then
    assertThat(key.name()).isEqualTo("flags");
    assertThat(key.codec()).isSameAs(CODEC);
    assertThat(key.mergeStrategy()).isInstanceOf(MergeStrategy.LastWriterWins.class);
    assertThat(key.defaultValue()).isEqualTo("default");
  }

  @Test
  void shouldDeclareMemberKey() {
    // when
    final MemberKey<String> key = GossipKey.member("partitions", CODEC);

    // then
    assertThat(key.name()).isEqualTo("partitions");
    assertThat(key.codec()).isSameAs(CODEC);
  }

  @Test
  void shouldRejectBlankName() {
    // when - then
    assertThatThrownBy(() -> GossipKey.member(" ", CODEC))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-blank key name");
    assertThatThrownBy(
            () -> GossipKey.global("", CODEC, MergeStrategy.merging((a, b) -> a), "default"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-blank key name");
  }
}
