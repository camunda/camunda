/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.api.records.ImmutableRecordBatchEntry;
import io.camunda.zeebe.engine.api.records.RecordBatch;
import io.camunda.zeebe.engine.api.records.RecordBatchSizePredicate;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RecordBatchTest {

  @Test
  void shouldAppendToRecordBatch() {
    // given
    final var recordBatch = new RecordBatch((count, size) -> true);
    final var processInstanceRecord = Records.processInstance(1);

    // when
    recordBatch.appendRecord(
        1,
        -1,
        RecordType.COMMAND,
        ProcessInstanceIntent.ACTIVATE_ELEMENT,
        RejectionType.ALREADY_EXISTS,
        "broken somehow",
        ValueType.PROCESS_INSTANCE,
        processInstanceRecord);

    // then
    final var batchSize = recordBatch.getBatchSize();
    assertThat(batchSize).isGreaterThan(processInstanceRecord.getLength());

    assertThat(recordBatch).map(ImmutableRecordBatchEntry::key).containsOnly(1L);
    assertThat(recordBatch).map(ImmutableRecordBatchEntry::sourceIndex).containsOnly(-1);
    assertThat(recordBatch)
        .map(ImmutableRecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getIntent)
        .containsOnly(ProcessInstanceIntent.ACTIVATE_ELEMENT);
    assertThat(recordBatch)
        .map(ImmutableRecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getRecordType)
        .containsOnly(RecordType.COMMAND);
    assertThat(recordBatch)
        .map(ImmutableRecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getRejectionType)
        .containsOnly(RejectionType.ALREADY_EXISTS);
    assertThat(recordBatch)
        .map(ImmutableRecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getValueType)
        .containsOnly(ValueType.PROCESS_INSTANCE);
    assertThat(recordBatch)
        .map(ImmutableRecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getRejectionReason)
        .containsOnly("broken somehow");
    assertThat(recordBatch)
        .map(ImmutableRecordBatchEntry::recordValue)
        .containsOnly(processInstanceRecord);
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
    recordBatch.appendRecord(
        1,
        -1,
        RecordType.COMMAND,
        ProcessInstanceIntent.ACTIVATE_ELEMENT,
        RejectionType.ALREADY_EXISTS,
        "broken somehow",
        ValueType.PROCESS_INSTANCE,
        processInstanceRecord);

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
    recordBatch.appendRecord(
        1,
        -1,
        RecordType.COMMAND,
        ProcessInstanceIntent.ACTIVATE_ELEMENT,
        RejectionType.ALREADY_EXISTS,
        "broken somehow",
        ValueType.PROCESS_INSTANCE,
        processInstanceRecord);

    // when
    recordBatch.appendRecord(
        1,
        -1,
        RecordType.COMMAND,
        ProcessInstanceIntent.ACTIVATE_ELEMENT,
        RejectionType.ALREADY_EXISTS,
        "broken somehow",
        ValueType.PROCESS_INSTANCE,
        processInstanceRecord);

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
    final var either =
        recordBatch.appendRecord(
            1,
            -1,
            RecordType.COMMAND,
            ProcessInstanceIntent.ACTIVATE_ELEMENT,
            RejectionType.ALREADY_EXISTS,
            "broken somehow",
            ValueType.PROCESS_INSTANCE,
            processInstanceRecord);

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

    recordBatch.appendRecord(
        1,
        -1,
        RecordType.COMMAND,
        ProcessInstanceIntent.ACTIVATE_ELEMENT,
        RejectionType.ALREADY_EXISTS,
        "broken somehow",
        ValueType.PROCESS_INSTANCE,
        processInstanceRecord);

    // when
    final var either =
        recordBatch.appendRecord(
            1,
            -1,
            RecordType.COMMAND,
            ProcessInstanceIntent.ACTIVATE_ELEMENT,
            RejectionType.ALREADY_EXISTS,
            "broken somehow",
            ValueType.PROCESS_INSTANCE,
            processInstanceRecord);

    // then
    assertThat(either.isLeft()).isTrue();
    assertThat(either.getLeft())
        .hasMessageContaining("Can't append entry")
        .hasMessageContaining("[ currentBatchEntryCount: 1, currentBatchSize: 237]");
  }

  @Test
  void shouldReturnFalseIfRecordSizeDoesReachSizelimit() {
    // given
    final var recordBatch = new RecordBatch((count, size) -> size < 100);

    // when
    final var canAppend = recordBatch.canAppendRecordOfLength(100);

    // then
    assertThat(canAppend).isFalse();
  }

  @Test
  void shouldReturnTrueIfRecordSizeDoesntReachSizelimit() {
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
    final var recordBatch = new RecordBatch((count, size) -> size < 300);
    final var processInstanceRecord = Records.processInstance(1);

    recordBatch.appendRecord(
        1,
        -1,
        RecordType.COMMAND,
        ProcessInstanceIntent.ACTIVATE_ELEMENT,
        RejectionType.ALREADY_EXISTS,
        "broken somehow",
        ValueType.PROCESS_INSTANCE,
        processInstanceRecord);

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

    recordBatch.appendRecord(
        1,
        -1,
        RecordType.COMMAND,
        ProcessInstanceIntent.ACTIVATE_ELEMENT,
        RejectionType.ALREADY_EXISTS,
        "broken somehow",
        ValueType.PROCESS_INSTANCE,
        processInstanceRecord);

    // when
    final var canAppend = recordBatch.canAppendRecordOfLength(recordBatch.getBatchSize());

    // then
    assertThat(canAppend).isFalse();
  }
}
