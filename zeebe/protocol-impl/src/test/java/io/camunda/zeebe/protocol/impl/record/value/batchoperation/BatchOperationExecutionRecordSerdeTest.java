/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue.BatchOperationExecutionItemValue;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link
 * io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy.BatchOperationExecutionRecord}
 * is a frozen copy of the property based implementation the generated codec replaced.
 */
final class BatchOperationExecutionRecordSerdeTest {

  // the keys and ordinals change the integer widths: 1 byte, 3 bytes and 5 bytes
  private static final List<BatchOperationExecutionItemValue> ITEMS =
      List.of(
          new BatchOperationExecutionItem(1L, 0),
          new BatchOperationExecutionItem(300L, 5),
          new BatchOperationExecutionItem(70_000L, 200));
  private static final Set<Long> KEYS = new LinkedHashSet<>(List.of(1L, 300L, 70_000L));

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy
            .BatchOperationExecutionRecord();
    final var current = new BatchOperationExecutionRecord();
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
  void shouldWriteTheSameBytesAsV1WithKeysOnly() {
    // given the items without storage ordinals
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy
            .BatchOperationExecutionRecord();
    final var current = new BatchOperationExecutionRecord();
    v1.setBatchOperationKey(42L).setItemKeys(KEYS);
    current.setBatchOperationKey(42L).setItemKeys(KEYS);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given only the property without a default is set, everything else stays default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy
            .BatchOperationExecutionRecord();
    final var current = new BatchOperationExecutionRecord();
    v1.setBatchOperationKey(42L);
    current.setBatchOperationKey(42L);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldFailToWriteWhileUnsetLikeV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy
            .BatchOperationExecutionRecord();
    final var current = new BatchOperationExecutionRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy
            .BatchOperationExecutionRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new BatchOperationExecutionRecord();
    current.setBatchOperationKey(7L).setStorageOrdinal(7).setItemKeys(Set.of(9L, 8L));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getBatchOperationKey()).isEqualTo(42L);
    assertThat(current.getStorageOrdinal()).isEqualTo(3);
    assertThat(current.getItemKeys()).isEqualTo(KEYS);
    assertThat(current.getItems()).hasSize(3);
    assertThat(current.getItems().get(2).getItemKey()).isEqualTo(70_000L);
    assertThat(current.getItems().get(2).getStorageOrdinal()).isEqualTo(200);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere except the property without a default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy
            .BatchOperationExecutionRecord();
    v1.setBatchOperationKey(42L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new BatchOperationExecutionRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getBatchOperationKey()).isEqualTo(42L);
    assertThat(current.getStorageOrdinal()).isEqualTo(0);
    assertThat(current.getItems()).isEmpty();
    assertThat(current.getItemKeys()).isEmpty();
  }

  @Test
  void shouldFailToReadWithoutTheKeyLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure =
        catchThrowable(
            () ->
                new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy
                        .BatchOperationExecutionRecord()
                    .wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new BatchOperationExecutionRecord().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new BatchOperationExecutionRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy
            .BatchOperationExecutionRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getBatchOperationKey()).isEqualTo(42L);
    assertThat(v1.getStorageOrdinal()).isEqualTo(3);
    assertThat(v1.getItemKeys()).isEqualTo(KEYS);
    assertThat(v1.getItems()).hasSize(3);
    assertThat(v1.getItems().get(2).getStorageOrdinal()).isEqualTo(200);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new BatchOperationExecutionRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has four entries, plus one unknown: 0x84 becomes 0x85
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new BatchOperationExecutionRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setStorageOrdinal(9);
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
        new io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy
            .BatchOperationExecutionRecord();
    final var current = new BatchOperationExecutionRecord();
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
      final io.camunda.zeebe.protocol.impl.record.value.batchoperation.legacy
              .BatchOperationExecutionRecord
          record) {
    record.setBatchOperationKey(42L).setStorageOrdinal(3).setItems(ITEMS);
  }

  private static void setAllProperties(final BatchOperationExecutionRecord record) {
    record.setBatchOperationKey(42L).setStorageOrdinal(3).setItems(ITEMS);
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
