/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.LegacyObjectValues;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link
 * io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy.BatchOperationItem} is a frozen
 * copy of the property based implementation the generated codec replaced.
 */
final class BatchOperationItemSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy.BatchOperationItem(
            1L << 40, 70_000L, 300L, 5);
    final var current = new BatchOperationItem(1L << 40, 70_000L, 300L, 5);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = LegacyObjectValues.write(v1, v1Bytes);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength)
        .isEqualTo(current.getLength())
        .isEqualTo(LegacyObjectValues.length(v1));
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given only defaults, every property has one
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy.BatchOperationItem();
    final var current = new BatchOperationItem();

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = LegacyObjectValues.write(v1, v1Bytes);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy.BatchOperationItem(
            1L << 40, 70_000L, 300L, 5);
    final var bytes = new ExpandableArrayBuffer();
    final int length = LegacyObjectValues.write(v1, bytes);
    final var current = new BatchOperationItem(1L, 2L, 3L, 4);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getItemKey()).isEqualTo(1L << 40);
    assertThat(current.getProcessInstanceKey()).isEqualTo(70_000L);
    assertThat(current.getRootProcessInstanceKey()).isEqualTo(300L);
    assertThat(current.getStorageOrdinal()).isEqualTo(5);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy.BatchOperationItem();
    final var bytes = new ExpandableArrayBuffer();
    final int length = LegacyObjectValues.write(v1, bytes);
    final var current = new BatchOperationItem(1L, 2L, 3L, 4);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getItemKey()).isEqualTo(v1.getItemKey());
    assertThat(current.getProcessInstanceKey()).isEqualTo(v1.getProcessInstanceKey());
    assertThat(current.getRootProcessInstanceKey()).isEqualTo(v1.getRootProcessInstanceKey());
    assertThat(current.getStorageOrdinal()).isEqualTo(v1.getStorageOrdinal());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new BatchOperationItem(1L << 40, 70_000L, 300L, 5);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy.BatchOperationItem();
    LegacyObjectValues.read(v1, bytes, length);

    // then
    assertThat(v1.getItemKey()).isEqualTo(1L << 40);
    assertThat(v1.getProcessInstanceKey()).isEqualTo(70_000L);
    assertThat(v1.getRootProcessInstanceKey()).isEqualTo(300L);
    assertThat(v1.getStorageOrdinal()).isEqualTo(5);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new BatchOperationItem(1L, 2L, 3L, 4);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 4 entries, written as 0x84, plus one unknown: 0x85
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new BatchOperationItem();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setItemKey(100L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy.BatchOperationItem(
            1L << 40, 70_000L, 300L, 5);
    final var current = new BatchOperationItem(1L << 40, 70_000L, 300L, 5);

    // when
    final var mapper = new ObjectMapper();

    // then exporters see the same document
    final com.fasterxml.jackson.databind.JsonNode currentJson = mapper.valueToTree(current);
    final com.fasterxml.jackson.databind.JsonNode v1Json = mapper.valueToTree(v1);
    assertThat(currentJson).isEqualTo(v1Json);
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
