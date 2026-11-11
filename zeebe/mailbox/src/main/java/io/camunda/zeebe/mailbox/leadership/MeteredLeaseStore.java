/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

import io.camunda.zeebe.mailbox.StoreCallTimer;
import io.camunda.zeebe.mailbox.api.leadership.LeaseStore;
import io.camunda.zeebe.mailbox.api.leadership.PartitionLease;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A lease store that records the latency of the calls to another one. A renewal that takes longer
 * than the fence time makes the leader step down, so the tail of the renew latency tells how close
 * the leaders are to stepping down without a failure.
 */
public final class MeteredLeaseStore implements LeaseStore {

  public static final String STORE_LATENCY = "zeebe.lease.store.latency";

  private final LeaseStore delegate;
  private final StoreCallTimer timer;

  public MeteredLeaseStore(final LeaseStore delegate, final MeterRegistry registry) {
    this.delegate = delegate;
    timer =
        new StoreCallTimer(registry, STORE_LATENCY, "The latency of the calls to the lease store");
  }

  @Override
  public Optional<PartitionLease> claim(
      final int partitionId,
      final String nodeId,
      final String zone,
      final long observedTerm,
      final long observedCounter) {
    return timer.time(
        "claim", () -> delegate.claim(partitionId, nodeId, zone, observedTerm, observedCounter));
  }

  @Override
  public Set<Integer> renew(final String nodeId, final Map<Integer, Long> termsByPartition) {
    return timer.time("renew", () -> delegate.renew(nodeId, termsByPartition));
  }

  @Override
  public boolean release(final int partitionId, final String nodeId, final long term) {
    return timer.time("release", () -> delegate.release(partitionId, nodeId, term));
  }

  @Override
  public Optional<PartitionLease> findLease(final int partitionId) {
    return timer.time("findLease", () -> delegate.findLease(partitionId));
  }

  @Override
  public List<PartitionLease> findLeases() {
    return timer.time("findLeases", delegate::findLeases);
  }
}
