/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.log.records;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Decodes the records of a partition's log from the bytes its brokers replicate: a journal record
 * holds a Raft entry, an application entry holds application data, and application data is a
 * sequence of log stream frames, one per record.
 *
 * <p>It reads the journal's and Raft's SBE messages by hand. Their generated decoders live in
 * zeebe-journal and zeebe-atomix-cluster, which bring the broker's storage and networking with
 * them. It reads only the fields it needs and finds each message by its header's block length, as
 * SBE decoders do, so it reads messages that newer brokers extend. {@code LogRecordReaderTest}
 * checks it against both modules' serializers.
 */
public final class LogRecordReader {

  private static final int MESSAGE_HEADER_LENGTH = 8;
  private static final int VAR_DATA_HEADER_LENGTH = 4;

  // journal-schema.xml: RecordData is index (uint64) and asqn (int64), then data
  private static final int JOURNAL_SCHEMA_ID = 7;
  private static final int RECORD_DATA_TEMPLATE_ID = 2;
  private static final int RECORD_DATA_BLOCK_LENGTH = 16;

  // raft-entry-schema.xml: RaftLogEntry is term (uint64) and type (uint8), then the entry itself;
  // ApplicationEntry is lowestAsqn and highestAsqn (uint64), then applicationData
  private static final int RAFT_SCHEMA_ID = 8;
  private static final int RAFT_LOG_ENTRY_TEMPLATE_ID = 1;
  private static final int RAFT_LOG_ENTRY_BLOCK_LENGTH = 9;
  private static final int RAFT_LOG_ENTRY_TYPE_OFFSET = 8;
  private static final int APPLICATION_ENTRY_TYPE = 0;
  private static final int CONFIGURATION_ENTRY_TYPE = 1;
  private static final int INITIAL_ENTRY_TYPE = 2;
  private static final int APPLICATION_ENTRY_TEMPLATE_ID = 2;
  private static final int APPLICATION_ENTRY_BLOCK_LENGTH = 16;

  private static final int MIN_FRAME_LENGTH =
      DataFrameDescriptor.HEADER_LENGTH + LogEntryDescriptor.headerLength(0);

  private LogRecordReader() {}

  /**
   * The records of a journal record, as Raft replicates it: an SBE message header and {@code
   * RecordData}.
   */
  public static List<LogRecord> fromJournalRecord(
      final int partitionId, final DirectBuffer buffer) {
    final var recordData =
        message(
            buffer,
            0,
            JOURNAL_SCHEMA_ID,
            RECORD_DATA_TEMPLATE_ID,
            RECORD_DATA_BLOCK_LENGTH,
            "journal record");
    return fromRaftEntry(partitionId, varData(buffer, recordData, "journal record's data"));
  }

  /**
   * The records of a Raft entry: an SBE message header and {@code RaftLogEntry}, followed by the
   * entry. Configuration and initial entries hold no records.
   */
  public static List<LogRecord> fromRaftEntry(final int partitionId, final DirectBuffer buffer) {
    final var raftLogEntry =
        message(
            buffer,
            0,
            RAFT_SCHEMA_ID,
            RAFT_LOG_ENTRY_TEMPLATE_ID,
            RAFT_LOG_ENTRY_BLOCK_LENGTH,
            "Raft entry");
    final int type = buffer.getByte(raftLogEntry + RAFT_LOG_ENTRY_TYPE_OFFSET) & 0xFF;
    if (type == CONFIGURATION_ENTRY_TYPE || type == INITIAL_ENTRY_TYPE) {
      return List.of();
    }
    if (type != APPLICATION_ENTRY_TYPE) {
      throw new MalformedLogEntryException(
          "Expected a Raft entry of type %d, %d or %d, but got type %d"
              .formatted(
                  APPLICATION_ENTRY_TYPE, CONFIGURATION_ENTRY_TYPE, INITIAL_ENTRY_TYPE, type));
    }
    final var applicationEntry =
        message(
            buffer,
            end(buffer, raftLogEntry),
            RAFT_SCHEMA_ID,
            APPLICATION_ENTRY_TEMPLATE_ID,
            APPLICATION_ENTRY_BLOCK_LENGTH,
            "application entry");
    return fromApplicationData(
        partitionId, varData(buffer, applicationEntry, "application entry's data"));
  }

  /** The records of an application entry's data: log stream frames, each aligned to 8 bytes. */
  public static List<LogRecord> fromApplicationData(
      final int partitionId, final DirectBuffer buffer) {
    final var records = new ArrayList<LogRecord>();
    final var event = new LoggedEventImpl();
    int offset = 0;
    while (offset < buffer.capacity()) {
      require(buffer, offset, DataFrameDescriptor.HEADER_LENGTH, "a log frame's header");
      final int framedLength =
          buffer.getInt(DataFrameDescriptor.lengthOffset(offset), Protocol.ENDIANNESS);
      if (framedLength < MIN_FRAME_LENGTH) {
        throw new MalformedLogEntryException(
            "Expected a log frame of at least %d bytes at byte %d, but got %d bytes"
                .formatted(MIN_FRAME_LENGTH, offset, framedLength));
      }
      require(buffer, offset, framedLength, "a log frame");
      event.wrap(buffer, offset);
      final int metadataEnd =
          DataFrameDescriptor.HEADER_LENGTH
              + LogEntryDescriptor.headerLength(event.getMetadataLength());
      if (event.getMetadataLength() < MESSAGE_HEADER_LENGTH || metadataEnd > framedLength) {
        throw new MalformedLogEntryException(
            "Expected the metadata of the log frame at byte %d to fit its %d bytes, but it has %d bytes"
                .formatted(offset, framedLength, event.getMetadataLength()));
      }
      records.add(record(partitionId, event));
      offset += event.getFragmentLength();
    }
    return records;
  }

  private static LogRecord record(final int partitionId, final LoggedEventImpl event) {
    final var header =
        new MessageHeaderDecoder().wrap(event.getMetadata(), event.getMetadataOffset());
    final var metadata =
        new RecordMetadataDecoder()
            .wrap(
                event.getMetadata(),
                event.getMetadataOffset() + header.encodedLength(),
                header.blockLength(),
                header.version());
    final var valueType = metadata.valueType();
    if (valueType == ValueType.SBE_UNKNOWN
        || valueType == ValueType.NULL_VAL
        || UnifiedRecordValue.fromValueType(valueType) == null) {
      return new LogRecord.Unknown(
          event.getPosition(),
          event.getKey(),
          event.getTimestamp(),
          metadata.valueTypeRaw(),
          metadata.intent());
    }
    return new LogRecord.Known(CopiedRecords.createCopiedRecord(partitionId, event));
  }

  /**
   * Checks the SBE message header at the offset and returns the offset of the message's block,
   * whose length the header gives.
   */
  private static int message(
      final DirectBuffer buffer,
      final int offset,
      final int schemaId,
      final int templateId,
      final int minBlockLength,
      final String message) {
    require(buffer, offset, MESSAGE_HEADER_LENGTH, "the " + message + "'s header");
    final int actualTemplateId = uint16(buffer, offset + 2);
    final int actualSchemaId = uint16(buffer, offset + 4);
    if (actualSchemaId != schemaId || actualTemplateId != templateId) {
      throw new MalformedLogEntryException(
          "Expected a %s (schema %d, template %d), but got schema %d, template %d"
              .formatted(message, schemaId, templateId, actualSchemaId, actualTemplateId));
    }
    final int blockLength = uint16(buffer, offset);
    if (blockLength < minBlockLength) {
      throw new MalformedLogEntryException(
          "Expected a %s block of at least %d bytes, but got %d bytes"
              .formatted(message, minBlockLength, blockLength));
    }
    final int block = offset + MESSAGE_HEADER_LENGTH;
    require(buffer, block, blockLength, "the " + message + "'s block");
    return block;
  }

  /** The offset after the block of the message whose block starts at the offset. */
  private static int end(final DirectBuffer buffer, final int block) {
    return block + uint16(buffer, block - MESSAGE_HEADER_LENGTH);
  }

  private static DirectBuffer varData(
      final DirectBuffer buffer, final int block, final String data) {
    final int offset = end(buffer, block);
    require(buffer, offset, VAR_DATA_HEADER_LENGTH, "the length of the " + data);
    final long length = Integer.toUnsignedLong(buffer.getInt(offset, LITTLE_ENDIAN));
    require(buffer, offset + VAR_DATA_HEADER_LENGTH, length, "the " + data);
    return new UnsafeBuffer(buffer, offset + VAR_DATA_HEADER_LENGTH, (int) length);
  }

  private static void require(
      final DirectBuffer buffer, final int offset, final long length, final String what) {
    if (offset + length > buffer.capacity()) {
      throw new MalformedLogEntryException(
          "Expected %s of %d bytes at byte %d, but only %d bytes remain"
              .formatted(what, length, offset, Math.max(0, buffer.capacity() - offset)));
    }
  }

  private static int uint16(final DirectBuffer buffer, final int offset) {
    return buffer.getShort(offset, LITTLE_ENDIAN) & 0xFFFF;
  }
}
