/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import java.util.regex.Pattern;

/**
 * The naming scheme for clean-segment files. A segment is named {@code
 * clean-<firstPosition>-<cleanerPoint>.log}, both numbers zero-padded to 19 digits (the widest a
 * {@code long} can be) so names sort lexicographically by position.
 *
 * <h3>Why the name encodes the cleaner point</h3>
 *
 * <p>Including the pass's cleaner point makes each pass's output uniquely named, so a new pass
 * never overwrites a segment still referenced by the currently-committed manifest — superseded
 * segments are left in place for the refcounted {@link TrashQueue} to unlink once no reader holds
 * them. Two independent runs over identical input pick the same cleaner point and produce the same
 * first positions, hence byte-identical names — the property the determinism guarantee needs. A
 * crash rerun with identical input reuses the same names and, because segment bytes are
 * deterministic, safely re-creates identical files.
 *
 * <p>Threading: stateless static helpers.
 */
final class CleanSegmentFiles {

  static final String PREFIX = "clean-";
  static final String SUFFIX = ".log";
  static final String TMP_SUFFIX = ".log.tmp";

  /**
   * Suffix appended to a segment's name when the trash queue condemns it (renames it ahead of the
   * deferred physical unlink) — the journal's {@code *-deleted} marker convention. A condemned name
   * never collides with a future segment: names embed the cleaner point, which strictly advances
   * per committed pass, so a superseded name is never written again.
   */
  static final String DELETED_SUFFIX = "-deleted";

  private static final Pattern SEGMENT_PATTERN =
      Pattern.compile(Pattern.quote(PREFIX) + "\\d{19}-\\d{19}" + Pattern.quote(SUFFIX));

  private CleanSegmentFiles() {}

  /** Returns the final segment file name for the given first position and cleaner point. */
  static String segmentName(final long firstPosition, final long cleanerPoint) {
    return PREFIX + pad(firstPosition) + "-" + pad(cleanerPoint) + SUFFIX;
  }

  /** Returns the temp file name a segment is staged through before its atomic rename. */
  static String tmpName(final long firstPosition, final long cleanerPoint) {
    return PREFIX + pad(firstPosition) + "-" + pad(cleanerPoint) + TMP_SUFFIX;
  }

  /** Returns the condemned marker name for a segment file name. */
  static String condemnedName(final String segmentFileName) {
    return segmentFileName + DELETED_SUFFIX;
  }

  /** Returns {@code true} if the given file name is a finalized clean-segment file. */
  static boolean isSegment(final String fileName) {
    return SEGMENT_PATTERN.matcher(fileName).matches();
  }

  /** Returns {@code true} if the given file name is a clean-segment temp (staging) file. */
  static boolean isTmp(final String fileName) {
    return fileName.startsWith(PREFIX) && fileName.endsWith(TMP_SUFFIX);
  }

  /** Returns {@code true} if the given file name is a condemned ({@code *-deleted}) marker. */
  static boolean isCondemned(final String fileName) {
    return fileName.startsWith(PREFIX) && fileName.endsWith(SUFFIX + DELETED_SUFFIX);
  }

  private static String pad(final long value) {
    return String.format("%019d", value);
  }
}
