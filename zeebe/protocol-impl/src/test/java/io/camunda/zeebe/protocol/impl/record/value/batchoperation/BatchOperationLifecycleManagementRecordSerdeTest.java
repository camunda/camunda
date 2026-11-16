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
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link BatchOperationLifecycleManagementRecordV1} is a frozen copy of the property based
 * implementation the generated codec replaced.
 */
final class BatchOperationLifecycleManagementRecordSerdeTest {

  private static final String MESSAGE = "an error message that is longer than forty bytes of text";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new BatchOperationLifecycleManagementRecordV1();
    final var current = new BatchOperationLifecycleManagementRecord();
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
    // given only the property without a default is set, everything else stays default
    final var v1 = new BatchOperationLifecycleManagementRecordV1();
    final var current = new BatchOperationLifecycleManagementRecord();
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
    final var v1 = new BatchOperationLifecycleManagementRecordV1();
    final var current = new BatchOperationLifecycleManagementRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new BatchOperationLifecycleManagementRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new BatchOperationLifecycleManagementRecord();
    current.setBatchOperationKey(7L).setStorageOrdinal(7).setErrors(List.of(error(9, "dirty")));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getBatchOperationKey()).isEqualTo(42L);
    assertThat(current.getStorageOrdinal()).isEqualTo(3);
    assertThat(current.getErrors()).hasSize(2);
    assertThat(current.getErrors().get(0).getMessage()).isEqualTo(MESSAGE);
    assertThat(current.getErrors().get(0).getType())
        .isEqualTo(BatchOperationErrorType.QUERY_FAILED);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere except the property without a default
    final var v1 = new BatchOperationLifecycleManagementRecordV1();
    v1.setBatchOperationKey(42L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new BatchOperationLifecycleManagementRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getBatchOperationKey()).isEqualTo(42L);
    assertThat(current.getStorageOrdinal()).isEqualTo(0);
    assertThat(current.getErrors()).isEmpty();
  }

  @Test
  void shouldFailToReadWithoutTheKeyLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure =
        catchThrowable(() -> new BatchOperationLifecycleManagementRecordV1().wrap(bytes, 0, 1));
    final var failure =
        catchThrowable(() -> new BatchOperationLifecycleManagementRecord().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new BatchOperationLifecycleManagementRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new BatchOperationLifecycleManagementRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getBatchOperationKey()).isEqualTo(42L);
    assertThat(v1.getStorageOrdinal()).isEqualTo(3);
    assertThat(v1.getErrors()).hasSize(2);
    assertThat(v1.getErrors().get(0).getMessage()).isEqualTo(MESSAGE);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new BatchOperationLifecycleManagementRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has three entries, plus one unknown: 0x83 becomes 0x84
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new BatchOperationLifecycleManagementRecord();
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
    final var v1 = new BatchOperationLifecycleManagementRecordV1();
    final var current = new BatchOperationLifecycleManagementRecord();
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

  private static void setAllProperties(final BatchOperationLifecycleManagementRecordV1 record) {
    record
        .setBatchOperationKey(42L)
        .setStorageOrdinal(3)
        .setErrors(List.of(error(1, MESSAGE), error(2, "second")));
  }

  private static void setAllProperties(final BatchOperationLifecycleManagementRecord record) {
    record
        .setBatchOperationKey(42L)
        .setStorageOrdinal(3)
        .setErrors(List.of(error(1, MESSAGE), error(2, "second")));
  }

  private static BatchOperationError error(final int partitionId, final String message) {
    return new BatchOperationError()
        .setPartitionId(partitionId)
        .setType(BatchOperationErrorType.QUERY_FAILED)
        .setMessage(message);
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
