/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import io.camunda.zeebe.mailbox.api.PartitionLeader;
import io.camunda.zeebe.mailbox.api.leadership.LeaseStore;
import io.camunda.zeebe.mailbox.api.leadership.PartitionLease;
import java.time.Duration;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * The leaders whose leases are alive, for a node that only reads, such as a gateway. A lease that
 * was released, or that has not been renewed for the lease duration, does not count, so a crashed
 * leader drops out instead of staying recorded.
 *
 * <p>The lease duration is measured as the elector measures it: on the reader's own clock, from
 * when it first saw the counter unchanged. After a failed read the measuring starts again.
 */
public final class LiveLeaders {

  private final LeaseStore store;
  private final LongSupplier nanoClock;
  private final LeaseObserver observer;

  /**
   * @param leaseDuration the lease duration of the brokers
   * @param nanoClock a monotonic clock in nanoseconds
   */
  public LiveLeaders(
      final LeaseStore store, final Duration leaseDuration, final LongSupplier nanoClock) {
    this.store = store;
    this.nanoClock = nanoClock;
    observer = new LeaseObserver(leaseDuration);
  }

  /**
   * Reads the leases and returns the leaders that are alive. It blocks on the store.
   *
   * @throws MailboxStoreException if the store cannot be read
   */
  public synchronized List<PartitionLeader> read() {
    final var leases = readOrReset();
    final long now = nanoClock.getAsLong();
    observer.observe(leases, now);
    return leases.stream()
        .filter(lease -> !lease.released() && !observer.hasRunOut(lease.partitionId(), now))
        .map(
            lease ->
                new PartitionLeader(
                    lease.partitionId(), lease.leaderId(), lease.zone(), lease.term()))
        .toList();
  }

  private List<PartitionLease> readOrReset() {
    try {
      return store.findLeases();
    } catch (final MailboxStoreException e) {
      observer.reset();
      throw e;
    }
  }
}
