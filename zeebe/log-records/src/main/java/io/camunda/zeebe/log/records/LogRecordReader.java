/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.log.records;

import io.atomix.raft.storage.serializer.ApplicationEntryDecoder;
import io.atomix.raft.storage.serializer.EntryType;
import io.atomix.raft.storage.serializer.RaftLogEntryDecoder;
import io.camunda.zeebe.journal.file.RecordDataDecoder;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.sbe.MessageDecoderFlyweight;

/**
 * Decodes the records of a partition's log from the bytes its brokers replicate: a journal record
 * holds a Raft entry, an application entry holds application data, and application data is a
 * sequence of log stream frames, one per record.
 *
 * <p>It reads the journal's and Raft's messages with the decoders generated from their schemas in
 * zeebe-log-schemas, the same ones zeebe-journal and zeebe-atomix-cluster read them with, and reads
 * only the fields it needs. The decoders find each message by its header's block length, so it
 * reads messages that newer brokers extend. {@code LogRecordReaderTest} checks it against both
 * modules' serializers.
 */
public final class LogRecordReader {

  private static final int MIN_FRAME_LENGTH =
      DataFrameDescriptor.HEADER_LENGTH + LogEntryDescriptor.headerLength(0);

  private LogRecordReader() {}

  /**
   * The records of a journal record, as Raft replicates it: an SBE message header and {@code
   * RecordData}.
   */
  public static List<LogRecord> fromJournalRecord(
      final int partitionId, final DirectBuffer buffer) {
    final var recordData = new RecordDataDecoder();
    message(buffer, 0, recordData, "journal record");
    requireVarData(
        buffer,
        recordData.limit(),
        RecordDataDecoder.dataHeaderLength(),
        recordData::dataLength,
        "journal record's data");
    final var data = new UnsafeBuffer();
    recordData.wrapData(data);
    return fromRaftEntry(partitionId, data);
  }

  /**
   * The records of a Raft entry: an SBE message header and {@code RaftLogEntry}, followed by the
   * entry. Configuration and initial entries hold no records.
   */
  public static List<LogRecord> fromRaftEntry(final int partitionId, final DirectBuffer buffer) {
    final var raftLogEntry = new RaftLogEntryDecoder();
    message(buffer, 0, raftLogEntry, "Raft entry");
    switch (raftLogEntry.type()) {
      case ApplicationEntry -> {}
      case ConfigurationEntry, InitialEntry -> {
        return List.of();
      }
      default ->
          throw new MalformedLogEntryException(
              "Expected a Raft entry of type %d, %d or %d, but got type %d"
                  .formatted(
                      EntryType.ApplicationEntry.value(),
                      EntryType.ConfigurationEntry.value(),
                      EntryType.InitialEntry.value(),
                      raftLogEntry.typeRaw()));
    }
    final var applicationEntry = new ApplicationEntryDecoder();
    message(buffer, raftLogEntry.limit(), applicationEntry, "application entry");
    requireVarData(
        buffer,
        applicationEntry.limit(),
        ApplicationEntryDecoder.applicationDataHeaderLength(),
        applicationEntry::applicationDataLength,
        "application entry's data");
    final var data = new UnsafeBuffer();
    applicationEntry.wrapApplicationData(data);
    return fromApplicationData(partitionId, data);
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
      if (event.getMetadataLength() < MessageHeaderDecoder.ENCODED_LENGTH
          || metadataEnd > framedLength) {
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
   * Wraps the decoder around the message at the offset, after checking that it is the message the
   * decoder reads. All schemas share the message header of {@code common-types.xml}.
   */
  private static void message(
      final DirectBuffer buffer,
      final int offset,
      final MessageDecoderFlyweight decoder,
      final String message) {
    require(buffer, offset, MessageHeaderDecoder.ENCODED_LENGTH, "the " + message + "'s header");
    final var header = new MessageHeaderDecoder().wrap(buffer, offset);
    if (header.schemaId() != decoder.sbeSchemaId()
        || header.templateId() != decoder.sbeTemplateId()) {
      throw new MalformedLogEntryException(
          "Expected a %s (schema %d, template %d), but got schema %d, template %d"
              .formatted(
                  message,
                  decoder.sbeSchemaId(),
                  decoder.sbeTemplateId(),
                  header.schemaId(),
                  header.templateId()));
    }
    if (header.blockLength() < decoder.sbeBlockLength()) {
      throw new MalformedLogEntryException(
          "Expected a %s block of at least %d bytes, but got %d bytes"
              .formatted(message, decoder.sbeBlockLength(), header.blockLength()));
    }
    final int block = offset + header.encodedLength();
    require(buffer, block, header.blockLength(), "the " + message + "'s block");
    decoder.wrap(buffer, block, header.blockLength(), header.version());
  }

  private static void require(
      final DirectBuffer buffer, final int offset, final long length, final String what) {
    if (offset + length > buffer.capacity()) {
      throw new MalformedLogEntryException(
          "Expected %s of %d bytes at byte %d, but only %d bytes remain"
              .formatted(what, length, offset, Math.max(0, buffer.capacity() - offset)));
    }
  }

  /**
   * Checks that the variable length data after a message's block fits the buffer, which the
   * generated decoders do not.
   */
  private static void requireVarData(
      final DirectBuffer buffer,
      final int offset,
      final int headerLength,
      final IntSupplier length,
      final String data) {
    require(buffer, offset, headerLength, "the length of the " + data);
    require(
        buffer, offset + headerLength, Integer.toUnsignedLong(length.getAsInt()), "the " + data);
  }
}
