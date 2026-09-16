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

/**
 * Finds process definitions that were <b>partially deleted</b> across partitions and can never
 * reconcile.
 *
 * <p>Draining is reliable, so under normal operation every partition converges on the same view of
 * a definition. This scan detects the one rare exception left behind by a pre-draining defect: a
 * process-definition deletion issued while the deployment queue was blocked could reach some
 * partitions but not others. The deployment partition (P1) removed the definition, while other
 * partitions still carry it — and once the cluster upgrades to a draining-capable version those
 * stragglers surface as {@code DRAINING}. With the definition and its coordination bookkeeping gone
 * from P1, nothing remains to finalize the drain, so the definition is stranded and secondary
 * storage is left inconsistent with primary storage.
 *
 * <p>The concrete signature is cross-partition: a definition {@code DRAINING} on at least one
 * partition while <b>absent</b> from the deployment partition. Because process definitions are
 * deployed cluster-wide through P1, absence on P1 means deletion, not "never deployed".
 * Deliberately free of any RocksDB wiring so it can be unit-tested with hand-built partition maps.
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
   */
  List<Finding> scan(
      final Map<Integer, Map<Long, PersistedProcessState>> partitionStates,
      final Map<Long, DefinitionInfo> definitions) {
    final Map<Long, PersistedProcessState> deploymentStates =
        partitionStates.get(deploymentPartitionId);
    if (deploymentStates == null) {
      throw new IllegalArgumentException(
          "Missing state for the deployment partition (id " + deploymentPartitionId + ")");
    }

    final var findings = new ArrayList<Finding>();
    for (final var entry : definitions.entrySet()) {
      final long processDefinitionKey = entry.getKey();

      // Stranded only when the deployment partition no longer holds the definition: with P1 gone
      // there is no coordinator left, so the draining stragglers can never finalize.
      if (deploymentStates.containsKey(processDefinitionKey)) {
        continue;
      }

      final var drainingPartitions = new ArrayList<Integer>();
      final var absentPartitions = new ArrayList<Integer>();
      for (final var partition : partitionStates.entrySet()) {
        final PersistedProcessState state = partition.getValue().get(processDefinitionKey);
        if (state == null) {
          absentPartitions.add(partition.getKey());
        } else if (state == PersistedProcessState.DRAINING) {
          drainingPartitions.add(partition.getKey());
        }
      }

      if (!drainingPartitions.isEmpty()) {
        drainingPartitions.sort(null);
        absentPartitions.sort(null);
        findings.add(new Finding(entry.getValue(), drainingPartitions, absentPartitions));
      }
    }
    findings.sort(
        (a, b) ->
            Long.compare(
                a.definition().processDefinitionKey(), b.definition().processDefinitionKey()));
    return findings;
  }

  /** Display metadata for a definition, captured from a partition that still holds it. */
  record DefinitionInfo(
      long processDefinitionKey,
      String bpmnProcessId,
      int version,
      String tenantId,
      boolean deleteHistory) {}

  /**
   * A stranded, partially-deleted definition with the partitions still draining it and gone from
   * it.
   */
  record Finding(
      DefinitionInfo definition,
      List<Integer> drainingPartitions,
      List<Integer> absentPartitions) {}
}
