/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.RecordBatchSizePredicate;
import io.camunda.zeebe.stream.impl.records.RecordBatch;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import io.camunda.zeebe.stream.util.Records;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RecordBatchTest {

  private static final RecordType RECORD_TYPE = RecordType.COMMAND;
  private static final ProcessInstanceIntent INTENT = ProcessInstanceIntent.ACTIVATE_ELEMENT;
  private static final RejectionType REJECTION_TYPE = RejectionType.ALREADY_EXISTS;
  private static final String REJECTION_REASON = "broken somehow";
  private static final ValueType VALUE_TYPE = ValueType.PROCESS_INSTANCE;
  private static final RecordMetadata RECORD_METADATA =
      new RecordMetadata()
          .recordType(RECORD_TYPE)
          .intent(INTENT)
          .rejectionType(REJECTION_TYPE)
          .rejectionReason(REJECTION_REASON)
          .valueType(VALUE_TYPE);

  @Test
  void shouldAppendToRecordBatch() {
    // given
    final var recordBatch = new RecordBatch((count, size) -> true);
    final var processInstanceRecord = Records.processInstance(1);

    // when
    recordBatch.appendRecord(1, RECORD_METADATA, -1, processInstanceRecord);

    // then
    final var batchSize = recordBatch.getBatchSize();
    assertThat(batchSize).isGreaterThan(processInstanceRecord.getLength());

    assertThat(recordBatch).map(LogAppendEntry::key).containsOnly(1L);
    assertThat(recordBatch).map(LogAppendEntry::sourceIndex).containsOnly(-1);
    assertThat(recordBatch)
        .map(RecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getIntent)
        .containsOnly(INTENT);
    assertThat(recordBatch)
        .map(RecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getRecordType)
        .containsOnly(RECORD_TYPE);
    assertThat(recordBatch)
        .map(RecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getRejectionType)
        .containsOnly(REJECTION_TYPE);
    assertThat(recordBatch)
        .map(RecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getValueType)
        .containsOnly(VALUE_TYPE);
    assertThat(recordBatch)
        .map(RecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getRejectionReason)
        .containsOnly(REJECTION_REASON);
    assertThat(recordBatch).map(RecordBatchEntry::recordValue).containsOnly(processInstanceRecord);
  }

  @Test
  void shouldUseRecordSizePredicate() {
    // given
    final AtomicInteger batchEntryCount = new AtomicInteger(-1);
    final AtomicInteger batchSize = new AtomicInteger(-1);
    final var batchSizePredicate =
        new RecordBatchSizePredicate() {
          @Override
          public boolean test(final Integer count, final Integer size) {
            batchEntryCount.set(count);
            batchSize.set(size);
            return true;
          }
        };
    final var recordBatch = new RecordBatch(batchSizePredicate);
    final var processInstanceRecord = Records.processInstance(1);

    // when
    recordBatch.appendRecord(1, RECORD_METADATA, -1, processInstanceRecord);

    // then
    assertThat(recordBatch.getBatchSize()).isEqualTo(batchSize.get());
    assertThat(batchSize.get()).isGreaterThan(processInstanceRecord.getLength());
    assertThat(batchEntryCount.get()).isEqualTo(1);
  }

  @Test
  void shouldUpdateBatchEntryCountWhenUsingRecordSizePredicate() {
    // given
    final AtomicInteger batchEntryCount = new AtomicInteger(-1);
    final AtomicInteger batchSize = new AtomicInteger(-1);
    final var batchSizePredicate =
        new RecordBatchSizePredicate() {
          @Override
          public boolean test(final Integer count, final Integer size) {
            batchEntryCount.set(count);
            batchSize.set(size);
            return true;
          }
        };
    final var recordBatch = new RecordBatch(batchSizePredicate);
    final var processInstanceRecord = Records.processInstance(1);
    recordBatch.appendRecord(1, RECORD_METADATA, -1, processInstanceRecord);

    // when
    recordBatch.appendRecord(1, RECORD_METADATA, -1, processInstanceRecord);

    // then
    assertThat(recordBatch.getBatchSize()).isEqualTo(batchSize.get());
    assertThat(batchSize.get()).isGreaterThan(processInstanceRecord.getLength());
    assertThat(batchEntryCount.get()).isEqualTo(2);
  }

  @Test
  void shouldNotAppendToRecordBatchIfMaxSizeIsReached() {
    // given
    final var recordBatch = new RecordBatch((count, size) -> false);
    final var processInstanceRecord = Records.processInstance(1);

    // when
    final var either = recordBatch.appendRecord(1, RECORD_METADATA, -1, processInstanceRecord);

    // then
    assertThat(either.isLeft()).isTrue();
    assertThat(either.getLeft())
        .hasMessageContaining("Can't append entry")
        .hasMessageContaining("[ currentBatchEntryCount: 0, currentBatchSize: 0]");
  }

  @Test
  void shouldOnlyAddUntilMaxBatchSizeIsReached() {
    // given
    final var recordBatch = new RecordBatch((count, size) -> count < 2);
    final var processInstanceRecord = Records.processInstance(1);

    recordBatch.appendRecord(1, RECORD_METADATA, -1, processInstanceRecord);

    // when
    final var either = recordBatch.appendRecord(1, RECORD_METADATA, -1, processInstanceRecord);

    // then
    assertThat(either.isLeft()).isTrue();
    assertThat(either.getLeft())
        .hasMessageContaining("Can't append entry")
        .hasMessageContaining("[ currentBatchEntryCount: 1, currentBatchSize: 464]");
  }

  @Test
  void shouldReturnFalseIfRecordSizeDoesReachSizeLimit() {
    // given
    final var recordBatch = new RecordBatch((count, size) -> size < 100);

    // when
    final var canAppend = recordBatch.canAppendRecordOfLength(100);

    // then
    assertThat(canAppend).isFalse();
  }

  @Test
  void shouldReturnTrueIfRecordSizeDoesntReachSizeLimit() {
    // given
    final var recordBatch = new RecordBatch((count, size) -> size < 100);

    // when
    final var canAppend = recordBatch.canAppendRecordOfLength(99);

    // then
    assertThat(canAppend).isTrue();
  }

  @Test
  void shouldOnlyReturnTrueUntilMaxBatchSizeIsReached() {
    // given
    final var recordBatch = new RecordBatch((count, size) -> size < 465);
    final var processInstanceRecord = Records.processInstance(1);

    recordBatch.appendRecord(1, RECORD_METADATA, -1, processInstanceRecord);

    // when
    final var canAppend = recordBatch.canAppendRecordOfLength(recordBatch.getBatchSize());

    // then
    assertThat(canAppend).isFalse();
  }

  @Test
  void shouldOnlyReturnTrueUntilMaxCountIsReached() {
    // given
    final var recordBatch = new RecordBatch((count, size) -> count < 2);
    final var processInstanceRecord = Records.processInstance(1);

    recordBatch.appendRecord(1, RECORD_METADATA, -1, processInstanceRecord);

    // when
    final var canAppend = recordBatch.canAppendRecordOfLength(recordBatch.getBatchSize());

    // then
    assertThat(canAppend).isFalse();
  }
}
