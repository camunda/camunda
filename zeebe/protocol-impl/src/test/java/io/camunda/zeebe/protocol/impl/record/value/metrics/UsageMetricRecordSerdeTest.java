/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.metrics.legacy.UsageMetricRecord} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class UsageMetricRecordSerdeTest {

  // the msgpack map {"a": 1}
  private static final DirectBuffer COUNTERS =
      new UnsafeBuffer(new byte[] {(byte) 0x81, (byte) 0xa1, 'a', 0x01});
  // the msgpack map {"a": [1]}
  private static final DirectBuffer SETS =
      new UnsafeBuffer(new byte[] {(byte) 0x81, (byte) 0xa1, 'a', (byte) 0x91, 0x01});
  private static final DirectBuffer EMPTY_MAP = new UnsafeBuffer(new byte[] {(byte) 0x80});

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.metrics.legacy.UsageMetricRecord();
    final var current = new UsageMetricRecord();
    setAllProperties(v1);
    setAllProperties(current);

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
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.metrics.legacy.UsageMetricRecord();
    final var current = new UsageMetricRecord();

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
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.metrics.legacy.UsageMetricRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new UsageMetricRecord();
    current
        .setEventType(EventType.TU)
        .setResetTime(7L)
        .setStartTime(7L)
        .setEndTime(7L)
        .setCounterValues(EMPTY_MAP)
        .setSetValues(EMPTY_MAP);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getIntervalType()).isEqualTo(IntervalType.ACTIVE);
    assertThat(current.getEventType()).isEqualTo(EventType.EDI);
    assertThat(current.getResetTime()).isEqualTo(11L);
    assertThat(current.getStartTime()).isEqualTo(12L);
    assertThat(current.getEndTime()).isEqualTo(13L);
    assertThat(current.getCounterValueBuffer()).isEqualTo(COUNTERS);
    assertThat(current.getSetValueBuffer()).isEqualTo(SETS);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.metrics.legacy.UsageMetricRecord();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new UsageMetricRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then re-encoding gives the bytes of the defaults, so no dirtied property survived
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(bytes, length));
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new UsageMetricRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.metrics.legacy.UsageMetricRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getIntervalType()).isEqualTo(IntervalType.ACTIVE);
    assertThat(v1.getEventType()).isEqualTo(EventType.EDI);
    assertThat(v1.getResetTime()).isEqualTo(11L);
    assertThat(v1.getStartTime()).isEqualTo(12L);
    assertThat(v1.getEndTime()).isEqualTo(13L);
    assertThat(v1.getCounterValueBuffer()).isEqualTo(COUNTERS);
    assertThat(v1.getSetValueBuffer()).isEqualTo(SETS);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new UsageMetricRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 7 entries, plus one unknown: 0x87 becomes 0x88
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new UsageMetricRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setEndTime(9L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.metrics.legacy.UsageMetricRecord();
    final var current = new UsageMetricRecord();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
  }

  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.metrics.legacy.UsageMetricRecord record) {
    record
        .setIntervalType(IntervalType.ACTIVE)
        .setEventType(EventType.EDI)
        .setResetTime(11L)
        .setStartTime(12L)
        .setEndTime(13L)
        .setCounterValues(COUNTERS)
        .setSetValues(SETS);
  }

  private static void setAllProperties(final UsageMetricRecord record) {
    record
        .setIntervalType(IntervalType.ACTIVE)
        .setEventType(EventType.EDI)
        .setResetTime(11L)
        .setStartTime(12L)
        .setEndTime(13L)
        .setCounterValues(COUNTERS)
        .setSetValues(SETS);
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
