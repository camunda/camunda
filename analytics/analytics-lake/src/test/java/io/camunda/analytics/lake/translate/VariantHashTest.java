/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.translate;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VariantHash} — the frozen variant-k1 hash/mix scheme (see its own class
 * javadoc for the exact functions under test). These pin down the scheme byte for byte: a future
 * SQL-side reimplementation must match every assertion here.
 */
class VariantHashTest {

  @Test
  void shouldBeDeterministic() {
    // given/when
    final long first = VariantHash.h64("service-task-a");
    final long second = VariantHash.h64("service-task-a");

    // then
    assertThat(first).isEqualTo(second);
  }

  @Test
  void shouldDifferForDifferentInputs() {
    // given/when/then
    assertThat(VariantHash.h64("service-task-a")).isNotEqualTo(VariantHash.h64("service-task-b"));
  }

  @Test
  void shouldMatchManualFnv1aOverUtf8BytesForAnAsciiString() {
    // given: the frozen scheme's own definition, computed independently over a plain byte[] --
    // see VariantHash's class javadoc for the offset basis / prime this must match exactly
    final String input = "gateway-1";
    long expected = 0xcbf29ce484222325L;
    for (final byte b : input.getBytes(UTF_8)) {
      expected = (expected ^ (b & 0xFFL)) * 0x100000001b3L;
    }

    // when
    final long actual = VariantHash.h64(input);

    // then
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void shouldMatchManualFnv1aForAMultiByteUtf8String() {
    // given: a string whose UTF-8 encoding includes 2-byte (é), 3-byte (€) and the ASCII range --
    // proves the on-the-fly UTF-8 encoding in h64 matches String#getBytes(UTF_8) exactly
    final String input = "café-€-task";
    long expected = 0xcbf29ce484222325L;
    for (final byte b : input.getBytes(UTF_8)) {
      expected = (expected ^ (b & 0xFFL)) * 0x100000001b3L;
    }

    // when
    final long actual = VariantHash.h64(input);

    // then
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void shouldMatchManualFnv1aForASupplementaryPlaneCodePoint() {
    // given: a surrogate pair (outside the BMP), 4-byte UTF-8 encoding
    final String input = "task-😀"; // U+1F600 GRINNING FACE
    long expected = 0xcbf29ce484222325L;
    for (final byte b : input.getBytes(UTF_8)) {
      expected = (expected ^ (b & 0xFFL)) * 0x100000001b3L;
    }

    // when
    final long actual = VariantHash.h64(input);

    // then
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void shouldHandleEmptyString() {
    // given/when
    final long hash = VariantHash.h64("");

    // then: the bare offset basis, nothing folded in
    assertThat(hash).isEqualTo(0xcbf29ce484222325L);
  }

  @Test
  void mix64ShouldBeDeterministic() {
    // given/when
    final long first = VariantHash.mix64(1L, 2L);
    final long second = VariantHash.mix64(1L, 2L);

    // then
    assertThat(first).isEqualTo(second);
  }

  @Test
  void mix64ShouldDifferForDifferentSeeds() {
    // given/when/then: the same id hashed under two different processes (seeds) must mix
    // differently -- otherwise two unrelated processes sharing an element id could collide
    assertThat(VariantHash.mix64(1L, 42L)).isNotEqualTo(VariantHash.mix64(2L, 42L));
  }

  @Test
  void mix64ShouldBeSymmetricInItsTwoArguments() {
    // given: mix64 folds seed ^ x through the finalizer -- XOR is commutative, so the function is
    // symmetric in its two arguments; this is a property of the frozen definition, not a bug
    assertThat(VariantHash.mix64(7L, 11L)).isEqualTo(VariantHash.mix64(11L, 7L));
  }

  @Test
  void toHex16ShouldEncodeZeroAsSixteenZeros() {
    assertThat(VariantHash.toHex16(0L)).isEqualTo("0000000000000000");
  }

  @Test
  void toHex16ShouldEncodeMinusOneAsSixteenFs() {
    assertThat(VariantHash.toHex16(-1L)).isEqualTo("ffffffffffffffff");
  }

  @Test
  void toHex16ShouldProduceSixteenLowercaseHexCharacters() {
    // given/when
    final String hex = VariantHash.toHex16(VariantHash.h64("some-element-id"));

    // then
    assertThat(hex).hasSize(16).matches("[0-9a-f]{16}");
  }

  @Test
  void toHex16ShouldBeMostSignificantByteFirst() {
    // given: a value whose top byte is non-zero and bottom bytes are zero
    final long value = 0x12_00_00_00_00_00_00_00L;

    // when/then
    assertThat(VariantHash.toHex16(value)).isEqualTo("1200000000000000");
  }
}
