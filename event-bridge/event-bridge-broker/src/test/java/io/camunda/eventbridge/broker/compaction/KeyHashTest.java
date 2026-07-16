/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.broker.compaction.KeyHash.Hash128;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins the 128-bit key hash to the canonical MurmurHash3 x64 128 digest. The golden values below
 * were cross-checked against an independent reference implementation of the algorithm, so a
 * refactor that silently changes the digest — which would corrupt every clean set on disk — fails
 * here.
 */
final class KeyHashTest {

  private static Hash128 hash(final String s) {
    return KeyHash.hash(s.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void shouldMatchCanonicalMurmur3Vectors() {
    // given / when / then — canonical MurmurHash3 x64 128 (seed 0) digests
    assertThat(hash("")).isEqualTo(new Hash128(0L, 0L));
    assertThat(hash("hello")).isEqualTo(new Hash128(-3758069500696749310L, 6565844092913065241L));
    assertThat(hash("The quick brown fox jumps over the lazy dog"))
        .isEqualTo(new Hash128(-2068352364225029268L, 8809951995912426311L));
    assertThat(hash("key-0000000042"))
        .isEqualTo(new Hash128(3892190919217450352L, -1757187300729897748L));
  }

  @Test
  void shouldBeDeterministic() {
    // given
    final byte[] key = "some-partition-key".getBytes(StandardCharsets.UTF_8);

    // when
    final Hash128 first = KeyHash.hash(key);
    final Hash128 second = KeyHash.hash(key);

    // then
    assertThat(first).isEqualTo(second);
  }

  @Test
  void shouldHashArrayRegionSameAsSubArray() {
    // given
    final byte[] framed = "XXXpayloadYYY".getBytes(StandardCharsets.UTF_8);
    final byte[] bare = "payload".getBytes(StandardCharsets.UTF_8);

    // when
    final Hash128 fromRegion = KeyHash.hash(framed, 3, bare.length);
    final Hash128 fromWhole = KeyHash.hash(bare);

    // then
    assertThat(fromRegion).isEqualTo(fromWhole);
  }

  @Test
  void shouldDistributeDistinctKeysWithoutCollision() {
    // given
    final Set<Hash128> digests = new HashSet<>();

    // when
    for (int i = 0; i < 100_000; i++) {
      digests.add(hash("key-" + i));
    }

    // then — no collisions across 100k distinct keys
    assertThat(digests).hasSize(100_000);
  }

  @Test
  void shouldAvalancheOnSingleBitChange() {
    // given two keys differing by one bit
    final Hash128 a = hash("key-0");
    final Hash128 b = hash("key-1");

    // when / then — the digests are entirely different in both halves
    assertThat(a.high()).isNotEqualTo(b.high());
    assertThat(a.low()).isNotEqualTo(b.low());
  }
}
