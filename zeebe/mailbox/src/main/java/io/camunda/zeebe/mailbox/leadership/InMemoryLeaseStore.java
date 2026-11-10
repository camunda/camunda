/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

import io.camunda.zeebe.mailbox.api.leadership.LeaseStore;
import io.camunda.zeebe.mailbox.api.leadership.PartitionLease;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link LeaseStore} in memory, for tests and a proof of concept in one process. All methods are
 * synchronized, so each takes effect atomically.
 */
public final class InMemoryLeaseStore implements LeaseStore {

  private final Map<Integer, PartitionLease> leases = new HashMap<>();

  @Override
  public synchronized Optional<PartitionLease> claim(
      final int partitionId,
      final String nodeId,
      final String zone,
      final long observedTerm,
      final long observedCounter) {
    final var current = leases.get(partitionId);
    final boolean matches =
        current == null
            ? observedTerm == NO_LEASE
            : current.term() == observedTerm && current.renewalCounter() == observedCounter;
    if (!matches) {
      return Optional.empty();
    }
    final var lease = new PartitionLease(partitionId, nodeId, zone, observedTerm + 1, 0, false);
    leases.put(partitionId, lease);
    return Optional.of(lease);
  }

  @Override
  public synchronized Set<Integer> renew(
      final String nodeId, final Map<Integer, Long> termsByPartition) {
    final var renewed = new HashSet<Integer>();
    termsByPartition.forEach(
        (partitionId, term) -> {
          final var current = leases.get(partitionId);
          if (current != null && isHeldBy(current, nodeId, term)) {
            leases.put(partitionId, withCounter(current, current.renewalCounter() + 1, false));
            renewed.add(partitionId);
          }
        });
    return renewed;
  }

  @Override
  public synchronized boolean release(final int partitionId, final String nodeId, final long term) {
    final var current = leases.get(partitionId);
    if (current == null || !isHeldBy(current, nodeId, term)) {
      return false;
    }
    leases.put(partitionId, withCounter(current, current.renewalCounter() + 1, true));
    return true;
  }

  @Override
  public synchronized Optional<PartitionLease> findLease(final int partitionId) {
    return Optional.ofNullable(leases.get(partitionId));
  }

  @Override
  public synchronized List<PartitionLease> findLeases() {
    return List.copyOf(leases.values());
  }

  private static boolean isHeldBy(
      final PartitionLease lease, final String nodeId, final long term) {
    return !lease.released() && lease.term() == term && lease.leaderId().equals(nodeId);
  }

  private static PartitionLease withCounter(
      final PartitionLease lease, final long counter, final boolean released) {
    return new PartitionLease(
        lease.partitionId(), lease.leaderId(), lease.zone(), lease.term(), counter, released);
  }
}
