/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern.Exact;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Similar to {@link BackupIdentifier} but fields that are omitted should be interpreted as a
 * wildcard.
 */
@NullMarked
public interface BackupIdentifierWildcard {
  /**
   * @return id of the broker which took this backup.
   */
  Optional<Integer> nodeId();

  /**
   * @return id of the partition of which the backup is taken
   */
  Optional<Integer> partitionId();

  /**
   * @return id of the checkpoint included in the backup
   */
  CheckpointPattern checkpointPattern();

  /**
   * Predicate that tries to match an id to this wildcard.
   *
   * @return true if the given id matches the wildcard, otherwise false.
   */
  boolean matches(BackupIdentifier id);

  /**
   * Creates a BackupIdentifierWildcard that matches all backups with the specified checkpoint
   * pattern, regardless of partition or node ID.
   *
   * @param pattern the checkpoint pattern to match against
   * @return a BackupIdentifierWildcard with no partition or node ID specified
   */
  static BackupIdentifierWildcard ofPattern(final CheckpointPattern pattern) {
    return BackupIdentifierWildcard.forPartition(null, pattern);
  }

  /**
   * Creates a BackupIdentifierWildcard that matches backups with the specified partition and
   * checkpoint pattern, regardless of node ID.
   *
   * @param partition the partition ID to match, or null to match all partitions
   * @param pattern the checkpoint pattern to match against
   * @return a BackupIdentifierWildcard with the specified partition and pattern, but no node ID
   */
  static BackupIdentifierWildcard forPartition(
      final Integer partition, final CheckpointPattern pattern) {
    return new BackupIdentifierWildcardImpl(
        Optional.empty(), Optional.ofNullable(partition), pattern);
  }

  /**
   * Tries to build the longest possible prefix based on the given wildcard. The prefix is
   * constructed as follows: {@code ${partitionId}/${checkpointId}/${nodeId}}. If a field is not
   * present or is a non-exact match, all following fields are omitted. For example, if the
   * checkpoint id should match a {@link CheckpointPattern.Prefix prefix}, the returned prefix is
   * {@code ${partitionId}/${checkpointPattern.Prefix}}. If none of the fields are present, an empty
   * string is returned.
   */
  static String asPrefix(final BackupIdentifierWildcard wildcard) {
    final var prefix = new StringBuilder();
    if (wildcard.partitionId().isEmpty()) {
      return prefix.toString();
    }
    prefix.append(wildcard.partitionId().get());
    prefix.append("/");
    prefix.append(wildcard.checkpointPattern().prefix());

    if (wildcard.checkpointPattern() instanceof Exact) {
      prefix.append("/");
      // Checkpoint pattern is exact so we can include node id if present
      if (wildcard.nodeId().isPresent()) {
        prefix.append(wildcard.nodeId().get());
      }
    }
    return prefix.toString();
  }

  sealed interface CheckpointPattern {

    boolean matches(final long checkpointId);

    String asRegex();

    String prefix();

    static CheckpointPattern any() {
      return new Any();
    }

    static CheckpointPattern of(final long checkpointId) {
      return new Exact(checkpointId);
    }

    static CheckpointPattern of(@Nullable final String pattern) {
      if (pattern == null || pattern.isEmpty() || "*".equals(pattern)) {
        return new Any();
      } else if (pattern.endsWith("*")) {
        return new Prefix(pattern.substring(0, pattern.length() - 1));
      } else {
        try {
          final var checkpointId = Long.parseLong(pattern);
          return new Exact(checkpointId);
        } catch (final NumberFormatException e) {
          throw new IllegalArgumentException("Invalid checkpoint id pattern: " + pattern, e);
        }
      }
    }

    static CheckpointPattern longestCommonPrefix(final String... ids) {
      if (ids == null || ids.length == 0) {
        return new Any();
      }
      if (ids.length == 1) {
        try {
          return new Exact(Long.parseLong(ids[0]));
        } catch (final NumberFormatException e) {
          return new Prefix(ids[0]);
        }
      }

      // Find the minimum length among all strings
      final int minLength = Arrays.stream(ids).mapToInt(String::length).min().orElse(0);
      final int maxLength = Arrays.stream(ids).mapToInt(String::length).max().orElse(0);

      // Find the common prefix
      int commonPrefixLength = 0;
      for (int i = 0; i < minLength; i++) {
        final char currentChar = ids[0].charAt(i);
        boolean allMatch = true;
        for (int j = 1; j < ids.length; j++) {
          if (ids[j].charAt(i) != currentChar) {
            allMatch = false;
            break;
          }
        }
        if (allMatch) {
          commonPrefixLength++;
        } else {
          break;
        }
      }

      if (commonPrefixLength == maxLength) {
        return new Exact(Long.parseLong(ids[0]));
      } else if (commonPrefixLength > 0) {
        return new Prefix(ids[0].substring(0, commonPrefixLength));
      } else {
        return new Any();
      }
    }

    static CheckpointPattern.Range ofInterval(final Interval<Long> checkpointIdInterval) {
      // Find the common prefix between checkpoint IDs
      final var fromStr = String.valueOf(checkpointIdInterval.start());
      final var toStr = String.valueOf(checkpointIdInterval.end());

      final var commonPrefixPattern = longestCommonPrefix(fromStr, toStr);

      // Use the common prefix to create a TimeRange pattern
      return new Range(
          commonPrefixPattern, checkpointIdInterval.start(), checkpointIdInterval.end());
    }

    /**
     * Creates a CheckpointPattern that matches checkpoint IDs within a timestamp range. Uses a
     * CheckpointIdGenerator to convert raw timestamps to checkpoint IDs (applying the configured
     * offset), then computes the common prefix to create a Prefix pattern.
     *
     * @param from the start of the range (inclusive)
     * @param to the end of the range (inclusive)
     * @param generator the CheckpointIdGenerator used to convert timestamps to checkpoint IDs
     * @return a CheckpointPattern (Prefix or Any) that matches checkpoints in the given range
     */
    static Range ofTimeRange(
        final Instant from, final Instant to, final CheckpointIdGenerator generator) {

      if (from.toEpochMilli() < 0) {
        throw new IllegalArgumentException(
            "Expected 'from' to be non-negative, but got %s".formatted(from));
      }
      if (to.toEpochMilli() < 0) {
        throw new IllegalArgumentException(
            "Expected 'to' to be non-negative, but got %s".formatted(to));
      }
      if (from.isAfter(to)) {
        throw new IllegalArgumentException(
            "Expected 'from' to be <= 'to', but got from=%s and to=%s".formatted(from, to));
      }

      // Convert timestamps to checkpoint IDs using the generator
      final long fromCheckpointId = generator.fromTimestamp(from.toEpochMilli());
      final long toCheckpointId = generator.fromTimestamp(to.toEpochMilli());

      return ofInterval(new Interval<>(fromCheckpointId, toCheckpointId));
    }

    record Any() implements CheckpointPattern {

      @Override
      public boolean matches(final long checkpointId) {
        return true;
      }

      @Override
      public String asRegex() {
        return "\\d+";
      }

      @Override
      public String prefix() {
        return "";
      }
    }

    record Prefix(String prefix) implements CheckpointPattern {
      public Prefix {
        if (prefix.isEmpty()) {
          throw new IllegalArgumentException(
              "Expected prefix to be non-empty, but got empty string");
        }
        try {
          Long.valueOf(prefix);
        } catch (final NumberFormatException e) {
          throw new IllegalArgumentException(
              "Expected prefix to be a valid number, but got '%s'".formatted(prefix), e);
        }
      }

      @Override
      public boolean matches(final long checkpointId) {
        return String.valueOf(checkpointId).startsWith(prefix);
      }

      @Override
      public String asRegex() {
        return prefix + "\\d*";
      }
    }

    record Range(CheckpointPattern pattern, long checkpointStart, long checkpointEnd)
        implements CheckpointPattern {

      @Override
      public boolean matches(final long checkpointId) {
        return checkpointId >= checkpointStart && checkpointId <= checkpointEnd;
      }

      @Override
      public String asRegex() {
        return pattern.asRegex();
      }

      @Override
      public String prefix() {
        return pattern.prefix();
      }
    }

    record Exact(long checkpointId) implements CheckpointPattern {
      public Exact {
        if (checkpointId < 0) {
          throw new IllegalArgumentException(
              "Expected checkpointId to be non-negative, but got %d".formatted(checkpointId));
        }
      }

      @Override
      public boolean matches(final long checkpointId) {
        return checkpointId == this.checkpointId;
      }

      @Override
      public String asRegex() {
        return Long.toString(checkpointId);
      }

      @Override
      public String prefix() {
        return String.valueOf(checkpointId);
      }
    }
  }
}
