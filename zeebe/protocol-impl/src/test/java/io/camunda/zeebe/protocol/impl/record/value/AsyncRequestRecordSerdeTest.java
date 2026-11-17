/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.legacy.AsyncRequestRecord} is a frozen
 * copy of the property based implementation the generated codec replaced.
 */
final class AsyncRequestRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.legacy.AsyncRequestRecord();
    final var current = new AsyncRequestRecord();
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
    // given only defaults, every property has one
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.legacy.AsyncRequestRecord();
    final var current = new AsyncRequestRecord();

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
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.legacy.AsyncRequestRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AsyncRequestRecord();
    current
        .setScopeKey(1L)
        .setValueType(ValueType.USER)
        .setIntent(JobIntent.FAIL)
        .setRequestId(2L)
        .setRequestStreamId(4)
        .setOperationReference(6L)
        .setBatchOperationReference(8L)
        .setStorageOrdinal(10);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getScopeKey()).isEqualTo(42L);
    assertThat(current.getValueType()).isEqualTo(ValueType.JOB);
    assertThat(current.getIntent()).isEqualTo(JobIntent.COMPLETE);
    assertThat(current.getRequestId()).isEqualTo(7L);
    assertThat(current.getRequestStreamId()).isEqualTo(3);
    assertThat(current.getOperationReference()).isEqualTo(9L);
    assertThat(current.getBatchOperationReference()).isEqualTo(11L);
    assertThat(current.getStorageOrdinal()).isEqualTo(5);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.legacy.AsyncRequestRecord();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AsyncRequestRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults =
        new io.camunda.zeebe.protocol.impl.record.value.legacy.AsyncRequestRecord();
    assertThat(current.getScopeKey()).isEqualTo(defaults.getScopeKey());
    assertThat(current.getValueType()).isEqualTo(defaults.getValueType());
    assertThat(current.getRequestId()).isEqualTo(defaults.getRequestId());
    assertThat(current.getRequestStreamId()).isEqualTo(defaults.getRequestStreamId());
    assertThat(current.getOperationReference()).isEqualTo(defaults.getOperationReference());
    assertThat(current.getBatchOperationReference())
        .isEqualTo(defaults.getBatchOperationReference());
    assertThat(current.getStorageOrdinal()).isEqualTo(defaults.getStorageOrdinal());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new AsyncRequestRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.legacy.AsyncRequestRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getScopeKey()).isEqualTo(42L);
    assertThat(v1.getValueType()).isEqualTo(ValueType.JOB);
    assertThat(v1.getIntent()).isEqualTo(JobIntent.COMPLETE);
    assertThat(v1.getRequestId()).isEqualTo(7L);
    assertThat(v1.getRequestStreamId()).isEqualTo(3);
    assertThat(v1.getOperationReference()).isEqualTo(9L);
    assertThat(v1.getBatchOperationReference()).isEqualTo(11L);
    assertThat(v1.getStorageOrdinal()).isEqualTo(5);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new AsyncRequestRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has eight entries, plus one unknown: 0x88 becomes 0x89
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new AsyncRequestRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setRequestId(100L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.legacy.AsyncRequestRecord();
    final var current = new AsyncRequestRecord();
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
      final io.camunda.zeebe.protocol.impl.record.value.legacy.AsyncRequestRecord record) {
    record
        .setScopeKey(42L)
        .setValueType(ValueType.JOB)
        .setIntent(JobIntent.COMPLETE)
        .setRequestId(7L)
        .setRequestStreamId(3)
        .setOperationReference(9L)
        .setBatchOperationReference(11L)
        .setStorageOrdinal(5);
  }

  private static void setAllProperties(final AsyncRequestRecord record) {
    record
        .setScopeKey(42L)
        .setValueType(ValueType.JOB)
        .setIntent(JobIntent.COMPLETE)
        .setRequestId(7L)
        .setRequestStreamId(3)
        .setOperationReference(9L)
        .setBatchOperationReference(11L)
        .setStorageOrdinal(5);
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
