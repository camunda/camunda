/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds process definitions that are <b>draining without valid coordination</b> and can therefore
 * never finish deleting.
 *
 * <p>A definition scheduled for deletion is marked {@code DRAINING} on the partitions that still
 * hold it, and the deployment partition (P1) records one {@code
 * PENDING_PROCESS_DELETIONS_PER_PARTITION} coordination entry per still-draining partition. Each
 * partition removes the definition locally once its last instance finishes and reports back, which
 * clears its entry on P1; when the last entry clears, the definition is fully deleted. During a
 * healthy in-progress drain every {@code DRAINING} partition therefore has a matching pending entry
 * on P1 — <b>even after P1 has removed the definition from its own state</b>, since P1 keeps the
 * other partitions' entries until they report.
 *
 * <p>A pre-draining defect could leave a definition {@code DRAINING} on a partition with no
 * matching pending entry on P1: the coordination was never set up, so nothing will ever finalize
 * the drain and the definition is stuck. The signature is exactly that mismatch — {@code DRAINING}
 * on a partition that P1 is not tracking as pending — not P1's absence of the definition, which
 * also occurs in a normal drain and would be a false positive.
 *
 * <p>Deliberately free of any RocksDB wiring so it can be unit-tested with hand-built partition
 * maps.
 */
final class ProcessDefinitionDeletionScan {

  private final int deploymentPartitionId;

  ProcessDefinitionDeletionScan(final int deploymentPartitionId) {
    this.deploymentPartitionId = deploymentPartitionId;
  }

  /**
   * @param partitionStates per-partition view of {@code PROCESS_CACHE}: partition id → (definition
   *     key → state). Must include the deployment partition.
   * @param definitions metadata for every definition seen {@code DRAINING} on any partition, keyed
   *     by definition key, used only for reporting.
   * @param pendingByDefinition the deployment partition's coordination view: definition key → set
   *     of partition ids P1 still tracks as pending for that definition. A draining partition
   *     absent from this set has no coordination.
   * @param definitionsWithActiveInstances definition keys that still have at least one active
   *     process instance on any scanned partition, used to flag remaining orphaned instances.
   */
  List<Finding> scan(
      final Map<Integer, Map<Long, PersistedProcessState>> partitionStates,
      final Map<Long, DefinitionInfo> definitions,
      final Map<Long, Set<Integer>> pendingByDefinition,
      final Set<Long> definitionsWithActiveInstances) {
    if (!partitionStates.containsKey(deploymentPartitionId)) {
      throw new IllegalArgumentException(
          "Missing state for the deployment partition (id " + deploymentPartitionId + ")");
    }

    final var findings = new ArrayList<Finding>();
    for (final var entry : definitions.entrySet()) {
      final long processDefinitionKey = entry.getKey();
      final Set<Integer> pendingPartitions =
          pendingByDefinition.getOrDefault(processDefinitionKey, Set.of());

      final var drainingPartitions = new ArrayList<Integer>();
      final var uncoordinatedPartitions = new ArrayList<Integer>();
      for (final var partition : partitionStates.entrySet()) {
        final PersistedProcessState state = partition.getValue().get(processDefinitionKey);
        if (state != PersistedProcessState.DRAINING) {
          continue;
        }
        final int partitionId = partition.getKey();
        drainingPartitions.add(partitionId);
        // Draining but P1 is not tracking this partition as pending: coordination is missing, so
        // this partition can never finalize the drain on its own.
        if (!pendingPartitions.contains(partitionId)) {
          uncoordinatedPartitions.add(partitionId);
        }
      }

      if (!uncoordinatedPartitions.isEmpty()) {
        drainingPartitions.sort(null);
        uncoordinatedPartitions.sort(null);
        findings.add(
            new Finding(
                entry.getValue(),
                drainingPartitions,
                uncoordinatedPartitions,
                definitionsWithActiveInstances.contains(processDefinitionKey)));
      }
    }
    findings.sort(
        (a, b) ->
            Long.compare(
                a.definition().processDefinitionKey(), b.definition().processDefinitionKey()));
    return findings;
  }

  record DefinitionInfo(
      long processDefinitionKey, String bpmnProcessId, int version, String tenantId) {}

  /**
   * {@code uncoordinatedPartitions} is the subset of {@code drainingPartitions} that the deployment
   * partition holds no pending entry for — the reason the definition is stuck.
   */
  record Finding(
      DefinitionInfo definition,
      List<Integer> drainingPartitions,
      List<Integer> uncoordinatedPartitions,
      boolean orphanedInstances) {}
}
