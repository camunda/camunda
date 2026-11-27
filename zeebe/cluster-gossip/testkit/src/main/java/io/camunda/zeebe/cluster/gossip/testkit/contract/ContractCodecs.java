/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.testkit.contract;

import io.camunda.zeebe.cluster.gossip.GossipCodec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class ContractCodecs {

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

  /** A codec for strings that cannot decode anything, as after an incompatible format change. */
  static final GossipCodec<String> FAILING_DECODER =
      new GossipCodec<>() {
        @Override
        public byte[] encode(final String value) {
          return STRING.encode(value);
        }

        @Override
        public String decode(final byte[] bytes) {
          throw new IllegalArgumentException("unknown format");
        }
      };

  private ContractCodecs() {}
}
