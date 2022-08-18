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
import io.camunda.zeebe.streamprocessor.records.RecordBatch;
import io.camunda.zeebe.streamprocessor.records.UnmodifiableRecordBatchEntry;
import org.junit.jupiter.api.Test;

public class RecordBatchTest {

  @Test
  public void shouldAppendToRecordBatch() {
    // given
    final var recordBatch = new RecordBatch(1024);
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

    assertThat(recordBatch).map(UnmodifiableRecordBatchEntry::key).containsOnly(1L);
    assertThat(recordBatch).map(UnmodifiableRecordBatchEntry::sourceIndex).containsOnly(-1);
    assertThat(recordBatch)
        .map(UnmodifiableRecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getIntent)
        .containsOnly(ProcessInstanceIntent.ACTIVATE_ELEMENT);
    assertThat(recordBatch)
        .map(UnmodifiableRecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getRecordType)
        .containsOnly(RecordType.COMMAND);
    assertThat(recordBatch)
        .map(UnmodifiableRecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getRejectionType)
        .containsOnly(RejectionType.ALREADY_EXISTS);
    assertThat(recordBatch)
        .map(UnmodifiableRecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getValueType)
        .containsOnly(ValueType.PROCESS_INSTANCE);
    assertThat(recordBatch)
        .map(UnmodifiableRecordBatchEntry::recordMetadata)
        .map(RecordMetadata::getRejectionReason)
        .containsOnly("broken somehow");
    assertThat(recordBatch)
        .map(UnmodifiableRecordBatchEntry::recordValue)
        .containsOnly(processInstanceRecord);
  }

  @Test
  public void shouldNotAppendToRecordBatchIfMaxSizeIsReached() {
    // given
    final var maxBatchSize = 100; // bytes
    final var recordBatch = new RecordBatch(maxBatchSize);
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
        .hasMessageContaining("Batch would reach his maxBatchSize ");
  }
}
