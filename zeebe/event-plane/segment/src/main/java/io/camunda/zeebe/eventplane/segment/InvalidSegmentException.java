/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * Bytes that aren't a segment, or a journal entry, as docs/12-segment-format.md describes it. Its
 * subtypes name the part that's wrong, and callers can switch over them exhaustively. It's
 * unchecked, and no {@code IOException}: reading the same bytes again won't help, unlike after a
 * failed read.
 */
public abstract sealed class InvalidSegmentException extends RuntimeException {

  private InvalidSegmentException(@Nullable String message, @Nullable Throwable cause) {
    super(message, cause);
  }

  /** A journal entry that isn't as the journal and Raft write it. */
  public abstract static sealed class InvalidEntry extends InvalidSegmentException {

    private InvalidEntry(String message) {
      super(message, null);
    }

    /** An entry whose bytes don't match its checksum. */
    public static final class ChecksumMismatch extends InvalidEntry {

      private final long expected;
      private final long actual;

      ChecksumMismatch(long expected, long actual) {
        super(
            "Expected a journal entry with checksum %d, but its bytes have checksum %d"
                .formatted(expected, actual));
        this.expected = expected;
        this.actual = actual;
      }

      /** The checksum the entry came with. */
      public long expected() {
        return expected;
      }

      /** The checksum of the entry's bytes. */
      public long actual() {
        return actual;
      }
    }

    /** An entry whose bytes aren't laid out as the journal's and Raft's SBE schemas say. */
    public static final class Malformed extends InvalidEntry {

      Malformed(String message) {
        super(message);
      }
    }
  }

  /** A footer this reader can't use. */
  public abstract static sealed class InvalidFooter extends InvalidSegmentException {

    private InvalidFooter(String message) {
      super(message, null);
    }

    /** A format version this reader doesn't know, so it can't find the footer's start. */
    public static final class UnknownVersion extends InvalidFooter {

      private final int version;

      UnknownVersion(int version) {
        super("Expected segment format version %d, but got %d".formatted(Footer.VERSION, version));
        this.version = version;
      }

      public int version() {
        return version;
      }
    }

    /** A level byte that is neither a publisher's nor a rollup's. */
    public static final class UnknownLevel extends InvalidFooter {

      private final int level;

      UnknownLevel(int level) {
        super(
            "Expected segment level %d or %d, but got %d"
                .formatted(Level.PUBLISHER.code(), Level.ROLLUP.code(), level));
        this.level = level;
      }

      public int level() {
        return level;
      }
    }

    /** Indexes that don't run from at least 1 up. */
    public static final class Indexes extends InvalidFooter {

      private final long firstIndex;
      private final long lastIndex;

      Indexes(long firstIndex, long lastIndex) {
        super(
            "Expected a segment's indexes to run from at least 1 up, but got %d to %d"
                .formatted(firstIndex, lastIndex));
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
      }

      public long firstIndex() {
        return firstIndex;
      }

      public long lastIndex() {
        return lastIndex;
      }
    }

    /** Positions that are neither both -1, for no records, nor run from at least 1 up. */
    public static final class Positions extends InvalidFooter {

      private final long firstPosition;
      private final long lastPosition;

      Positions(long firstPosition, long lastPosition) {
        super(
            ("Expected a segment's positions to be %d and %d, or to run from at least 1 up, but"
                    + " got %d to %d")
                .formatted(Entry.NO_POSITION, Entry.NO_POSITION, firstPosition, lastPosition));
        this.firstPosition = firstPosition;
        this.lastPosition = lastPosition;
      }

      public long firstPosition() {
        return firstPosition;
      }

      public long lastPosition() {
        return lastPosition;
      }
    }

    /** A latest record timestamp below 0 with records, or other than -1 without any. */
    public static final class Timestamp extends InvalidFooter {

      private final long latestRecordTimestamp;

      Timestamp(boolean hasRecords, long latestRecordTimestamp) {
        super(Footer.timestampExpectation(hasRecords, latestRecordTimestamp));
        this.latestRecordTimestamp = latestRecordTimestamp;
      }

      public long latestRecordTimestamp() {
        return latestRecordTimestamp;
      }
    }

    /** A body length below 1: every segment holds at least one entry. */
    public static final class BodyLength extends InvalidFooter {

      private final long bodyLength;

      BodyLength(long bodyLength) {
        super("Expected a segment's body to be at least 1 byte, but got " + bodyLength);
        this.bodyLength = bodyLength;
      }

      public long bodyLength() {
        return bodyLength;
      }
    }
  }

  /** A body that zstd can't decode: damaged, cut inside a frame, or needing a window over 8 MiB. */
  public static final class InvalidBody extends InvalidSegmentException {

    InvalidBody(IOException zstdError) {
      super(
          "Expected a body of zstd frames with windows of at most 8 MiB, but zstd fails with: "
              + zstdError.getMessage(),
          zstdError);
    }
  }

  /** Metadata whose directory or entries contradict each other, the footer or the format. */
  public static final class InvalidMetadata extends InvalidSegmentException {

    InvalidMetadata(String message) {
      super(message, null);
    }

    InvalidMetadata(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** A segment, or the part of it that was read, that ends inside an entry or the footer. */
  public static final class Truncated extends InvalidSegmentException {

    Truncated(String message) {
      super(message, null);
    }
  }
}
