/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.log.records;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.raft.storage.log.entry.InitialEntry;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.atomix.raft.storage.serializer.RaftEntrySBESerializer;
import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class LogRecordReaderTest {

  private static final int PARTITION_ID = 3;
  private static final int UNKNOWN_VALUE_TYPE = 250;

  @Property(tries = 200)
  void shouldReadTheRecordsOfAJournalRecord(@ForAll("variables") final List<Variable> variables) {
    // given
    final var journalRecord = journalRecord(raftEntry(applicationData(variables, 100)));

    // when
    final var records = LogRecordReader.fromJournalRecord(PARTITION_ID, journalRecord);

    // then
    assertThat(records).hasSize(variables.size());
    for (int i = 0; i < variables.size(); i++) {
      final var variable = variables.get(i);
      final var record = ((LogRecord.Known) records.get(i)).record();
      assertThat(record.getPosition()).isEqualTo(100 + i);
      assertThat(record.getPartitionId()).isEqualTo(PARTITION_ID);
      assertThat(record.getKey()).isEqualTo(variable.key());
      assertThat(record.getTimestamp()).isEqualTo(variable.timestamp());
      assertThat(record.getRecordType()).isEqualTo(RecordType.EVENT);
      assertThat(record.getValueType()).isEqualTo(ValueType.VARIABLE);
      assertThat(record.getIntent()).isEqualTo(VariableIntent.CREATED);
      assertThat(record.getValue()).isEqualTo(variable.record());
    }
  }

  @Test
  void shouldReadTheSameRecordsFromEachLayer() {
    // given
    final var variables = List.of(new Variable(1, 10, "a", "1"), new Variable(2, 20, "b", "2"));
    final var applicationData = applicationData(variables, 1);
    final var raftEntry = raftEntry(applicationData);

    // when
    final var fromData = LogRecordReader.fromApplicationData(PARTITION_ID, applicationData);
    final var fromRaft = LogRecordReader.fromRaftEntry(PARTITION_ID, raftEntry);
    final var fromJournal =
        LogRecordReader.fromJournalRecord(PARTITION_ID, journalRecord(raftEntry));

    // then
    assertThat(json(fromData)).hasSize(2).isEqualTo(json(fromRaft)).isEqualTo(json(fromJournal));
  }

  private static List<String> json(final List<LogRecord> records) {
    return records.stream().map(record -> ((LogRecord.Known) record).record().toJson()).toList();
  }

  @Test
  void shouldReportRecordsOfUnknownValueTypes() {
    // given
    final var variables = List.of(new Variable(1, 10, "a", "1"), new Variable(2, 20, "b", "2"));
    final var applicationData = applicationData(variables, 7);
    setValueType(applicationData, 0, UNKNOWN_VALUE_TYPE);

    // when
    final var records = LogRecordReader.fromApplicationData(PARTITION_ID, applicationData);

    // then
    assertThat(records.getFirst())
        .isEqualTo(
            new LogRecord.Unknown(7, 1, 10, UNKNOWN_VALUE_TYPE, VariableIntent.CREATED.value()));
    assertThat(records.get(1)).isInstanceOf(LogRecord.Known.class);
    assertThat(records).extracting(LogRecord::position).containsExactly(7L, 8L);
  }

  @Test
  void shouldReadNoRecordsFromAnInitialEntry() {
    // given
    final var serializer = new RaftEntrySBESerializer();
    final var buffer = new ExpandableArrayBuffer();
    final int length = serializer.writeInitialEntry(4, new InitialEntry(), buffer, 0);

    // when
    final var records =
        LogRecordReader.fromJournalRecord(
            PARTITION_ID, journalRecord(new UnsafeBuffer(buffer, 0, length)));

    // then
    assertThat(records).isEmpty();
  }

  @Test
  void shouldRejectARaftEntryOfAnUnknownType() {
    // given
    final var raftEntry = raftEntry(applicationData(List.of(new Variable(1, 10, "a", "1")), 1));
    raftEntry.putByte(8 + 8, (byte) 9);

    // when, then
    assertThatThrownBy(() -> LogRecordReader.fromRaftEntry(PARTITION_ID, raftEntry))
        .isInstanceOf(MalformedLogEntryException.class)
        .hasMessage("Expected a Raft entry of type 0, 1 or 2, but got type 9");
  }

  @Test
  void shouldRejectAJournalRecordOfAnotherSchema() {
    // given
    final var raftEntry = raftEntry(applicationData(List.of(new Variable(1, 10, "a", "1")), 1));

    // when, then
    assertThatThrownBy(() -> LogRecordReader.fromJournalRecord(PARTITION_ID, raftEntry))
        .isInstanceOf(MalformedLogEntryException.class)
        .hasMessage(
            "Expected a journal record (schema 7, template 2), but got schema 8, template 1");
  }

  @Property(tries = 100)
  void shouldRejectTruncatedJournalRecords(
      @ForAll("variables") final List<Variable> variables, @ForAll final double cut) {
    // given
    final var journalRecord = journalRecord(raftEntry(applicationData(variables, 1)));
    final int length = (int) (journalRecord.capacity() * Math.abs(cut % 1));
    final var truncated = new UnsafeBuffer(journalRecord, 0, length);

    // when, then
    assertThatThrownBy(() -> LogRecordReader.fromJournalRecord(PARTITION_ID, truncated))
        .isInstanceOf(MalformedLogEntryException.class);
  }

  @Provide
  Arbitrary<List<Variable>> variables() {
    final var variable =
        Combinators.combine(
                Arbitraries.longs().greaterOrEqual(0),
                Arbitraries.longs().between(0, Long.MAX_VALUE / 2),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                Arbitraries.strings().ofMaxLength(200))
            .as(Variable::new);
    return variable.list().ofMinSize(1).ofMaxSize(20);
  }

  /** Log stream frames for the variables, as the log stream writes them, from the position. */
  private static MutableDirectBuffer applicationData(
      final List<Variable> variables, final long firstPosition) {
    final var buffer = new ExpandableArrayBuffer();
    int offset = 0;
    for (int i = 0; i < variables.size(); i++) {
      final var variable = variables.get(i);
      final var metadata =
          new RecordMetadata()
              .recordType(RecordType.EVENT)
              .valueType(ValueType.VARIABLE)
              .intent(VariableIntent.CREATED);
      final int message = DataFrameDescriptor.messageOffset(offset);
      LogEntryDescriptor.setVersion(buffer, message);
      LogEntryDescriptor.skipProcessing(buffer, message, false);
      LogEntryDescriptor.zeroReserved(buffer, message);
      LogEntryDescriptor.setPosition(buffer, message, firstPosition + i);
      LogEntryDescriptor.setSourceEventPosition(buffer, message, -1);
      LogEntryDescriptor.setKey(buffer, message, variable.key());
      LogEntryDescriptor.setTimestamp(buffer, message, variable.timestamp());
      final int metadataLength = metadata.write(buffer, LogEntryDescriptor.metadataOffset(message));
      LogEntryDescriptor.setMetadataLength(buffer, message, metadataLength);
      final int valueLength =
          variable.record().write(buffer, LogEntryDescriptor.valueOffset(message, metadataLength));
      final int framedLength =
          DataFrameDescriptor.framedLength(
              LogEntryDescriptor.headerLength(metadataLength) + valueLength);
      DataFrameDescriptor.write(buffer, offset, framedLength);
      offset += DataFrameDescriptor.alignedLength(framedLength);
    }
    buffer.checkLimit(offset);
    return new UnsafeBuffer(buffer, 0, offset);
  }

  private static void setValueType(
      final MutableDirectBuffer applicationData, final int frameOffset, final int valueType) {
    final var event = new LoggedEventImpl();
    event.wrap(applicationData, frameOffset);
    // after the 8-byte message header, valueType is byte 15 of RecordMetadata's block
    applicationData.putByte(event.getMetadataOffset() + 8 + 15, (byte) valueType);
  }

  private static MutableDirectBuffer raftEntry(final DirectBuffer applicationData) {
    final var entry = new SerializedApplicationEntry(1, 2, applicationData);
    final var serializer = new RaftEntrySBESerializer();
    final var buffer = new ExpandableArrayBuffer();
    final int length = serializer.writeApplicationEntry(4, entry, buffer, 0);
    return new UnsafeBuffer(buffer, 0, length);
  }

  private static DirectBuffer journalRecord(final DirectBuffer raftEntry) {
    // the serializer checks the capacity instead of expanding the buffer
    final var buffer = new UnsafeBuffer(new byte[raftEntry.capacity() + 64]);
    final BufferWriter data =
        new BufferWriter() {
          @Override
          public int getLength() {
            return raftEntry.capacity();
          }

          @Override
          public int write(final MutableDirectBuffer target, final int offset) {
            target.putBytes(offset, raftEntry, 0, raftEntry.capacity());
            return raftEntry.capacity();
          }
        };
    final int length = new SBESerializer().writeData(5, 1, data, buffer, 0).get();
    return new UnsafeBuffer(buffer, 0, length);
  }

  record Variable(long key, long timestamp, String name, String value) {

    VariableRecord record() {
      return new VariableRecord()
          .setName(wrapString(name))
          .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack('"' + escape(value) + '"')))
          .setScopeKey(key)
          .setProcessInstanceKey(key)
          .setProcessDefinitionKey(key)
          .setBpmnProcessId(wrapString("process"));
    }

    private static String escape(final String value) {
      final var escaped = new StringBuilder();
      for (final char c : value.toCharArray()) {
        if (c == '"' || c == '\\' || c < 0x20) {
          escaped.append("\\u%04x".formatted((int) c));
        } else {
          escaped.append(c);
        }
      }
      return escaped.toString();
    }
  }
}
