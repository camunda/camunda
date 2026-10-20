/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.InvalidEntry;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.CRC32C;

/**
 * One journal entry, as the broker's journal hands it out: its {@code RecordData} message, which
 * Raft replicates unchanged, and that message's checksum. Its index and positions are read from the
 * message, so they always agree with it. It keeps the bytes without copying them, and compares them
 * by content.
 *
 * <p>It reads the SBE messages by hand, not with C8's generated decoders. Those live in {@code
 * zeebe-journal} and {@code zeebe-atomix-cluster}, which bring 40 and 67 runtime jars at 8.10.5
 * (Netty, Jackson, Guava, ...) that the consumer SDK shouldn't carry, and they aren't a published
 * API. Generating our own from C8's schemas doesn't work either: the packaged schemas include
 * {@code common-types.xml} by a path into camunda's source tree. So this reads only the fields it
 * needs, and finds each message by its header's block length, as SBE decoders do. {@code EntryTest}
 * checks it against C8's decoders, so a schema change shows when {@code version.camunda} is bumped.
 *
 * <p>In the long run, C8 could publish the journal's and Raft's entry codecs in a small artifact
 * that needs only Agrona, and this class would use them instead.
 */
public record Entry(long checksum, byte[] serializedRecord) {

  /** Zeebe's {@code ASQN_IGNORE}: no position. */
  public static final long NO_POSITION = -1;

  static final int SBE_HEADER_LENGTH = 8;
  static final int JOURNAL_SCHEMA_ID = 7;
  static final int RECORD_DATA_TEMPLATE_ID = 2;
  private static final int RAFT_SCHEMA_ID = 8;
  private static final int RAFT_LOG_ENTRY_TEMPLATE_ID = 1;
  private static final int RAFT_APPLICATION_ENTRY_TEMPLATE_ID = 2;
  private static final int RAFT_APPLICATION_ENTRY_TYPE = 0;
  private static final int INDEX_OFFSET = SBE_HEADER_LENGTH;
  private static final int ASQN_OFFSET = SBE_HEADER_LENGTH + 8;

  /**
   * Takes a journal record's {@code checksum()} and {@code serializedRecord()}, and checks them.
   */
  public Entry {
    final var crc = new CRC32C();
    crc.update(serializedRecord);
    if (crc.getValue() != checksum) {
      throw new InvalidEntry.ChecksumMismatch(checksum, crc.getValue());
    }
    final var record = ByteBuffer.wrap(serializedRecord).order(ByteOrder.LITTLE_ENDIAN);
    final int recordBlock =
        header(record, 0, JOURNAL_SCHEMA_ID, RECORD_DATA_TEMPLATE_ID, 16, "RecordData");
    final long index = record.getLong(INDEX_OFFSET);
    final long asqn = record.getLong(ASQN_OFFSET);
    if (index < 1) {
      throw new InvalidEntry.Malformed(
          "Expected a journal entry's index to be at least 1, but got " + index);
    }
    final int lengthOffset = SBE_HEADER_LENGTH + recordBlock;
    final int raftEntryOffset = lengthOffset + 4;
    if (raftEntryOffset > serializedRecord.length) {
      throw new InvalidEntry.Malformed(
          "Expected journal entry %d to hold at least %d bytes, but got %d"
              .formatted(index, raftEntryOffset, serializedRecord.length));
    }
    final long raftEntryLength = Integer.toUnsignedLong(record.getInt(lengthOffset));
    if (raftEntryLength != serializedRecord.length - raftEntryOffset) {
      throw new InvalidEntry.Malformed(
          "Expected journal entry %d's Raft entry to be %d bytes, as the entry says, but got %d"
              .formatted(index, raftEntryLength, serializedRecord.length - raftEntryOffset));
    }

    final var raft = raftEntry(serializedRecord, raftEntryOffset);
    final int raftBlock =
        header(raft, 0, RAFT_SCHEMA_ID, RAFT_LOG_ENTRY_TEMPLATE_ID, 9, "RaftLogEntry");
    final int type = Byte.toUnsignedInt(raft.get(SBE_HEADER_LENGTH + 8));
    if (type != RAFT_APPLICATION_ENTRY_TYPE) {
      if (asqn != NO_POSITION) {
        throw new InvalidEntry.Malformed(
            ("Expected asqn %d for journal entry %d of Raft entry type %d, which holds no"
                    + " records, but got %d")
                .formatted(NO_POSITION, index, type, asqn));
      }
    } else {
      final int application = SBE_HEADER_LENGTH + raftBlock;
      header(
          raft,
          application,
          RAFT_SCHEMA_ID,
          RAFT_APPLICATION_ENTRY_TEMPLATE_ID,
          16,
          "ApplicationEntry");
      final long lowest = raft.getLong(application + SBE_HEADER_LENGTH);
      final long highest = raft.getLong(application + SBE_HEADER_LENGTH + 8);
      if (lowest != asqn) {
        throw new InvalidEntry.Malformed(
            "Expected journal entry %d's asqn %d to be its lowest position, but got %d"
                .formatted(index, asqn, lowest));
      }
      if (lowest < 1 || highest < lowest) {
        throw new InvalidEntry.Malformed(
            ("Expected journal entry %d's lowest position to be at least 1 and at most its"
                    + " highest, but got %d to %d")
                .formatted(index, lowest, highest));
      }
    }
  }

  /** Checks an SBE message header, and returns the message's block length. */
  private static int header(
      ByteBuffer buffer,
      int offset,
      int schemaId,
      int templateId,
      int minBlockLength,
      String message) {
    if (buffer.limit() < offset + SBE_HEADER_LENGTH) {
      throw new InvalidEntry.Malformed(
          "Expected a %s header at bytes %d to %d of a journal entry, but it has %d bytes"
              .formatted(message, offset, offset + SBE_HEADER_LENGTH, buffer.limit()));
    }
    final int blockLength = Short.toUnsignedInt(buffer.getShort(offset));
    final int actualTemplateId = Short.toUnsignedInt(buffer.getShort(offset + 2));
    final int actualSchemaId = Short.toUnsignedInt(buffer.getShort(offset + 4));
    if (actualTemplateId != templateId || actualSchemaId != schemaId) {
      throw new InvalidEntry.Malformed(
          "Expected %s, template %d of schema %d, but got template %d of schema %d"
              .formatted(message, templateId, schemaId, actualTemplateId, actualSchemaId));
    }
    if (blockLength < minBlockLength) {
      throw new InvalidEntry.Malformed(
          "Expected a %s block of at least %d bytes, but got %d"
              .formatted(message, minBlockLength, blockLength));
    }
    final int end = offset + SBE_HEADER_LENGTH + blockLength;
    if (buffer.limit() < end) {
      throw new InvalidEntry.Malformed(
          "Expected a %s block up to byte %d of a journal entry, but it has %d bytes"
              .formatted(message, end, buffer.limit()));
    }
    return blockLength;
  }

  /** The {@code RecordData} message, as the journal wrote it. Callers must not change it. */
  @Override
  public byte[] serializedRecord() {
    return serializedRecord;
  }

  /** The entry's Raft index. */
  public long index() {
    return record().getLong(INDEX_OFFSET);
  }

  /**
   * The position of the entry's first record, or {@link #NO_POSITION} if it holds none: its asqn,
   * which the constructor checked against the Raft entry.
   */
  public long firstPosition() {
    return record().getLong(ASQN_OFFSET);
  }

  /** The position of the entry's last record, or {@link #NO_POSITION} if it holds none. */
  public long lastPosition() {
    if (!hasRecords()) {
      return NO_POSITION;
    }
    final var raft = raftEntry();
    final int application = SBE_HEADER_LENGTH + Short.toUnsignedInt(raft.getShort(0));
    return raft.getLong(application + SBE_HEADER_LENGTH + 8);
  }

  public boolean hasRecords() {
    return firstPosition() != NO_POSITION;
  }

  /** The Raft entry, a {@code RaftLogEntry} message and the entry after it, read-only. */
  public ByteBuffer raftEntry() {
    final var record = record();
    final int raftEntryOffset = SBE_HEADER_LENGTH + Short.toUnsignedInt(record.getShort(0)) + 4;
    return raftEntry(serializedRecord, raftEntryOffset)
        .asReadOnlyBuffer()
        .order(ByteOrder.LITTLE_ENDIAN);
  }

  private ByteBuffer record() {
    return ByteBuffer.wrap(serializedRecord).order(ByteOrder.LITTLE_ENDIAN);
  }

  private static ByteBuffer raftEntry(byte[] serializedRecord, int offset) {
    return ByteBuffer.wrap(serializedRecord)
        .slice(offset, serializedRecord.length - offset)
        .order(ByteOrder.LITTLE_ENDIAN);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Entry(long otherChecksum, byte[] otherRecord)
        && checksum == otherChecksum
        && Arrays.equals(serializedRecord, otherRecord);
  }

  @Override
  public int hashCode() {
    return 31 * Long.hashCode(checksum) + Arrays.hashCode(serializedRecord);
  }

  @Override
  public String toString() {
    return "Entry[index=%d, firstPosition=%d, lastPosition=%d, bytes=%d]"
        .formatted(index(), firstPosition(), lastPosition(), serializedRecord.length);
  }
}
