/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationStatus;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.NestedRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationError;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationJobUpdatePlan;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceMigrationPlan;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationPlan;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationMoveInstruction;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.util.buffer.BufferUtil;
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
 * break. {@link PersistedBatchOperationV1} is a frozen copy of the property based implementation
 * the generated codec replaced.
 */
final class PersistedBatchOperationSerdeTest {

  private static final String CURSOR = "a search cursor that is longer than forty bytes of text";
  private static final List<Integer> PARTITIONS = List.of(1, 2, 300);

  private static DirectBuffer filter() {
    return BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack("{\"key\":\"value\"}"));
  }

  private static DirectBuffer authentication() {
    return BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(Map.of("claim", "value")));
  }

  private static BatchOperationProcessInstanceMigrationPlan migrationPlan() {
    return new BatchOperationProcessInstanceMigrationPlan().setTargetProcessDefinitionKey(42L);
  }

  private static BatchOperationProcessInstanceModificationPlan modificationPlan() {
    return new BatchOperationProcessInstanceModificationPlan()
        .addMoveInstruction(
            new ProcessInstanceModificationMoveInstruction()
                .setSourceElementId("source")
                .setTargetElementId("target"));
  }

  private static BatchOperationJobUpdatePlan jobUpdatePlan() {
    return new BatchOperationJobUpdatePlan().setRetries(3).setTimeout(1_000L).setPriority(7);
  }

  private static BatchOperationError error() {
    return new BatchOperationError()
        .setPartitionId(2)
        .setType(BatchOperationErrorType.QUERY_FAILED)
        .setMessage("a message that is longer than forty bytes of utf8 text");
  }

  private static NestedRecord followUpCommand() {
    return new NestedRecord().setValueType(ValueType.JOB);
  }

  /** Sets every property, the ones without a default to a non-default value. */
  private static void setAll(final PersistedBatchOperationV1 v1) {
    v1.setKey(1L << 40)
        .setBatchOperationType(BatchOperationType.MIGRATE_PROCESS_INSTANCE)
        .setStorageOrdinal(200)
        .setStatus(PersistedBatchOperationV1.BatchOperationStatus.INITIALIZED)
        .setEntityFilter(filter())
        .setMigrationPlan(migrationPlan())
        .setModificationPlan(modificationPlan())
        .setJobUpdatePlan(jobUpdatePlan())
        .addChunkKey(1L)
        .addChunkKey(70_000L)
        .setInitializationSearchCursor(CURSOR)
        .setInitializationSearchQueryPageSize(500)
        .setAuthentication(authentication())
        .setPartitions(PARTITIONS)
        .addFinishedPartition(1)
        .setNumTotalItems(1_000)
        .setNumExecutedItems(70_000)
        .addError(error())
        .setFollowUpCommand(followUpCommand());
    v1.markAsInitialized();
  }

  private static void setAll(final PersistedBatchOperation current) {
    current
        .setKey(1L << 40)
        .setBatchOperationType(BatchOperationType.MIGRATE_PROCESS_INSTANCE)
        .setStorageOrdinal(200)
        .setStatus(BatchOperationStatus.INITIALIZED)
        .setEntityFilter(filter())
        .setMigrationPlan(migrationPlan())
        .setModificationPlan(modificationPlan())
        .setJobUpdatePlan(jobUpdatePlan())
        .addChunkKey(1L)
        .addChunkKey(70_000L)
        .setInitializationSearchCursor(CURSOR)
        .setInitializationSearchQueryPageSize(500)
        .setAuthentication(authentication())
        .setPartitions(PARTITIONS)
        .addFinishedPartition(1)
        .setNumTotalItems(1_000)
        .setNumExecutedItems(70_000)
        .addError(error())
        .setFollowUpCommand(followUpCommand());
    current.markAsInitialized();
  }

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new PersistedBatchOperationV1();
    final var current = new PersistedBatchOperation();
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
    // given only the properties without a default are set, the rest stays default
    final var v1 = new PersistedBatchOperationV1();
    final var current = new PersistedBatchOperation();
    v1.setKey(1L)
        .setBatchOperationType(BatchOperationType.UPDATE_JOB)
        .setStatus(PersistedBatchOperationV1.BatchOperationStatus.CREATED)
        .setEntityFilter(filter());
    current
        .setKey(1L)
        .setBatchOperationType(BatchOperationType.UPDATE_JOB)
        .setStatus(BatchOperationStatus.CREATED)
        .setEntityFilter(filter());

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
    final var v1 = new PersistedBatchOperationV1();
    final var current = new PersistedBatchOperation();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new PersistedBatchOperationV1();
    setAll(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedBatchOperation();
    current.setKey(7L).setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    current.setStatus(BatchOperationStatus.FAILED).setEntityFilter(filter());
    current.setPartitions(List.of(9)).addFinishedPartition(9).addChunkKey(9L).addError(error());
    current.setNumTotalItems(3).setInitializationSearchQueryPageSize(3);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getKey()).isEqualTo(1L << 40);
    assertThat(current.getBatchOperationType())
        .isEqualTo(BatchOperationType.MIGRATE_PROCESS_INSTANCE);
    assertThat(current.getStorageOrdinal()).isEqualTo(200);
    assertThat(current.getStatus()).isEqualTo(BatchOperationStatus.INITIALIZED);
    assertThat(current.isInitialized()).isTrue();
    assertThat(current.getInitializationSearchCursor()).isEqualTo(CURSOR);
    assertThat(current.getInitializationSearchQueryPageSize()).isEqualTo(500);
    assertThat(current.getPartitions()).isEqualTo(PARTITIONS);
    assertThat(current.getFinishedPartitions()).containsExactly(1);
    assertThat(current.getNumTotalItems()).isEqualTo(1_000);
    assertThat(current.getNumExecutedItems()).isEqualTo(70_000);
    assertThat(current.getErrors()).hasSize(1);
    assertThat(current.getMaxChunkKey()).isEqualTo(70_000L);
    assertThat(current.getMinChunkKey()).isEqualTo(1L);
    assertThat(current.hasFollowUpCommand()).isTrue();
  }

  @Test
  void shouldResetToDefaultsWhenReadingBytesWithDefaults() {
    // given
    final var bytes = new ExpandableArrayBuffer();
    final var source = new PersistedBatchOperationV1();
    source
        .setKey(1L)
        .setBatchOperationType(BatchOperationType.UPDATE_JOB)
        .setStatus(PersistedBatchOperationV1.BatchOperationStatus.CREATED)
        .setEntityFilter(filter());
    final int length = source.write(bytes, 0);
    final var current = new PersistedBatchOperation();
    setAll(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.isInitialized()).isFalse();
    assertThat(current.getStorageOrdinal()).isZero();
    assertThat(current.getInitializationSearchCursor()).isEmpty();
    assertThat(current.getInitializationSearchQueryPageSize()).isEqualTo(-1);
    assertThat(current.getPartitions()).isEmpty();
    assertThat(current.getFinishedPartitions()).isEmpty();
    assertThat(current.getErrors()).isEmpty();
    assertThat(current.getNumTotalItems()).isZero();
    assertThat(current.getNumExecutedItems()).isZero();
    assertThat(current.hasChunks()).isFalse();
    assertThat(current.hasFollowUpCommand()).isFalse();
  }

  @Test
  void shouldFailToReadWithoutTheKeyLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure = catchThrowable(() -> new PersistedBatchOperationV1().wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new PersistedBatchOperation().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(v1Failure).isInstanceOf(RuntimeException.class);
    assertThat(failure).isInstanceOf(RuntimeException.class);
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedBatchOperation();
    setAll(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new PersistedBatchOperationV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getKey()).isEqualTo(1L << 40);
    assertThat(v1.getStorageOrdinal()).isEqualTo(200);
    assertThat(v1.getStatus())
        .isEqualTo(PersistedBatchOperationV1.BatchOperationStatus.INITIALIZED);
    assertThat(v1.getPartitions()).isEqualTo(PARTITIONS);
    assertThat(v1.getNumExecutedItems()).isEqualTo(70_000);
    assertThat(v1.getErrors()).hasSize(1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedBatchOperation();
    setAll(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // 19 declared entries need the 0xde header with a two byte count: 19 becomes 20
    stored.putByte(2, (byte) (stored.getByte(2) + 1));

    // when read, modified and written again
    final var instance = new PersistedBatchOperation();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setNumExecutedItems(5);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
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
