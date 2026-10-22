/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RecordDecoderTest {

  @TempDir Path directory;
  private TestJournal journal;

  @BeforeEach
  void setUp() {
    journal = new TestJournal(directory);
  }

  @AfterEach
  void tearDown() {
    journal.close();
  }

  @Test
  void shouldDecodeTheRecordsThatTheLogStreamWrote() {
    // given
    journal.initial();
    journal.jobs(2);
    final var entry =
        journal.records(
            List.of(
                LogAppendEntry.of(
                    42,
                    new RecordMetadata()
                        .recordType(RecordType.EVENT)
                        .valueType(ValueType.PROCESS_INSTANCE)
                        .intent(ProcessInstanceIntent.ELEMENT_ACTIVATING),
                    new ProcessInstanceRecord()
                        .setBpmnProcessId("order")
                        .setElementId("ship")
                        .setProcessInstanceKey(7)),
                LogAppendEntry.of(
                    43,
                    new RecordMetadata()
                        .recordType(RecordType.COMMAND)
                        .valueType(ValueType.JOB)
                        .intent(JobIntent.COMPLETE),
                    new JobRecord().setType("ship-it").setRetries(2))));

    // when
    final var records = RecordDecoder.decode(entry, 3);

    // then
    assertThat(records).extracting(Record::getPosition).containsExactly(3L, 4L);
    assertThat(records).extracting(Record::getPartitionId).containsOnly(3);
    assertThat(records).extracting(Record::getSourceRecordPosition).containsOnly(-1L);
    final var instance = records.get(0);
    assertThat(instance.getKey()).isEqualTo(42);
    assertThat(instance.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(instance.getValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
    assertThat(instance.getIntent()).isEqualTo(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    assertThat(instance.getValue())
        .isInstanceOfSatisfying(
            ProcessInstanceRecordValue.class,
            value -> {
              assertThat(value.getBpmnProcessId()).isEqualTo("order");
              assertThat(value.getElementId()).isEqualTo("ship");
              assertThat(value.getProcessInstanceKey()).isEqualTo(7);
            });
    final var job = records.get(1);
    assertThat(job.getKey()).isEqualTo(43);
    assertThat(job.getRecordType()).isEqualTo(RecordType.COMMAND);
    assertThat(job.getIntent()).isEqualTo(JobIntent.COMPLETE);
    assertThat(job.getTimestamp()).isEqualTo(instance.getTimestamp()).isPositive();
    assertThat(job.getValue())
        .isInstanceOfSatisfying(
            JobRecordValue.class,
            value -> {
              assertThat(value.getType()).isEqualTo("ship-it");
              assertThat(value.getRetries()).isEqualTo(2);
            });
  }

  @Test
  void shouldDecodeNothingFromAnEntryWithoutRecords() {
    // given
    final var entry = journal.initial();

    // when
    final var records = RecordDecoder.decode(entry, 1);

    // then
    assertThat(records).isEmpty();
  }

  @Test
  void shouldRejectDataThatIsNoLogBatch() {
    // given
    final var entry = journal.garbage(2);

    // when / then
    assertThatThrownBy(() -> RecordDecoder.decode(entry, 1))
        .isInstanceOf(InvalidRecordBatchException.class)
        .hasMessage(
            "Expected a logged event of 1 to 64 bytes at byte 0 of entry 1's application data, but got 0");
  }
}
