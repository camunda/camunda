/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

import com.github.luben.zstd.ZstdIOException;
import com.github.luben.zstd.ZstdInputStreamNoFinalizer;
import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.InvalidBody;
import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.InvalidEntry;
import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.Truncated;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jspecify.annotations.Nullable;

/**
 * Reads the entries of a segment's body, from the start of a frame up to the body's end, and checks
 * each against its checksum. The stream must end where the body ends, as a ranged GET up to the
 * body length does: the metadata after it isn't a zstd frame. Closing the reader closes the stream.
 */
public final class EntryReader implements AutoCloseable {

  private final ZstdInputStreamNoFinalizer zstd;
  private final ByteBuffer prefix =
      ByteBuffer.allocate(SegmentWriter.ENTRY_PREFIX_LENGTH).order(ByteOrder.LITTLE_ENDIAN);

  public EntryReader(InputStream body) throws IOException {
    zstd = new ZstdInputStreamNoFinalizer(body).setLongMax(SegmentWriter.WINDOW_LOG);
  }

  /**
   * The next entry, or null at the body's end.
   *
   * @throws InvalidSegmentException if the body isn't zstd frames of entries
   * @throws IOException if reading the stream fails
   */
  public @Nullable Entry next() throws IOException {
    try {
      return read();
    } catch (ZstdIOException e) {
      throw new InvalidBody(e);
    }
  }

  private @Nullable Entry read() throws IOException {
    final int read = zstd.readNBytes(prefix.array(), 0, prefix.capacity());
    if (read == 0) {
      return null;
    }
    if (read < prefix.capacity()) {
      throw new Truncated(
          "Expected %d bytes of an entry's RecordMetadata, but the body ends after %d"
              .formatted(prefix.capacity(), read));
    }
    final int frameVersion = Byte.toUnsignedInt(prefix.get(0));
    if (frameVersion != SegmentWriter.FRAME_VERSION) {
      throw new InvalidEntry.Malformed(
          "Expected an entry with frame version %d, but got %d"
              .formatted(SegmentWriter.FRAME_VERSION, frameVersion));
    }
    final int blockLength = Short.toUnsignedInt(prefix.getShort(1));
    final int templateId = Short.toUnsignedInt(prefix.getShort(3));
    final int schemaId = Short.toUnsignedInt(prefix.getShort(5));
    if (templateId != SegmentWriter.RECORD_METADATA_TEMPLATE_ID
        || schemaId != Entry.JOURNAL_SCHEMA_ID) {
      throw new InvalidEntry.Malformed(
          "Expected RecordMetadata, template %d of schema %d, but got template %d of schema %d"
              .formatted(
                  SegmentWriter.RECORD_METADATA_TEMPLATE_ID,
                  Entry.JOURNAL_SCHEMA_ID,
                  templateId,
                  schemaId));
    }
    if (blockLength < SegmentWriter.RECORD_METADATA_BLOCK_LENGTH) {
      throw new InvalidEntry.Malformed(
          "Expected a RecordMetadata block of at least %d bytes, but got %d"
              .formatted(SegmentWriter.RECORD_METADATA_BLOCK_LENGTH, blockLength));
    }
    final long checksum = prefix.getLong(9);
    final int length = prefix.getInt(17);
    if (length < 0) {
      throw new InvalidEntry.Malformed(
          "Expected a RecordData of at most %d bytes, but got %d"
              .formatted(Integer.MAX_VALUE, Integer.toUnsignedLong(length)));
    }
    // A later RecordMetadata may have more fields, which this reader doesn't know.
    final int extra = blockLength - SegmentWriter.RECORD_METADATA_BLOCK_LENGTH;
    try {
      zstd.skipNBytes(extra);
    } catch (EOFException e) {
      throw new Truncated(
          "Expected %d more bytes of an entry's RecordMetadata, but the body ends first"
              .formatted(extra));
    }
    final var record = zstd.readNBytes(length);
    if (record.length < length) {
      throw new Truncated(
          "Expected a RecordData of %d bytes, but the body ends after %d"
              .formatted(length, record.length));
    }
    return new Entry(checksum, record);
  }

  @Override
  public void close() throws IOException {
    zstd.close();
  }
}
