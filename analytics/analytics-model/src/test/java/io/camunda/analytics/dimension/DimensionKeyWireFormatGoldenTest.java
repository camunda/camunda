/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden-value pin of the {@link DimensionKeyValue} wire/store format (ADR 0008, invariant 3).
 *
 * <p>THE HEX LITERALS BELOW ARE A FROZEN CONTRACT — do not regenerate them to make a failing build
 * green. They are the exact bytes of sealed shuffle segments and durable rollup cells already
 * written by today's implementation; the byte-backed {@code DimensionKey} refactor must produce
 * bit-identical encodings (its {@code encoded} form IS this layout) and must decode these bytes
 * into logically identical keys. A change here means old durable state can no longer be read.
 *
 * <p>Layout (msgpack via {@code UnpackedObject}): a 1-entry map {@code {"v": [...]}} whose array
 * holds one kind-tagged element per column in schema order, each a 3-entry map {@code {"k": kind,
 * "s": string-or-"", "n": long-or-0}} with kinds NULL=0, STRING=1, LONG=2, INT=3, BOOLEAN=4.
 */
final class DimensionKeyWireFormatGoldenTest {

  private static final DimensionSchema STRING_GRAIN =
      DimensionSchema.of(new DimensionColumn("s", DimensionType.STRING));
  private static final DimensionSchema TEXT_GRAIN =
      DimensionSchema.of(new DimensionColumn("t", DimensionType.TEXT));
  private static final DimensionSchema LONG_GRAIN =
      DimensionSchema.of(new DimensionColumn("l", DimensionType.LONG));
  private static final DimensionSchema INT_GRAIN =
      DimensionSchema.of(new DimensionColumn("i", DimensionType.INT));
  private static final DimensionSchema BOOL_GRAIN =
      DimensionSchema.of(new DimensionColumn("b", DimensionType.BOOLEAN));
  private static final DimensionSchema MIXED_GRAIN =
      DimensionSchema.of(
          new DimensionColumn("region", DimensionType.STRING),
          new DimensionColumn("processDefinitionKey", DimensionType.LONG),
          new DimensionColumn("version", DimensionType.INT),
          new DimensionColumn("completed", DimensionType.BOOLEAN));

  /** The frozen corpus: (schema, logical values, exact encoded bytes as hex). */
  private static final List<Golden> CORPUS =
      List.of(
          // -- STRING kind (k=1): value in the "s" slot, UTF-8
          golden(STRING_GRAIN, values("EU"), "81a1769183a16b01a173a24555a16e00"),
          // empty string is a real STRING value (k=1, s="") — distinct from the null bucket
          golden(STRING_GRAIN, values(""), "81a1769183a16b01a173a0a16e00"),
          // unicode goes through UTF-8 (2-, 3-, and 4-byte sequences incl. an emoji)
          golden(
              STRING_GRAIN,
              values("Ω-café-日本-🚀"),
              "81a1769183a16b01a173b4cea92d636166c3a92de697a5e69cac2df09f9a80a16e00"),
          // -- null / unknown bucket (k=0), regardless of the column's declared type
          golden(STRING_GRAIN, values((Object) null), "81a1769183a16b00a173a0a16e00"),
          golden(LONG_GRAIN, values((Object) null), "81a1769183a16b00a173a0a16e00"),
          // -- TEXT columns share the STRING wire kind (k=1); the distinction is schema-side only
          golden(
              TEXT_GRAIN,
              values("some text payload"),
              "81a1769183a16b01a173b1736f6d652074657874207061796c6f6164a16e00"),
          // -- LONG kind (k=2): value in the "n" slot, minimal-length msgpack integer
          golden(LONG_GRAIN, values(0L), "81a1769183a16b02a173a0a16e00"),
          golden(LONG_GRAIN, values(-1L), "81a1769183a16b02a173a0a16eff"),
          golden(
              LONG_GRAIN, values(Long.MAX_VALUE), "81a1769183a16b02a173a0a16ecf7fffffffffffffff"),
          golden(
              LONG_GRAIN, values(Long.MIN_VALUE), "81a1769183a16b02a173a0a16ed38000000000000000"),
          // -- INT kind (k=3): also carried in the "n" slot; the kind tag is what narrows it back
          golden(INT_GRAIN, values(Integer.MAX_VALUE), "81a1769183a16b03a173a0a16ece7fffffff"),
          golden(INT_GRAIN, values(Integer.MIN_VALUE), "81a1769183a16b03a173a0a16ed280000000"),
          // -- BOOLEAN kind (k=4): 1/0 in the "n" slot
          golden(BOOL_GRAIN, values(true), "81a1769183a16b04a173a0a16e01"),
          golden(BOOL_GRAIN, values(false), "81a1769183a16b04a173a0a16e00"),
          // -- multi-column grain: elements appended in schema order, no per-key schema stored
          golden(
              MIXED_GRAIN,
              values("EU", 1234L, 3, true),
              "81a1769483a16b01a173a24555a16e0083a16b02a173a0a16ecd04d2"
                  + "83a16b03a173a0a16e0383a16b04a173a0a16e01"),
          golden(
              MIXED_GRAIN,
              values(null, -1L, 0, false),
              "81a1769483a16b00a173a0a16e0083a16b02a173a0a16eff"
                  + "83a16b03a173a0a16e0083a16b04a173a0a16e00"));

  @Test
  void shouldEncodeToTheFrozenWireBytes() {
    for (final Golden golden : CORPUS) {
      // given
      final DimensionKeyValue codec = new DimensionKeyValue(golden.schema());
      final DimensionKey key = DimensionKey.of(golden.schema(), golden.values());

      // when
      final byte[] encoded = codec.toBytes(key);

      // then — bit-identical to what today's implementation has already written durably
      assertThat(HexFormat.of().formatHex(encoded))
          .as("encoding of %s", Arrays.toString(golden.values()))
          .isEqualTo(golden.hex());
    }
  }

  @Test
  void shouldDecodeTheFrozenWireBytesToTheLogicalKey() {
    for (final Golden golden : CORPUS) {
      // given bytes exactly as an old sealed segment / durable cell holds them
      final DimensionKeyValue codec = new DimensionKeyValue(golden.schema());
      final byte[] stored = HexFormat.of().parseHex(golden.hex());

      // when
      final DimensionKey decoded = codec.fromBytes(stored);

      // then — same logical key, and re-encoding reproduces the identical bytes
      assertThat(decoded)
          .as("decode of %s", golden.hex())
          .isEqualTo(DimensionKey.of(golden.schema(), golden.values()));
      assertThat(new DimensionKeyValue(golden.schema()).toBytes(decoded))
          .as("re-encode of %s", Arrays.toString(golden.values()))
          .isEqualTo(stored);
    }
  }

  private static Golden golden(
      final DimensionSchema schema, final Object[] values, final String hex) {
    return new Golden(schema, values, hex);
  }

  private static Object[] values(final Object... values) {
    return values;
  }

  private record Golden(DimensionSchema schema, Object[] values, String hex) {}
}
