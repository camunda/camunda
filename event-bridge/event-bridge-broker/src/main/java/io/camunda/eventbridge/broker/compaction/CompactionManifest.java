/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The authoritative description of a compacted partition's clean set: the ordered list of clean
 * segments, the cleaner point C the pass reached, the log clock, and the format version. Together
 * with the segment files it references, the manifest is the content that becomes the partition's
 * Raft snapshot (ADR 0001, decision 3) — here it is committed through the injectable {@link
 * ManifestStore} seam.
 *
 * <h3>Everything is log-derived — determinism is unconditional</h3>
 *
 * <p>Every field of the manifest is a pure function of the replicated committed log and the
 * previous committed manifest; no replica wall-clock value is ever persisted. {@link #serialize()}
 * emits a line-oriented UTF-8 form with segments sorted by first position, so identical logical
 * content always produces identical bytes — replicas with the same log and pass lineage hold
 * byte-identical manifests and clean sets.
 *
 * <h3>The log clock ({@code maxLogTimestamp})</h3>
 *
 * <p>{@code maxLogTimestamp} is the <em>running maximum</em> of broker-assigned record timestamps
 * observed up to the cleaner point, advanced monotonically each pass from the dirty-range scan. The
 * running maximum — never the raw last timestamp — is used because leader changes can make raw
 * timestamps wobble backwards; the maximum only moves forward. It is the clock against which
 * tombstone grace is measured (see {@link CompactionPass}): being derived from the log, it is
 * identical on every replica.
 *
 * <p>Threading: immutable; safe to share. Defensive copies are taken at construction.
 */
public final class CompactionManifest {

  /** Current manifest format version. */
  public static final int VERSION_1 = 1;

  /** The cleaner point of an empty (never-cleaned) partition; nothing has been swept yet. */
  public static final long NO_CLEANER_POINT = -1L;

  /** The log clock of an empty (never-cleaned) partition; no record timestamp observed yet. */
  public static final long NO_TIMESTAMP = -1L;

  private static final String MAGIC = "EBCOMPACT-MANIFEST";

  private final int version;
  private final long cleanerPoint;
  private final long maxLogTimestamp;
  private final List<CleanSegment> segments;

  /**
   * @param version the format version
   * @param cleanerPoint the cleaner point C this manifest was committed at
   * @param maxLogTimestamp the log clock: the running maximum record timestamp observed up to C
   * @param segments the clean segments (copied and sorted by first position)
   */
  public CompactionManifest(
      final int version,
      final long cleanerPoint,
      final long maxLogTimestamp,
      final List<CleanSegment> segments) {
    this.version = version;
    this.cleanerPoint = cleanerPoint;
    this.maxLogTimestamp = maxLogTimestamp;
    final List<CleanSegment> sorted = new ArrayList<>(segments);
    sorted.sort(
        Comparator.comparingLong(CleanSegment::firstPosition)
            .thenComparing(CleanSegment::fileName));
    this.segments = List.copyOf(sorted);
  }

  /** Returns an empty manifest for a partition that has never been cleaned. */
  public static CompactionManifest empty() {
    return new CompactionManifest(VERSION_1, NO_CLEANER_POINT, NO_TIMESTAMP, List.of());
  }

  public int version() {
    return version;
  }

  public long cleanerPoint() {
    return cleanerPoint;
  }

  /**
   * Returns the log clock: the running maximum record timestamp observed up to {@link
   * #cleanerPoint()}, or {@link #NO_TIMESTAMP} if the partition has never been cleaned.
   */
  public long maxLogTimestamp() {
    return maxLogTimestamp;
  }

  /** Returns the clean segments in ascending first-position order. */
  public List<CleanSegment> segments() {
    return segments;
  }

  /**
   * Serializes the manifest into its deterministic byte form.
   *
   * @return the manifest bytes (UTF-8)
   */
  public byte[] serialize() {
    final var sb = new StringBuilder();
    sb.append(MAGIC).append('\t').append(version).append('\n');
    sb.append("C").append('\t').append(cleanerPoint).append('\n');
    sb.append("M").append('\t').append(maxLogTimestamp).append('\n');
    for (final CleanSegment s : segments) {
      sb.append("S")
          .append('\t')
          .append(s.fileName())
          .append('\t')
          .append(s.firstPosition())
          .append('\t')
          .append(s.byteLength())
          .append('\t')
          .append(s.crc32())
          .append('\n');
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Parses a manifest from its serialized byte form. Structural problems (bad magic, unknown
   * version, malformed line, missing cleaner point or log clock) fail loudly.
   *
   * @param bytes the serialized manifest
   * @return the parsed manifest
   * @throws IllegalStateException if the bytes are not a well-formed manifest
   */
  public static CompactionManifest parse(final byte[] bytes) {
    final String text = new String(bytes, StandardCharsets.UTF_8);
    final String[] lines = text.split("\n", -1);
    if (lines.length == 0) {
      throw new IllegalStateException("Empty manifest");
    }

    final String[] header = lines[0].split("\t");
    if (header.length != 2 || !MAGIC.equals(header[0])) {
      throw new IllegalStateException("Not an event-bridge compaction manifest: " + lines[0]);
    }
    final int parsedVersion = parseInt(header[1], "version");
    if (parsedVersion != VERSION_1) {
      throw new IllegalStateException("Unsupported manifest version: " + parsedVersion);
    }

    long parsedCleanerPoint = NO_CLEANER_POINT;
    boolean sawCleanerPoint = false;
    long parsedMaxLogTimestamp = NO_TIMESTAMP;
    boolean sawMaxLogTimestamp = false;
    final var parsedSegments = new ArrayList<CleanSegment>();

    for (int i = 1; i < lines.length; i++) {
      final String line = lines[i];
      if (line.isEmpty()) {
        continue; // trailing newline
      }
      final String[] f = line.split("\t");
      switch (f[0]) {
        case "C" -> {
          requireFieldCount(f, 2, line);
          parsedCleanerPoint = parseLong(f[1], "cleanerPoint");
          sawCleanerPoint = true;
        }
        case "M" -> {
          requireFieldCount(f, 2, line);
          parsedMaxLogTimestamp = parseLong(f[1], "maxLogTimestamp");
          sawMaxLogTimestamp = true;
        }
        case "S" -> {
          requireFieldCount(f, 5, line);
          parsedSegments.add(
              new CleanSegment(
                  f[1],
                  parseLong(f[2], "firstPosition"),
                  parseLong(f[3], "byteLength"),
                  parseLong(f[4], "crc32")));
        }
        default -> throw new IllegalStateException("Unknown manifest line: " + line);
      }
    }

    if (!sawCleanerPoint) {
      throw new IllegalStateException("Manifest missing cleaner point line");
    }
    if (!sawMaxLogTimestamp) {
      throw new IllegalStateException("Manifest missing log clock (maxLogTimestamp) line");
    }
    return new CompactionManifest(
        parsedVersion, parsedCleanerPoint, parsedMaxLogTimestamp, parsedSegments);
  }

  private static void requireFieldCount(
      final String[] fields, final int expected, final String line) {
    if (fields.length != expected) {
      throw new IllegalStateException(
          "Malformed manifest line (expected " + expected + " fields): " + line);
    }
  }

  private static int parseInt(final String value, final String field) {
    try {
      return Integer.parseInt(value);
    } catch (final NumberFormatException e) {
      throw new IllegalStateException("Malformed " + field + " in manifest: " + value, e);
    }
  }

  private static long parseLong(final String value, final String field) {
    try {
      return Long.parseLong(value);
    } catch (final NumberFormatException e) {
      throw new IllegalStateException("Malformed " + field + " in manifest: " + value, e);
    }
  }
}
