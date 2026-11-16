/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link PersistedUsageMetricsV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class PersistedUsageMetricsSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new PersistedUsageMetricsV1();
    final var current = new PersistedUsageMetrics();
    setAll(v1);
    setAll(current);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength).isEqualTo(current.getLength()).isEqualTo(v1.getLength());
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given only defaults, the documents default to the empty map like V1
    final var v1 = new PersistedUsageMetricsV1();
    final var current = new PersistedUsageMetrics();

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new PersistedUsageMetricsV1();
    setAll(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedUsageMetrics();
    current.setFromTime(7L).setToTime(8L).setTenantRPIMap(Map.of("other", 9L));
    setMaps(current);
    current.setTenantTUMap(Map.of("other", Set.of(1L)));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getFromTime()).isEqualTo(1_000L);
    assertThat(current.getToTime()).isEqualTo(2_000L);
    assertThat(current.getTenantRPIMap()).isEqualTo(Map.of("rpi", 3L));
    assertThat(current.getTenantEDIMap()).isEqualTo(Map.of("edi", 4L));
    assertThat(current.getTenantTUMap()).isEqualTo(Map.of("tu", Set.of(5L)));
  }

  @Test
  void shouldReadDefaultsAfterReusingTheInstance() {
    // given bytes that carry the maps only, the times have their defaults
    final var v1 = new PersistedUsageMetricsV1();
    setMaps(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedUsageMetrics();
    current.setFromTime(7L).setToTime(8L);

    // when
    current.wrap(bytes, 0, length);

    // then the defaults are restored by reset()
    assertThat(current.getFromTime()).isEqualTo(PersistedUsageMetrics.TIME_NOT_SET);
    assertThat(current.getToTime()).isEqualTo(PersistedUsageMetrics.TIME_NOT_SET);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedUsageMetrics();
    setAll(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new PersistedUsageMetricsV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getFromTime()).isEqualTo(1_000L);
    assertThat(v1.getToTime()).isEqualTo(2_000L);
    assertThat(v1.getTenantRPIMap()).isEqualTo(Map.of("rpi", 3L));
    assertThat(v1.getTenantEDIMap()).isEqualTo(Map.of("edi", 4L));
    assertThat(v1.getTenantTUMap()).isEqualTo(Map.of("tu", Set.of(5L)));
  }

  @Test
  void shouldReadAnEmptyMapAsDefaultsLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer();
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1 = new PersistedUsageMetricsV1();
    final var current = new PersistedUsageMetrics();
    v1.wrap(bytes, 0, 1);
    current.wrap(bytes, 0, 1);

    // then the times and the documents have their defaults
    assertThat(current.getFromTime()).isEqualTo(v1.getFromTime());
    assertThat(hex(current.getTenantRPIMapValue())).isEqualTo(hex(v1.getTenantRPIMapValue()));
    assertThat(hex(current.getTenantEDIMapValue())).isEqualTo(hex(v1.getTenantEDIMapValue()));
    assertThat(hex(current.getTenantTUMapValue())).isEqualTo(hex(v1.getTenantTUMapValue()));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedUsageMetrics();
    setAll(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has five entries, a header of 0x80 | count, so announce one more.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedUsageMetrics();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setToTime(42L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static void setAll(final PersistedUsageMetricsV1 metrics) {
    metrics.setFromTime(1_000L).setToTime(2_000L);
    setMaps(metrics);
  }

  private static void setMaps(final PersistedUsageMetricsV1 metrics) {
    metrics.setTenantRPIMap(Map.of("rpi", 3L));
    metrics.setTenantEDIMap(Map.of("edi", 4L));
    metrics.setTenantTUMap(Map.of("tu", Set.of(5L)));
  }

  private static void setAll(final PersistedUsageMetrics metrics) {
    metrics.setFromTime(1_000L).setToTime(2_000L);
    setMaps(metrics);
  }

  private static void setMaps(final PersistedUsageMetrics metrics) {
    metrics.setTenantRPIMap(Map.of("rpi", 3L));
    metrics.setTenantEDIMap(Map.of("edi", 4L));
    metrics.setTenantTUMap(Map.of("tu", Set.of(5L)));
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(final DirectBuffer buffer) {
    final var bytes = new byte[buffer.capacity()];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
