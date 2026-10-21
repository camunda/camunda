/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

import static io.camunda.zeebe.eventplane.segment.Entry.NO_POSITION;

import com.github.luben.zstd.ZstdOutputStreamNoFinalizer;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Streams one segment into an output stream: the body as entries are appended, then the metadata
 * and the footer on {@link #finish}. The entries must be consecutive in one history's journal. The
 * writer doesn't close the stream. Closing the writer before {@link #finish} leaves the object
 * incomplete, and frees the compressor.
 */
public final class SegmentWriter implements AutoCloseable {

  /** 8 MiB, so a decoder never needs more memory than that. */
  static final int WINDOW_LOG = 23;

  static final int FRAME_VERSION = 1;
  static final int RECORD_METADATA_TEMPLATE_ID = 1;
  static final int RECORD_METADATA_BLOCK_LENGTH = 12;
  static final int ENTRY_PREFIX_LENGTH = 1 + Entry.SBE_HEADER_LENGTH + RECORD_METADATA_BLOCK_LENGTH;

  /** zstd's default: a publisher's few seconds of entries compress fast, and well enough. */
  private static final int COMPRESSION_LEVEL = 3;

  private static final int JOURNAL_SCHEMA_VERSION = 2;

  private final CountingOutputStream out;
  private final ZstdOutputStreamNoFinalizer zstd;
  private final LogId log;
  private final UUID history;
  private final Level level;
  private final long frameTarget;
  private final List<Frame> frames = new ArrayList<>();
  private final ByteBuffer prefix =
      ByteBuffer.allocate(ENTRY_PREFIX_LENGTH).order(ByteOrder.LITTLE_ENDIAN);

  private boolean frameOpen;
  private long frameBytes;
  private long firstIndex;
  private long lastIndex;
  private long firstPosition = NO_POSITION;
  private long lastPosition = NO_POSITION;
  private boolean done;

  public SegmentWriter(OutputStream out, LogId log, UUID history, Level level) throws IOException {
    this(out, log, history, level, level.frameTarget());
  }

  SegmentWriter(OutputStream out, LogId log, UUID history, Level level, long frameTarget)
      throws IOException {
    this.out = new CountingOutputStream(out);
    this.log = log;
    this.history = history;
    this.level = level;
    this.frameTarget = frameTarget;
    zstd =
        new ZstdOutputStreamNoFinalizer(this.out, COMPRESSION_LEVEL)
            .setWindowLog(WINDOW_LOG)
            .setCloseFrameOnFlush(true);
  }

  /** Appends the next entry of the journal, and ends the frame once it reaches its target size. */
  public void append(Entry entry) throws IOException {
    if (done) {
      throw new SegmentFinishedException("append an entry to");
    }
    if (lastIndex != 0 && entry.index() != lastIndex + 1) {
      throw EntryOutOfOrderException.index(lastIndex + 1, entry.index());
    }
    if (entry.hasRecords()
        && lastPosition != NO_POSITION
        && entry.firstPosition() != lastPosition + 1) {
      throw EntryOutOfOrderException.position(lastPosition + 1, entry.firstPosition());
    }
    if (!frameOpen) {
      frames.add(new Frame(NO_POSITION, out.count()));
      frameOpen = true;
      frameBytes = 0;
    }
    if (entry.hasRecords() && frames.getLast().firstPosition() == NO_POSITION) {
      frames.set(frames.size() - 1, new Frame(entry.firstPosition(), frames.getLast().offset()));
    }

    final var record = entry.serializedRecord();
    prefix
        .clear()
        .put((byte) FRAME_VERSION)
        .putShort((short) RECORD_METADATA_BLOCK_LENGTH)
        .putShort((short) RECORD_METADATA_TEMPLATE_ID)
        .putShort((short) Entry.JOURNAL_SCHEMA_ID)
        .putShort((short) JOURNAL_SCHEMA_VERSION)
        .putLong(entry.checksum())
        .putInt(record.length);
    zstd.write(prefix.array());
    zstd.write(record);
    frameBytes += ENTRY_PREFIX_LENGTH + record.length;
    if (frameBytes >= frameTarget) {
      zstd.flush();
      frameOpen = false;
    }

    if (firstIndex == 0) {
      firstIndex = entry.index();
    }
    lastIndex = entry.index();
    if (entry.hasRecords()) {
      if (firstPosition == NO_POSITION) {
        firstPosition = entry.firstPosition();
      }
      lastPosition = entry.lastPosition();
    }
  }

  /**
   * Ends the body, and writes the metadata and the footer. {@code latestRecordTimestamp} is the
   * latest timestamp of the segment's records, in epoch milliseconds, or {@link Entry#NO_POSITION}
   * if it holds none.
   */
  public Footer finish(long latestRecordTimestamp) throws IOException {
    if (done) {
      throw new SegmentFinishedException("finish");
    }
    if (firstIndex == 0) {
      throw new EmptySegmentException();
    }
    final boolean records = firstPosition != NO_POSITION;
    if (!Footer.validTimestamp(records, latestRecordTimestamp)) {
      throw new IllegalArgumentException(
          Footer.timestampExpectation(records, latestRecordTimestamp));
    }
    done = true;
    zstd.closeWithoutClosingParentStream();
    final var footer =
        new Footer(
            history,
            firstIndex,
            lastIndex,
            firstPosition,
            lastPosition,
            latestRecordTimestamp,
            out.count(),
            level);
    out.write(new Metadata(log, frames).encode());
    out.write(footer.encode());
    out.flush();
    return footer;
  }

  /** The bytes written so far, which after {@link #finish} is the object's size. */
  public long size() {
    return out.count();
  }

  @Override
  public void close() throws IOException {
    if (!done) {
      done = true;
      zstd.closeWithoutClosingParentStream();
    }
  }

  private static final class CountingOutputStream extends FilterOutputStream {

    private long count;

    CountingOutputStream(OutputStream out) {
      super(out);
    }

    long count() {
      return count;
    }

    @Override
    public void write(int b) throws IOException {
      out.write(b);
      count++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      out.write(b, off, len);
      count += len;
    }
  }
}
