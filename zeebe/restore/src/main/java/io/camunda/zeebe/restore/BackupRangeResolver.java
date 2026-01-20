/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.backup.api.BackupRange;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure, side-effect-free resolver for determining valid backup ranges for restore operations. All
 * methods are static and accept data as parameters, making them easily testable without mocks.
 */
public final class BackupRangeResolver {

  private BackupRangeResolver() {
    // Utility class
  }

  /**
   * Finds the latest backup before the given timestamp.
   *
   * @param toTimestamp the target timestamp
   * @param allBackups collection of all available backups
   * @return the backup with max(checkpointTimestamp) where checkpointTimestamp <= toTimestamp, or
   *     empty if no such backup exists
   */
  public static Optional<BackupStatus> findLatestBackupBefore(
      final Instant toTimestamp, final Collection<BackupStatus> allBackups) {
    return allBackups.stream()
        .filter(backup -> backup.descriptor().isPresent())
        .filter(
            backup -> {
              final var checkpointTimestamp = backup.descriptor().get().checkpointTimestamp();
              return !checkpointTimestamp.isAfter(toTimestamp);
            })
        .max(Comparator.comparing(backup -> backup.descriptor().get().checkpointTimestamp()));
  }

  /**
   * Finds the safe starting checkpoint for a partition based on its exported position.
   *
   * @param exportedPosition the last position exported to RDBMS for this partition
   * @param availableBackups collection of all available backups
   * @return the largest checkpoint ID where checkpointPosition <= exportedPosition, or empty if no
   *     such checkpoint exists
   */
  public static Optional<Long> findSafeStartCheckpoint(
      final long exportedPosition, final Collection<BackupStatus> availableBackups) {
    return availableBackups.stream()
        .filter(backup -> backup.statusCode() == BackupStatusCode.COMPLETED)
        .filter(
            backup ->
                backup
                    .descriptor()
                    .map(ds -> ds.checkpointPosition() <= exportedPosition)
                    .orElse(false))
        .map(backup -> backup.id().checkpointId())
        .max(Long::compareTo);
  }

  /**
   * Validates that all partitions can safely restore to the global checkpoint.
   *
   * <p>This is CRITICAL for cluster consistency: ALL nodes must restore to the SAME global
   * checkpoint ID. However, each partition may start from a different checkpoint based on its
   * export position.
   *
   * <p>The validation ensures:
   *
   * <ul>
   *   <li>We have backup data from ALL expected nodes
   *   <li>Each partition has a safe start checkpoint where checkpointPosition <= exportedPosition
   *   <li>For each partition, ALL nodes that back up that partition have continuous backup ranges
   *       from backup_start[partition] to the global checkpoint
   *   <li>If ANY partition from any node cannot reach the global checkpoint, the entire restore
   *       fails. This is necessary to ensure that all nodes in the cluster restore the same
   *       checkpoint.
   * </ul>
   *
   * @param globalCheckpointId the target checkpoint that ALL nodes will restore to
   * @param expectedNodeCount the expected number of nodes in the cluster
   * @param safeStartByPartition map of partitionId → safe start checkpoint based on RDBMS export
   *     position
   * @param backupsByNodePartition map of (nodeId, partitionId) → collection of available backups
   * @param rangesByPartition map of partitionId → backup range for validation
   * @return validation result with success if all partitions can reach global checkpoint
   */
  public static ValidationResult validateGlobalCheckpointReachability(
      final long globalCheckpointId,
      final int expectedNodeCount,
      final Map<Integer, Long> safeStartByPartition,
      final Map<NodePartitionId, Collection<BackupStatus>> backupsByNodePartition,
      final Map<Integer, BackupRange> rangesByPartition) {

    if (safeStartByPartition.isEmpty()) {
      return ValidationResult.failure("No partitions found for checkpoint reachability validation");
    }

    final var failures = new ArrayList<String>();

    // Validate node count
    validateNodeCount(expectedNodeCount, backupsByNodePartition, failures);

    // Validate each partition
    for (final var partitionEntry : safeStartByPartition.entrySet()) {
      final int partitionId = partitionEntry.getKey();
      final long safeStart = partitionEntry.getValue();

      // errors are appended into `failures`
      validatePartition(
          partitionId,
          safeStart,
          globalCheckpointId,
          rangesByPartition.get(partitionId),
          backupsByNodePartition,
          failures);
    }

    if (!failures.isEmpty()) {
      return ValidationResult.failure(
          String.format(
              "Cannot restore to global checkpoint %d. Failures:\n  - %s",
              globalCheckpointId, String.join("\n  - ", failures)));
    }

    return ValidationResult.success();
  }

  private static void validateNodeCount(
      final int expectedNodeCount,
      final Map<NodePartitionId, Collection<BackupStatus>> backupsByNodePartition,
      final List<String> failures) {
    final Set<Integer> nodesFound =
        backupsByNodePartition.keySet().stream()
            .map(NodePartitionId::nodeId)
            .collect(Collectors.toSet());

    if (nodesFound.size() != expectedNodeCount) {
      final Set<Integer> expectedNodes = new HashSet<>();
      for (int i = 0; i < expectedNodeCount; i++) {
        expectedNodes.add(i);
      }
      final Set<Integer> missingNodes = new HashSet<>(expectedNodes);
      missingNodes.removeAll(nodesFound);

      failures.add(
          String.format(
              "Expected %d nodes but found %d. Missing nodes: %s",
              expectedNodeCount, nodesFound.size(), missingNodes));
    }
  }

  private static void validatePartition(
      final int partitionId,
      final long safeStart,
      final long globalCheckpointId,
      final BackupRange range,
      final Map<NodePartitionId, Collection<BackupStatus>> backupsByNodePartition,
      final List<String> failures) {

    // Check if safe start is beyond global checkpoint
    if (safeStart > globalCheckpointId) {
      failures.add(
          String.format(
              "Partition %d: safe start checkpoint %d is beyond global checkpoint %d.",
              partitionId, safeStart, globalCheckpointId));
      return;
    }

    // Validate range exists
    switch (range) {
      case null -> failures.add(String.format("Partition %d: no backup range found", partitionId));

      // Check if range is incomplete (has deletions)
      case final BackupRange.Incomplete incomplete ->
          failures.add(
              String.format(
                  "Partition %d: backup range [%d, %d] has deletions: %s",
                  partitionId,
                  incomplete.startCheckpointId(),
                  incomplete.endCheckpointId(),
                  incomplete.deletedCheckpointIds()));

      // For Complete ranges, validate coverage and log position contiguity
      case final BackupRange.Complete complete -> {
        validateRangeCoverage(partitionId, safeStart, globalCheckpointId, complete, failures);
        validateLogPositionContiguity(
            partitionId, safeStart, globalCheckpointId, backupsByNodePartition, failures);
      }
    }
  }

  private static void validateRangeCoverage(
      final int partitionId,
      final long safeStart,
      final long globalCheckpointId,
      final BackupRange.Complete range,
      final List<String> failures) {
    if (range.startCheckpointId() > safeStart || range.endCheckpointId() < globalCheckpointId) {
      failures.add(
          String.format(
              "Partition %d: backup range [%d, %d] does not cover required range [%d, %d]",
              partitionId,
              range.startCheckpointId(),
              range.endCheckpointId(),
              safeStart,
              globalCheckpointId));
    }
  }

  private static void validateLogPositionContiguity(
      final int partitionId,
      final long safeStart,
      final long globalCheckpointId,
      final Map<NodePartitionId, Collection<BackupStatus>> backupsByNodePartition,
      final List<String> failures) {

    for (final var nodePartitionEntry : backupsByNodePartition.entrySet()) {
      final var nodePartition = nodePartitionEntry.getKey();
      if (nodePartition.partitionId() != partitionId) {
        continue; // Skip other partitions
      }

      final var backups = nodePartitionEntry.getValue();
      final var sortedBackups = filterAndSortBackups(backups, safeStart, globalCheckpointId);

      if (sortedBackups.isEmpty()) {
        failures.add(
            String.format(
                "Partition %d: %s has no backups in range [%d, %d]",
                partitionId, nodePartition, safeStart, globalCheckpointId));
        continue;
      }

      validateBackupChainContiguity(partitionId, nodePartition, sortedBackups, failures);
    }
  }

  /**
   * Validates that a series of backups forms a contiguous chain via log positions.
   *
   * <p>For backups to be contiguous, each backup's firstLogPosition must be exactly one position
   * after the previous backup's checkpointPosition. This ensures there are no gaps in the log.
   *
   * @param partitionId the partition being validated
   * @param nodePartition the node-partition combination
   * @param sortedBackups backups sorted by checkpoint ID (timestamp)
   * @param failures list to accumulate failure messages
   */
  private static void validateBackupChainContiguity(
      final int partitionId,
      final NodePartitionId nodePartition,
      final List<BackupStatus> sortedBackups,
      final List<String> failures) {

    for (int i = 1; i < sortedBackups.size(); i++) {
      final var prevBackup = sortedBackups.get(i - 1);
      final var currBackup = sortedBackups.get(i);

      final var prevCheckpointPosition = prevBackup.descriptor().get().checkpointPosition();
      final var currFirstLogPosition = currBackup.descriptor().get().firstLogPosition();

      // firstLogPosition should be  <= prevCheckpointPosition + 1
      // segments are not "truncated" to remove initial positions
      if (currFirstLogPosition.isPresent()
          && currFirstLogPosition.getAsLong() > prevCheckpointPosition + 1) {
        failures.add(
            String.format(
                "Partition %d: %s has gap in log positions - backup %d ends at position %d, "
                    + "but backup %d starts at position %d (expected %d)",
                partitionId,
                nodePartition,
                prevBackup.id().checkpointId(),
                prevCheckpointPosition,
                currBackup.id().checkpointId(),
                currFirstLogPosition.getAsLong(),
                prevCheckpointPosition + 1));
        break; // Report first gap for this node-partition
      }
    }
  }

  private static List<BackupStatus> filterAndSortBackups(
      final Collection<BackupStatus> backups, final long safeStart, final long globalCheckpointId) {
    return backups.stream()
        .filter(b -> b.descriptor().isPresent())
        .filter(
            b -> b.id().checkpointId() >= safeStart && b.id().checkpointId() <= globalCheckpointId)
        .sorted(Comparator.comparing(b -> b.id().checkpointId()))
        .toList();
  }

  /**
   * Identifier for a (nodeId, partitionId) pair. Used to track which node is responsible for which
   * partition's backup.
   */
  public record NodePartitionId(int nodeId, int partitionId) {
    @Override
    public String toString() {
      return "Node" + nodeId + "P" + partitionId;
    }
  }

  /** Result of backup range validation. */
  public static final class ValidationResult {
    private final boolean valid;
    private final String errorMessage;

    private ValidationResult(final boolean valid, final String errorMessage) {
      this.valid = valid;
      this.errorMessage = errorMessage;
    }

    public static ValidationResult success() {
      return new ValidationResult(true, null);
    }

    public static ValidationResult failure(final String errorMessage) {
      return new ValidationResult(false, errorMessage);
    }

    public boolean isValid() {
      return valid;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    @Override
    public String toString() {
      return valid
          ? "ValidationResult{valid=true}"
          : "ValidationResult{valid=false, error='" + errorMessage + "'}";
    }
  }
}
