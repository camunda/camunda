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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link BatchOperationCreationRecordV1} is a frozen copy of the property based
 * implementation the generated codec replaced.
 */
final class BatchOperationCreationRecordSerdeTest {

  private static final byte[] ENTITY_FILTER =
      MsgPackConverter.convertToMsgPack(Map.of("filter", "x".repeat(50)));
  private static final byte[] AUTHENTICATION =
      MsgPackConverter.convertToMsgPack(Map.of("username", "demo"));
  private static final byte[] AUTHORIZATION_CHECK =
      MsgPackConverter.convertToMsgPack(Map.of("authorized", "true"));

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new BatchOperationCreationRecordV1();
    final var current = new BatchOperationCreationRecord();
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
    final var v1 = new BatchOperationCreationRecordV1();
    final var current = new BatchOperationCreationRecord();
    v1.setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    current.setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);

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
    final var v1 = new BatchOperationCreationRecordV1();
    final var current = new BatchOperationCreationRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new BatchOperationCreationRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new BatchOperationCreationRecord();
    setAllProperties(current);
    current
        .setBatchOperationKey(7L)
        .setBatchOperationType(BatchOperationType.UPDATE_JOB)
        .setPartitionIds(List.of(9))
        .setEntityFilter(new UnsafeBuffer(new byte[] {(byte) 0x80}));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getBatchOperationKey()).isEqualTo(42L);
    assertThat(current.getStorageOrdinal()).isEqualTo(3);
    assertThat(current.getBatchOperationType())
        .isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(current.getPartitionIds()).containsExactly(1, 2, 3);
    assertThat(current.getEntityFilterBuffer()).isEqualTo(new UnsafeBuffer(ENTITY_FILTER));
    assertThat(current.getAuthenticationBuffer()).isEqualTo(new UnsafeBuffer(AUTHENTICATION));
    assertThat(current.getAuthorizationCheckBuffer())
        .isEqualTo(new UnsafeBuffer(AUTHORIZATION_CHECK));
    assertThat(current.getMigrationPlan().getTargetProcessDefinitionKey()).isEqualTo(5L);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere except the property without a default
    final var v1 = new BatchOperationCreationRecordV1();
    v1.setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new BatchOperationCreationRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getBatchOperationKey()).isEqualTo(-1L);
    assertThat(current.getStorageOrdinal()).isEqualTo(0);
    assertThat(current.getPartitionIds()).isEmpty();
    assertThat(current.getMigrationPlan().getTargetProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(current.getEntityFilter()).isNull();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new BatchOperationCreationRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new BatchOperationCreationRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getBatchOperationKey()).isEqualTo(42L);
    assertThat(v1.getBatchOperationType()).isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    assertThat(v1.getPartitionIds()).containsExactly(1, 2, 3);
    assertThat(v1.getAuthenticationBuffer()).isEqualTo(new UnsafeBuffer(AUTHENTICATION));
    assertThat(v1.getFollowUpCommand().getValueType()).isEqualTo(ValueType.HISTORY_DELETION);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new BatchOperationCreationRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has eleven entries, plus one unknown: 0x8b becomes 0x8c
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new BatchOperationCreationRecord();
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
    final var v1 = new BatchOperationCreationRecordV1();
    final var current = new BatchOperationCreationRecord();
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

  private static void setAllProperties(final BatchOperationCreationRecordV1 record) {
    record
        .setBatchOperationKey(42L)
        .setStorageOrdinal(3)
        .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
        .setEntityFilter(new UnsafeBuffer(ENTITY_FILTER))
        .setMigrationPlan(
            new BatchOperationProcessInstanceMigrationPlan().setTargetProcessDefinitionKey(5L))
        .setJobUpdatePlan(new BatchOperationJobUpdatePlan().setRetries(3))
        .setAuthentication(new UnsafeBuffer(AUTHENTICATION))
        .setAuthorizationCheck(new UnsafeBuffer(AUTHORIZATION_CHECK))
        .setPartitionIds(List.of(1, 2, 3))
        .setFollowUpCommand(
            ValueType.HISTORY_DELETION, HistoryDeletionIntent.DELETE, historyDeletion());
  }

  private static void setAllProperties(final BatchOperationCreationRecord record) {
    record
        .setBatchOperationKey(42L)
        .setStorageOrdinal(3)
        .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
        .setEntityFilter(new UnsafeBuffer(ENTITY_FILTER))
        .setMigrationPlan(
            new BatchOperationProcessInstanceMigrationPlan().setTargetProcessDefinitionKey(5L))
        .setJobUpdatePlan(new BatchOperationJobUpdatePlan().setRetries(3))
        .setAuthentication(new UnsafeBuffer(AUTHENTICATION))
        .setAuthorizationCheck(new UnsafeBuffer(AUTHORIZATION_CHECK))
        .setPartitionIds(List.of(1, 2, 3))
        .setFollowUpCommand(
            ValueType.HISTORY_DELETION, HistoryDeletionIntent.DELETE, historyDeletion());
  }

  private static HistoryDeletionRecord historyDeletion() {
    return new HistoryDeletionRecord()
        .setResourceType(HistoryDeletionType.PROCESS_DEFINITION)
        .setResourceKey(1);
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
