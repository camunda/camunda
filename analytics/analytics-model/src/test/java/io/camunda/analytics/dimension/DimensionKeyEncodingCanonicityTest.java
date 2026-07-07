/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Encoding canonicity and identity of {@link DimensionKey}/{@link DimensionKeyValue} (ADR 0008,
 * invariant 2): the byte-backed refactor makes key identity {@code Arrays.equals(encoded)}, which
 * is only sound if today's encoding is canonical — the same logical values always yield the same
 * bytes, regardless of how the key was constructed — and if logical equality, hash agreement, and
 * per-column distinctness already hold. These tests pin exactly that, so byte equality can later
 * substitute for object equality with no observable change.
 */
final class DimensionKeyEncodingCanonicityTest {

  private static final DimensionSchema GRAIN =
      DimensionSchema.of(
          new DimensionColumn("region", DimensionType.STRING),
          new DimensionColumn("processDefinitionKey", DimensionType.LONG),
          new DimensionColumn("version", DimensionType.INT),
          new DimensionColumn("completed", DimensionType.BOOLEAN));

  /** Representative logical value tuples across all kinds, null buckets, unicode, boundaries. */
  private static final List<Object[]> TUPLES =
      List.of(
          new Object[] {"EU", 1234L, 3, true},
          new Object[] {null, null, null, null},
          new Object[] {"", 0L, 0, false},
          new Object[] {"Ω-café-日本-🚀", -1L, -1, true},
          new Object[] {" ", Long.MIN_VALUE, Integer.MIN_VALUE, false},
          new Object[] {"a\u0001b", Long.MAX_VALUE, Integer.MAX_VALUE, null});

  @Test
  void shouldReencodeToIdenticalBytesAfterDecode() {
    for (final Object[] tuple : TUPLES) {
      // given the encoded form of a key
      final byte[] first = encode(DimensionKey.of(GRAIN, tuple));

      // when it is decoded and encoded again (the Stage-2 / durable-cell round trip)
      final byte[] second = encode(new DimensionKeyValue(GRAIN).fromBytes(first));

      // then the bytes are identical — encode ∘ decode is the identity on the wire form
      assertThat(second).as("re-encode of %s", Arrays.toString(tuple)).isEqualTo(first);
    }
  }

  @Test
  void shouldEncodeToIdenticalBytesRegardlessOfConstructionPath() {
    // given the same logical values arriving through every construction path there is today
    final DimensionKey ofVarargs = DimensionKey.of(GRAIN, "EU", 1234L, 3, true);
    final DimensionKey ofList =
        DimensionKey.of(GRAIN, new ArrayList<>(List.of("EU", 1234L, 3, true)));
    final DimensionKey fromFact =
        new DimensionKeySelector(GRAIN)
            .getKey(
                Fact.builder(FactType.PROCESS_INSTANCE)
                    .field("region", "EU")
                    .field("processDefinitionKey", 1234L)
                    .field("version", 3)
                    .field("completed", true)
                    .build());
    // the selector's narrowing coercion: the fact exposes an int where the column is LONG (and
    // vice versa) — the canonical encoding must not depend on the boxed type the fact carried
    final DimensionKey fromCoercedFact =
        new DimensionKeySelector(GRAIN)
            .getKey(
                Fact.builder(FactType.PROCESS_INSTANCE)
                    .field("region", "EU")
                    .field("processDefinitionKey", 1234)
                    .field("version", 3L)
                    .field("completed", true)
                    .build());
    final DimensionKey decoded =
        new DimensionKeyValue(GRAIN)
            .fromBytes(encode(DimensionKey.of(GRAIN, "EU", 1234L, 3, true)));

    // then all paths agree logically and produce byte-identical encodings
    final byte[] canonical = encode(ofVarargs);
    for (final DimensionKey key : List.of(ofList, fromFact, fromCoercedFact, decoded)) {
      assertThat(key).isEqualTo(ofVarargs).hasSameHashCodeAs(ofVarargs);
      assertThat(encode(key)).isEqualTo(canonical);
    }
  }

  @Test
  void shouldAgreeOnEqualsAndHashCodeForLogicallyEqualKeysIncludingNullBuckets() {
    for (final Object[] tuple : TUPLES) {
      // given two independently constructed keys over the same logical values
      final DimensionKey a = DimensionKey.of(GRAIN, tuple);
      final DimensionKey b = DimensionKey.of(GRAIN, Arrays.asList(tuple));

      // then equality and hash agree with logical equality, and so do the encoded bytes
      assertThat(a).as("equality of %s", Arrays.toString(tuple)).isEqualTo(b).hasSameHashCodeAs(b);
      assertThat(encode(a)).isEqualTo(encode(b));
    }
  }

  @Test
  void shouldDifferWhenExactlyOneColumnDiffers() {
    // given a base key and variants that change one column at a time (value change and null-swap)
    final Object[] base = {"EU", 1234L, 3, true};
    final List<Object[]> variants =
        List.of(
            new Object[] {"US", 1234L, 3, true},
            new Object[] {null, 1234L, 3, true},
            new Object[] {"", 1234L, 3, true},
            new Object[] {"EU", 1235L, 3, true},
            new Object[] {"EU", null, 3, true},
            new Object[] {"EU", 1234L, 4, true},
            new Object[] {"EU", 1234L, null, true},
            new Object[] {"EU", 1234L, 3, false},
            // false and the null bucket are distinct values, not a shared "falsy" bucket
            new Object[] {"EU", 1234L, 3, null});
    final DimensionKey baseKey = DimensionKey.of(GRAIN, base);
    final byte[] baseBytes = encode(baseKey);

    for (final Object[] variant : variants) {
      // then the keys differ both logically and in their encoded bytes
      final DimensionKey variantKey = DimensionKey.of(GRAIN, variant);
      assertThat(variantKey).as("variant %s", Arrays.toString(variant)).isNotEqualTo(baseKey);
      assertThat(encode(variantKey))
          .as("encoding of variant %s", Arrays.toString(variant))
          .isNotEqualTo(baseBytes);
    }
  }

  @Test
  void shouldDistinguishEmptyStringFromNullBucket() {
    // given — "" is a real STRING value, null is the unknown bucket
    final DimensionKey empty = DimensionKey.of(GRAIN, "", 1L, 1, true);
    final DimensionKey unknown = DimensionKey.of(GRAIN, null, 1L, 1, true);

    // then they are distinct logically and on the wire
    assertThat(empty).isNotEqualTo(unknown);
    assertThat(encode(empty)).isNotEqualTo(encode(unknown));
  }

  @Test
  void shouldEncodeIdenticallyThroughAReusedAndAFreshFlyweight() {
    // given one flyweight that has already encoded a different key (reuse is the production mode)
    final DimensionKeyValue reused = new DimensionKeyValue(GRAIN);
    reused.toBytes(DimensionKey.of(GRAIN, "stale", 9L, 9, false));
    final DimensionKey key = DimensionKey.of(GRAIN, "EU", 1234L, 3, true);

    // when the same key is encoded by the reused and by a fresh instance
    final byte[] viaReused = reused.toBytes(key);
    final byte[] viaFresh = new DimensionKeyValue(GRAIN).toBytes(key);

    // then no state bleeds through — the encoding is canonical per key, not per flyweight
    assertThat(viaReused).isEqualTo(viaFresh);
  }

  private static byte[] encode(final DimensionKey key) {
    return new DimensionKeyValue(key.schema()).toBytes(key);
  }
}
