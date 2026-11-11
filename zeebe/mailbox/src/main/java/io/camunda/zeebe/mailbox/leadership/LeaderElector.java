/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

import io.camunda.zeebe.mailbox.Scheduler;
import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import io.camunda.zeebe.mailbox.api.leadership.LeaseStore;
import io.camunda.zeebe.mailbox.api.leadership.PartitionLease;
import io.camunda.zeebe.mailbox.leadership.LeadershipListener.Reason;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elects the leaders of the partitions with leases in the database, for one node.
 *
 * <p>Every renew interval it does, in this order:
 *
 * <ol>
 *   <li><b>Renew</b> the leases this node holds, in one call. A partition that the store does not
 *       renew was taken over, and this node stops leading it.
 *   <li><b>Fence</b> every partition whose lease this node has not renewed for {@code fenceAfter}.
 *       This happens before an observer can see the lease run out, so the node stops leading before
 *       another node claims.
 *   <li><b>Observe</b> all lease rows in one read. After a failed read the observations start
 *       again.
 *   <li><b>Claim</b> the partitions that the {@link ClaimPolicy} gives to this node, each with a
 *       compare-and-set on the term and counter it read.
 * </ol>
 *
 * <p>Time is measured on this node's monotonic clock only. All work runs on the thread of the
 * scheduler, which may block on the store.
 */
public final class LeaderElector {

  private static final Logger LOG = LoggerFactory.getLogger(LeaderElector.class);

  private final LeaseStore store;
  private final LeaseConfig config;
  private final String nodeId;
  private final String zone;
  private final Set<Integer> partitions;
  private final ClusterView view;
  private final LeadershipListener listener;
  private final Scheduler scheduler;
  private final LongSupplier nanoClock;
  private final ClaimPolicy policy;
  private final LeaseObserver observer;
  private final Map<Integer, Held> held = new HashMap<>();
  private boolean closed;

  /**
   * @param partitions the partitions of the cluster. Any node may lead any of them.
   * @param scheduler runs the loop. Its thread may block.
   * @param nanoClock a monotonic clock in nanoseconds
   */
  public LeaderElector(
      final LeaseStore store,
      final LeaseConfig config,
      final String nodeId,
      final String zone,
      final Set<Integer> partitions,
      final ClusterView view,
      final LeadershipListener listener,
      final Scheduler scheduler,
      final LongSupplier nanoClock) {
    this.store = store;
    this.config = config;
    this.nodeId = nodeId;
    this.zone = zone;
    this.partitions = new TreeSet<>(partitions);
    this.view = view;
    this.listener = listener;
    this.scheduler = scheduler;
    this.nanoClock = nanoClock;
    policy = new ClaimPolicy(nodeId, zone);
    observer = new LeaseObserver(config.leaseDuration());
  }

  public void start() {
    scheduler.schedule(this::run, Duration.ZERO);
  }

  /**
   * Gives up a partition that this node leads, so that the next node of its region claims it. The
   * call to the store runs on the scheduler's thread, since it may block.
   *
   * @return completes when the node no longer leads the partition. Fails if the store could not
   *     release the lease; the node then keeps leading, because otherwise it would take its own
   *     unreleased lease back at once.
   */
  public CompletableFuture<Void> stepDown(final int partitionId) {
    final var result = new CompletableFuture<Void>();
    scheduler.schedule(
        () -> {
          try {
            stepDownNow(partitionId);
            result.complete(null);
          } catch (final MailboxStoreException e) {
            result.completeExceptionally(e);
          }
        },
        Duration.ZERO);
    return result;
  }

  /** Runs a step-down on the calling thread. Package-private, so a test can call it directly. */
  synchronized void stepDownNow(final int partitionId) {
    final var lease = held.get(partitionId);
    if (lease == null) {
      return;
    }
    store.release(partitionId, nodeId, lease.term());
    stopLeading(partitionId, lease.term(), Reason.RELEASED);
  }

  /**
   * Stops the loop and gives up the partitions that this node leads. The other nodes of the region
   * claim them at once; the other region after the lease duration.
   */
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    for (final var entry : Map.copyOf(held).entrySet()) {
      final int partitionId = entry.getKey();
      final long term = entry.getValue().term();
      try {
        store.release(partitionId, nodeId, term);
      } catch (final MailboxStoreException e) {
        LOG.warn(
            "Could not release the lease of partition {}; it runs out instead", partitionId, e);
      }
      stopLeading(partitionId, term, Reason.RELEASED);
    }
  }

  /** The partitions that this node leads, with their terms. */
  public synchronized Map<Integer, Long> leading() {
    return held.entrySet().stream()
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().term()));
  }

  private void run() {
    if (tick()) {
      scheduler.schedule(this::run, config.renewInterval());
    }
  }

  /**
   * Runs one round. Package-private, so a test can drive the rounds itself.
   *
   * @return false once the elector is closed
   */
  synchronized boolean tick() {
    if (closed) {
      return false;
    }
    renew();
    fence();
    final var leases = read();
    if (leases != null) {
      claim(leases);
    }
    return true;
  }

  private void renew() {
    if (held.isEmpty()) {
      return;
    }
    final var terms = leading();
    final Set<Integer> renewed;
    final long startedNanos = nanoClock.getAsLong();
    try {
      renewed = store.renew(nodeId, terms);
    } catch (final MailboxStoreException e) {
      LOG.warn("Could not renew the leases of {}", terms.keySet(), e);
      return;
    }
    terms.forEach(
        (partitionId, term) -> {
          if (renewed.contains(partitionId)) {
            // counted from before the call, since the renewal may have taken effect at its start
            held.put(partitionId, new Held(term, startedNanos));
          } else {
            stopLeading(partitionId, term, Reason.TAKEN_OVER);
          }
        });
  }

  private void fence() {
    final long now = nanoClock.getAsLong();
    final long limit = config.fenceAfter().toNanos();
    for (final var entry : Map.copyOf(held).entrySet()) {
      if (now - entry.getValue().renewedNanos() >= limit) {
        stopLeading(entry.getKey(), entry.getValue().term(), Reason.FENCED);
      }
    }
  }

  private @Nullable Map<Integer, PartitionLease> read() {
    final List<PartitionLease> leases;
    try {
      leases = store.findLeases();
    } catch (final MailboxStoreException e) {
      LOG.debug("Could not read the leases; observations start again", e);
      observer.reset();
      return null;
    }
    observer.observe(leases, nanoClock.getAsLong());
    return leases.stream()
        .collect(Collectors.toMap(PartitionLease::partitionId, Function.identity()));
  }

  private void claim(final Map<Integer, PartitionLease> leases) {
    final long now = nanoClock.getAsLong();
    for (final int partitionId : partitions) {
      if (held.containsKey(partitionId)) {
        continue;
      }
      final var lease = leases.get(partitionId);
      if (!policy.shouldClaim(partitionId, lease, observer.hasRunOut(partitionId, now), view)) {
        continue;
      }
      final long observedTerm = lease == null ? LeaseStore.NO_LEASE : lease.term();
      final long observedCounter = lease == null ? 0 : lease.renewalCounter();
      final long startedNanos = nanoClock.getAsLong();
      try {
        store
            .claim(partitionId, nodeId, zone, observedTerm, observedCounter)
            .ifPresent(
                claimed -> {
                  held.put(partitionId, new Held(claimed.term(), startedNanos));
                  listener.onLeading(partitionId, claimed.term());
                });
      } catch (final MailboxStoreException e) {
        // the claim may have taken effect; if so, this node sees its own lease on the next read
        // and claims it again
        LOG.warn("Could not claim partition {}", partitionId, e);
      }
    }
  }

  private void stopLeading(final int partitionId, final long term, final Reason reason) {
    held.remove(partitionId);
    listener.onNotLeading(partitionId, term, reason);
  }

  private record Held(long term, long renewedNanos) {}
}
