/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.serializer;

import static io.camunda.zeebe.logstreams.util.TestEntry.TestEntryAssert.assertThatEntry;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.log.records.LogRecord;
import io.camunda.zeebe.log.records.LogRecordReader;
import io.camunda.zeebe.log.records.LoggedEventImpl;
import io.camunda.zeebe.logstreams.impl.log.SequencedBatch;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.util.TestEntry;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class SequencedBatchSerializerTest {
  @Test
  void serializedBatchIsReadableAsLoggedEvents() {
    // given
    final var entries = List.of(TestEntry.ofKey(1), TestEntry.ofKey(2));
    final var batch = new SequencedBatch(0, 1, -1, entries);

    // when
    final var serialized = SequencedBatchSerializer.serializeBatch(batch);

    // then
    final var firstEvent = new LoggedEventImpl();
    firstEvent.wrap(new UnsafeBuffer(serialized), 0);
    assertThatEntry(entries.get(0)).matchesLoggedEvent(firstEvent);
    assertThat(firstEvent.getPosition()).isEqualTo(1);
    assertThat(firstEvent.getSourceEventPosition()).isEqualTo(-1);

    final var secondEvent = new LoggedEventImpl();
    secondEvent.wrap(new UnsafeBuffer(serialized), firstEvent.getLength());
    assertThatEntry(entries.get(1)).matchesLoggedEvent(secondEvent);
    assertThat(secondEvent.getPosition()).isEqualTo(2);
    assertThat(secondEvent.getSourceEventPosition()).isEqualTo(-1);
  }

  @Test
  void serializedBatchIsReadableAsRecords() {
    // given
    final var entries = List.of(variable(1, "a"), variable(2, "b"));
    final var batch = new SequencedBatch(123, 10, -1, entries);

    // when
    final var serialized = new UnsafeBuffer(SequencedBatchSerializer.serializeBatch(batch));
    final var records = LogRecordReader.fromApplicationData(1, serialized);

    // then
    assertThat(records)
        .map(record -> ((LogRecord.Known) record).record())
        .extracting(Record::getPosition, Record::getKey, Record::getTimestamp, Record::getValue)
        .containsExactly(
            tuple(10L, 1L, 123L, entries.get(0).recordValue()),
            tuple(11L, 2L, 123L, entries.get(1).recordValue()));
  }

  private static LogAppendEntry variable(final long key, final String name) {
    return TestEntry.builder()
        .withKey(key)
        .withRecordValue(
            new VariableRecord()
                .setName(wrapString(name))
                .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("\"value\"")))
                .setScopeKey(key)
                .setProcessInstanceKey(key)
                .setProcessDefinitionKey(key)
                .setBpmnProcessId(wrapString("process")))
        .withRecordMetadata(
            new RecordMetadata()
                .recordType(RecordType.EVENT)
                .valueType(ValueType.VARIABLE)
                .intent(VariableIntent.CREATED))
        .build();
  }
}
