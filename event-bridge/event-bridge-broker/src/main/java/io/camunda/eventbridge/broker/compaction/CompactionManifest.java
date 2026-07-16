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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The authoritative description of a compacted partition's clean set: the ordered list of clean
 * segments, the cleaner point C the pass reached, the format version, and the per-tombstone grace
 * stamps. Together with the segment files it references, the manifest is the content that becomes
 * the partition's Raft snapshot (ADR 0001, decision 3) — here it is committed through the
 * injectable {@link ManifestStore} seam.
 *
 * <h3>Deterministic serialization</h3>
 *
 * <p>{@link #serialize()} emits a line-oriented UTF-8 form with segments sorted by first position
 * and tombstone stamps sorted by position, so identical logical content always produces identical
 * bytes — the manifest half of the determinism guarantee (two replicas' identical logs must yield
 * byte-identical clean sets and manifest).
 *
 * <h3>Tombstone grace stamps (per position, not per segment)</h3>
 *
 * <p>The ADR describes stamping a tombstone "per clean segment". This implementation stamps per
 * <em>tombstone position</em> instead, because clean segments are rewritten on every pass: a stamp
 * tied to the segment would reset each pass and the grace window would never elapse. Positions are
 * stable forever, so a per-position stamp is a strictly finer, rewrite-stable carrier of the same
 * information — a tombstone keeps its original stamp across passes until the grace window passes
 * and a later pass drops it. The per-segment {@code cleanedAtTimestamp} is retained as segment
 * metadata. (Flagged for the ADR to be amended.)
 *
 * <p>Threading: immutable; safe to share. Defensive copies are taken at construction.
 */
public final class CompactionManifest {

  /** Current manifest format version. */
  public static final int VERSION_1 = 1;

  /** Sentinel returned by {@link #tombstoneStamp(long)} when a position has no stamp. */
  public static final long NO_STAMP = Long.MIN_VALUE;

  /** The cleaner point of an empty (never-cleaned) partition; nothing has been swept yet. */
  public static final long NO_CLEANER_POINT = -1L;

  private static final String MAGIC = "EBCOMPACT-MANIFEST";

  private final int version;
  private final long cleanerPoint;
  private final List<CleanSegment> segments;
  private final Map<Long, Long> tombstoneStamps;

  /**
   * @param version the format version
   * @param cleanerPoint the cleaner point C this manifest was committed at
   * @param segments the clean segments (copied and sorted by first position)
   * @param tombstoneStamps position → first-cleaned-at-millis for tombstones still within grace
   *     (copied)
   */
  public CompactionManifest(
      final int version,
      final long cleanerPoint,
      final List<CleanSegment> segments,
      final Map<Long, Long> tombstoneStamps) {
    this.version = version;
    this.cleanerPoint = cleanerPoint;
    final List<CleanSegment> sorted = new ArrayList<>(segments);
    sorted.sort(
        Comparator.comparingLong(CleanSegment::firstPosition)
            .thenComparing(CleanSegment::fileName));
    this.segments = List.copyOf(sorted);
    this.tombstoneStamps = Map.copyOf(tombstoneStamps);
  }

  /** Returns an empty manifest for a partition that has never been cleaned. */
  public static CompactionManifest empty() {
    return new CompactionManifest(VERSION_1, NO_CLEANER_POINT, List.of(), Map.of());
  }

  public int version() {
    return version;
  }

  public long cleanerPoint() {
    return cleanerPoint;
  }

  /** Returns the clean segments in ascending first-position order. */
  public List<CleanSegment> segments() {
    return segments;
  }

  /** Returns the tombstone grace stamps, keyed by position. */
  public Map<Long, Long> tombstoneStamps() {
    return tombstoneStamps;
  }

  /**
   * Returns the grace stamp for the tombstone at {@code position}, or {@link #NO_STAMP} if that
   * position has no recorded stamp (the tombstone has not yet entered the clean set).
   */
  public long tombstoneStamp(final long position) {
    final Long stamp = tombstoneStamps.get(position);
    return stamp == null ? NO_STAMP : stamp;
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
    for (final CleanSegment s : segments) {
      sb.append("S")
          .append('\t')
          .append(s.fileName())
          .append('\t')
          .append(s.firstPosition())
          .append('\t')
          .append(s.cleanedAtTimestamp())
          .append('\t')
          .append(s.byteLength())
          .append('\t')
          .append(s.crc32())
          .append('\n');
    }
    // Sort tombstone stamps by position for deterministic output.
    final var sortedStamps = new TreeMap<>(tombstoneStamps);
    for (final Map.Entry<Long, Long> e : sortedStamps.entrySet()) {
      sb.append("T").append('\t').append(e.getKey()).append('\t').append(e.getValue()).append('\n');
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Parses a manifest from its serialized byte form. Structural problems (bad magic, unknown
   * version, malformed line) fail loudly.
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
    final var parsedSegments = new ArrayList<CleanSegment>();
    final var parsedStamps = new LinkedHashMap<Long, Long>();

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
        case "S" -> {
          requireFieldCount(f, 6, line);
          parsedSegments.add(
              new CleanSegment(
                  f[1],
                  parseLong(f[2], "firstPosition"),
                  parseLong(f[3], "cleanedAtTimestamp"),
                  parseLong(f[4], "byteLength"),
                  parseLong(f[5], "crc32")));
        }
        case "T" -> {
          requireFieldCount(f, 3, line);
          parsedStamps.put(parseLong(f[1], "tombstonePosition"), parseLong(f[2], "tombstoneStamp"));
        }
        default -> throw new IllegalStateException("Unknown manifest line: " + line);
      }
    }

    if (!sawCleanerPoint) {
      throw new IllegalStateException("Manifest missing cleaner point line");
    }
    return new CompactionManifest(parsedVersion, parsedCleanerPoint, parsedSegments, parsedStamps);
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
