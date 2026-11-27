/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.testkit;

import io.camunda.zeebe.cluster.gossip.GossipCodec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class TestCodecs {

  static final GossipCodec<String> STRING =
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

  static final GossipCodec<Long> LONG =
      new GossipCodec<>() {
        @Override
        public byte[] encode(final Long value) {
          return Long.toString(value).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Long decode(final byte[] bytes) {
          return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
        }
      };

  /** Encodes sorted, so equal sets encode to identical bytes. */
  static final GossipCodec<Set<String>> STRING_SET =
      new GossipCodec<>() {
        @Override
        public byte[] encode(final Set<String> value) {
          return String.join(",", new TreeSet<>(value)).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Set<String> decode(final byte[] bytes) {
          final var text = new String(bytes, StandardCharsets.UTF_8);
          return text.isEmpty()
              ? Set.of()
              : Arrays.stream(text.split(",")).collect(Collectors.toUnmodifiableSet());
        }
      };

  private TestCodecs() {}
}
