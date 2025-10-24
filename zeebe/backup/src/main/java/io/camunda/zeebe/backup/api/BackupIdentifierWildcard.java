/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern.Any;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern.Exact;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern.Prefix;
import java.util.Optional;

/**
 * Similar to {@link BackupIdentifier} but fields that are omitted should be interpreted as a
 * wildcard.
 */
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

    switch (wildcard.checkpointPattern()) {
      case Exact(final var checkpointId) -> {
        prefix.append(checkpointId);
        prefix.append("/");
        // Checkpoint pattern is exact so we can include node id if present
        if (wildcard.nodeId().isPresent()) {
          prefix.append(wildcard.nodeId().get());
        }
      }
      // Checkpoint pattern is not exact so our prefix ends here, we can't include the node id
      case Prefix(final var cpPrefix) -> prefix.append(cpPrefix);
      case Any() -> {}
    }
    return prefix.toString();
  }

  sealed interface CheckpointPattern {

    boolean matches(final long checkpointId);

    String asRegex();

    static CheckpointPattern any() {
      return new Any();
    }

    static CheckpointPattern of(final long checkpointId) {
      return new Exact(checkpointId);
    }

    static CheckpointPattern of(final String pattern) {
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
    }

    record Prefix(String prefix) implements CheckpointPattern {
      public Prefix {
        if (prefix.isEmpty()) {
          throw new IllegalArgumentException("Prefix must not be empty");
        }
        try {
          Long.valueOf(prefix);
        } catch (final NumberFormatException e) {
          throw new IllegalArgumentException("Prefix must be a valid number", e);
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
          throw new IllegalArgumentException("Checkpoint id must be non-negative");
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
    }
  }
}
