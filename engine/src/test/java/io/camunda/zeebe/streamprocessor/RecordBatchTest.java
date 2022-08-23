/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.streamprocessor.records.ImmutableRecordBatchEntry;
import io.camunda.zeebe.streamprocessor.records.RecordBatch;
import io.camunda.zeebe.streamprocessor.records.RecordBatchSizePredicate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class RecordBatchTest {

  @Test
  public void shouldAppendToRecordBatch() {
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
  public void shouldUseRecordSizePredicate() {
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
  public void shouldUpdateBatchEntryCountWhenUsingRecordSizePredicate() {
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
  public void shouldNotAppendToRecordBatchIfMaxSizeIsReached() {
    // given
    final var recordBatch = new RecordBatch((count, size) -> false);
    final var processInstanceRecord = Records.processInstance(1);

    // expect
    assertThatThrownBy(
            () ->
                recordBatch.appendRecord(
                    1,
                    -1,
                    RecordType.COMMAND,
                    ProcessInstanceIntent.ACTIVATE_ELEMENT,
                    RejectionType.ALREADY_EXISTS,
                    "broken somehow",
                    ValueType.PROCESS_INSTANCE,
                    processInstanceRecord))
        .hasMessageContaining("Can't append entry")
        .hasMessageContaining("[ currentBatchEntryCount: 0, currentBatchSize: 0]");
  }

  @Test
  public void shouldOnlyAddUntilMaxBatchSizeIsReached() {
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

    // expect
    assertThatThrownBy(
            () ->
                recordBatch.appendRecord(
                    1,
                    -1,
                    RecordType.COMMAND,
                    ProcessInstanceIntent.ACTIVATE_ELEMENT,
                    RejectionType.ALREADY_EXISTS,
                    "broken somehow",
                    ValueType.PROCESS_INSTANCE,
                    processInstanceRecord))
        .hasMessageContaining("Can't append entry")
        .hasMessageContaining("[ currentBatchEntryCount: 1, currentBatchSize: 249]");
  }
}
