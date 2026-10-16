/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Object keys of the log archive bucket layout (spec section 9).
 *
 * <p>Keys returned by this class are relative to the partition prefix from {@link
 * #partitionPrefix(String, int)}. The full key in the store is the prefix followed by the relative
 * key. Sequence and index numbers are zero-padded to 20 digits, so that lexicographic order of keys
 * is the numeric order. The term in data keys and the snapshot id are not padded, following the
 * examples in the spec.
 */
public final class LogArchiveKeys {

  /** Width of zero-padded numbers in keys. */
  public static final int NUMBER_WIDTH = 20;

  private static final Pattern COMMIT_KEY = Pattern.compile("(?:^|/)commits/(\\d{20})\\.json$");
  private static final Pattern ROOT_KEY = Pattern.compile("(?:^|/)root/(\\d{20})\\.json$");

  private LogArchiveKeys() {}

  /** The prefix of all keys of one partition of one timeline, e.g. {@code log-archive/tl-0/p1/}. */
  public static String partitionPrefix(final String timelineId, final int partitionId) {
    return "log-archive/" + timelineId + "/p" + partitionId + "/";
  }

  /** {@code data/<firstIndex>-<lastIndex>-t<term>.rec} */
  public static String dataKey(final long firstIndex, final long lastIndex, final long term) {
    return "data/" + pad(firstIndex) + "-" + pad(lastIndex) + "-t" + term + ".rec";
  }

  /** {@code commits/<seq>.json} */
  public static String commitKey(final long seq) {
    return "commits/" + pad(seq) + ".json";
  }

  /** {@code index/<firstIndex>-<lastIndex>.json} */
  public static String indexKey(final long firstIndex, final long lastIndex) {
    return "index/" + pad(firstIndex) + "-" + pad(lastIndex) + ".json";
  }

  /** {@code root/<seq>.json} */
  public static String rootKey(final long seq) {
    return "root/" + pad(seq) + ".json";
  }

  /** {@code _latest.json} */
  public static String latestKey() {
    return "_latest.json";
  }

  /** {@code state/<snapshotId>/<fileName>} */
  public static String snapshotFileKey(final String snapshotId, final String fileName) {
    return "state/" + snapshotId + "/" + fileName;
  }

  /** {@code state/<snapshotId>/descriptor.json} */
  public static String snapshotDescriptorKey(final String snapshotId) {
    return "state/" + snapshotId + "/descriptor.json";
  }

  /**
   * Returns the seq of a commit key, e.g. {@code commits/00000000000000004127.json} or the same key
   * with the partition prefix in front of it.
   *
   * @throws IllegalArgumentException if the key is not a commit key
   */
  public static long seqFromCommitKey(final String key) {
    return seqFrom(COMMIT_KEY, key, "commit");
  }

  /**
   * Returns the seq of a root key, e.g. {@code root/00000000000000004100.json} or the same key with
   * the partition prefix in front of it.
   *
   * @throws IllegalArgumentException if the key is not a root key
   */
  public static long seqFromRootKey(final String key) {
    return seqFrom(ROOT_KEY, key, "root");
  }

  private static long seqFrom(final Pattern pattern, final String key, final String kind) {
    final Matcher matcher = pattern.matcher(key);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Not a log archive " + kind + " key: " + key);
    }
    return Long.parseLong(matcher.group(1));
  }

  private static String pad(final long number) {
    if (number < 0) {
      throw new IllegalArgumentException("Key numbers must not be negative, but was " + number);
    }
    final String digits = Long.toString(number);
    return "0".repeat(NUMBER_WIDTH - digits.length()) + digits;
  }
}
