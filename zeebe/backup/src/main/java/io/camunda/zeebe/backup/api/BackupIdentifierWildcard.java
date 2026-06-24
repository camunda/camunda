/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern.Exact;
import io.camunda.zeebe.util.MemberIdUtil;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Similar to {@link BackupIdentifier} but fields that are omitted should be interpreted as a
 * wildcard.
 */
@NullMarked
public interface BackupIdentifierWildcard {
  /**
   * @return the broker that took this backup, or empty to match any broker
   */
  Optional<BrokerMemberId> brokerId();

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
   * Tries to build the longest possible prefix based on the given wildcard. The prefix is
   * constructed as follows: {@code ${partitionId}/${checkpointId}/${brokerId}}. If a field is not
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
      wildcard.brokerId().ifPresent(id -> prefix.append(id.id()));
    }
    return prefix.toString();
  }

  /**
   * Returns a regex that matches the broker member path segment for the given wildcard. When
   * brokerId is present, returns the exact encoded segment. Otherwise returns a pattern that
   * matches the full set of possible forms (both zoned and zone-less).
   */
  static String memberIdRegex(final BackupIdentifierWildcard wildcard) {
    return wildcard
        .brokerId()
        .map(id -> Pattern.quote(id.id()))
        .orElse(MemberIdUtil.regexPattern());
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
