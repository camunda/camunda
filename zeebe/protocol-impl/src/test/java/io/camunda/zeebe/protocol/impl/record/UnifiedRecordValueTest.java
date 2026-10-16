/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.management.ArchivedSnapshot;
import io.camunda.zeebe.protocol.impl.record.value.management.CoveredCheckpoint;
import io.camunda.zeebe.protocol.impl.record.value.management.IndexChunk;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.SuspensionBatchRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class UnifiedRecordValueTest {

  private static final Set<ValueType> EXPECTED_NULL_VALUE_TYPES =
      Set.of(ValueType.SBE_UNKNOWN, ValueType.NULL_VAL);

  @ParameterizedTest
  @MethodSource("provideValueTypes")
  void shouldReturnNonNullRecordValueForValueType(final ValueType valueType) {
    // given
    final var expectedNull = EXPECTED_NULL_VALUE_TYPES.contains(valueType);
    // when
    final UnifiedRecordValue result = UnifiedRecordValue.fromValueType(valueType);

    // then
    if (expectedNull) {
      assertThat(result).as("Expected null for ValueType.%s", valueType.name()).isNull();
    } else {
      assertThat(result)
          .as("Expected non-null record value for ValueType.%s", valueType.name())
          .isNotNull();
      assertThat(result.valueType()).isEqualTo(valueType);

      // Enforce the naming convention "${camelCase(valueType)}Record"
      assertThat(result.getClass().getSimpleName())
          .isEqualTo(snakeCaseToCamelCase(valueType.name().toLowerCase()) + "Record");
    }
  }

  @ParameterizedTest
  @MethodSource("suspensionBatchRecords")
  void shouldRoundTripSuspensionBatchRecord(final SuspensionBatchRecord original) {
    // given
    final var buffer = new UnsafeBuffer(new byte[original.getLength()]);
    original.write(buffer, 0);

    // when
    final var copy = new SuspensionBatchRecord();
    copy.wrap(buffer);

    // then
    assertThat(copy.getProcessInstanceKey()).isEqualTo(original.getProcessInstanceKey());
    assertThat(copy.getProcessDefinitionKey()).isEqualTo(original.getProcessDefinitionKey());
    assertThat(copy.getElementInstanceKey()).isEqualTo(original.getElementInstanceKey());
    assertThat(copy.getParentKey()).isEqualTo(original.getParentKey());
    assertThat(copy.getIndex()).isEqualTo(original.getIndex());
    assertThat(copy.getStorageOrdinal()).isEqualTo(original.getStorageOrdinal());
  }

  @Test
  void shouldDefaultMissingSuspensionBatchFields() {
    // given
    final var buffer = new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{}"));

    // when
    final var record = new SuspensionBatchRecord();
    record.wrap(buffer);

    // then
    assertThat(record.getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(record.getProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(record.getElementInstanceKey()).isEqualTo(-1L);
    assertThat(record.getParentKey()).isEqualTo(-1L);
    assertThat(record.getIndex()).isEqualTo(-1L);
    assertThat(record.getStorageOrdinal()).isZero();
  }

  @Test
  void shouldRoundTripLogArchiveRecordWithNestedValues() {
    // given
    final var original =
        new LogArchiveRecord()
            .setTimelineId("t1")
            .setSeq(3L)
            .setStartIndex(1L)
            .setParentTimelineId("t0")
            .addObject(new LogObject().setKey("data/1.rec").setFirstIndex(1L).setLastIndex(9L))
            .addObject(new LogObject().setKey("data/2.rec").setFirstIndex(10L).setLastIndex(19L))
            .setSnapshot(new ArchivedSnapshot().setId("1-2-3-4-5-6").setIndex(10L).setFileCount(4L))
            .setChunk(new IndexChunk().setChunkNo(1L).setKey("index/1.json").setObjectCount(2L))
            .setPublishedSeq(2L)
            .setArchivedIndex(19L)
            .setArchivedPosition(90L)
            .addCheckpoint(
                new CoveredCheckpoint()
                    .setCheckpointId(8L)
                    .setCheckpointPosition(30L)
                    .setCheckpointType(CheckpointType.SCHEDULED_BACKUP));
    final var buffer = new UnsafeBuffer(new byte[original.getLength()]);
    original.write(buffer, 0);

    // when
    final var copy = new LogArchiveRecord();
    copy.wrap(buffer);

    // then
    assertThat(copy.getTimelineId()).isEqualTo("t1");
    assertThat(copy.getSeq()).isEqualTo(3L);
    assertThat(copy.getParentTimelineId()).isEqualTo("t0");
    assertThat(copy.getObjects())
        .extracting(o -> o.getKey())
        .containsExactly("data/1.rec", "data/2.rec");
    assertThat(copy.getSnapshot().getId()).isEqualTo("1-2-3-4-5-6");
    assertThat(copy.getSnapshot().getFileCount()).isEqualTo(4L);
    assertThat(copy.getChunk().getKey()).isEqualTo("index/1.json");
    assertThat(copy.getPublishedSeq()).isEqualTo(2L);
    assertThat(copy.getArchivedIndex()).isEqualTo(19L);
    assertThat(copy.getArchivedPosition()).isEqualTo(90L);
    assertThat(copy.getCheckpoints())
        .extracting(c -> c.getCheckpointType())
        .containsExactly(CheckpointType.SCHEDULED_BACKUP);
  }

  @Test
  void shouldDefaultMissingLogArchiveFields() {
    // given
    final var buffer = new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{}"));

    // when
    final var record = new LogArchiveRecord();
    record.wrap(buffer);

    // then
    assertThat(record.getTimelineId()).isEmpty();
    assertThat(record.getSeq()).isEqualTo(-1L);
    assertThat(record.getStartIndex()).isEqualTo(-1L);
    assertThat(record.getObjects()).isEmpty();
    assertThat(record.getCheckpoints()).isEmpty();
    assertThat(record.getSnapshot().getIndex()).isEqualTo(-1L);
  }

  private static Stream<SuspensionBatchRecord> suspensionBatchRecords() {
    return Stream.of(
        new SuspensionBatchRecord(),
        new SuspensionBatchRecord()
            .setProcessInstanceKey(123L)
            .setProcessDefinitionKey(234L)
            .setElementInstanceKey(123L)
            .setParentKey(-1L)
            .setIndex(456L)
            .setStorageOrdinal(1001),
        new SuspensionBatchRecord()
            .setProcessInstanceKey(123L)
            .setProcessDefinitionKey(234L)
            .setElementInstanceKey(Long.MAX_VALUE - 1)
            .setParentKey(345L)
            .setIndex(Long.MAX_VALUE - 2)
            .setStorageOrdinal(1002));
  }

  /**
   * Converts a SNAKE_CASE string to CamelCase.
   *
   * @param snakeCase the input string in SNAKE_CASE format
   * @return the converted string in CamelCase format
   */
  private static String snakeCaseToCamelCase(final String snakeCase) {
    final StringBuilder result = new StringBuilder();
    boolean capitalizeNext = true;
    for (final char c : snakeCase.toLowerCase().toCharArray()) {
      if (c == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  private static Stream<Arguments> provideValueTypes() {
    return Arrays.stream(ValueType.values()).map(Arguments::of);
  }
}
