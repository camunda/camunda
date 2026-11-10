/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.mailbox;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.PartitionLeader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

/**
 * Records in the store that this node leads a partition, and removes the record when it stops. A
 * gateway that cannot see the leader in the cluster topology reads the record.
 *
 * <p>The store may block, and the callers are actors, so the calls run on an executor. They run one
 * after another, so a removal never overtakes the record that it removes. A failure is logged and
 * does not stop the leader, which can still serve the requests that reach it over the network.
 *
 * <p>Not thread-safe. The callers are the transitions of one partition, which run one at a time.
 */
public final class LeaderPublisher {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final MailboxStore store;
  private final Executor executor;
  private final int partitionId;
  private final String nodeId;
  private final String zone;
  private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

  /**
   * @param zone the zone of this node
   */
  public LeaderPublisher(
      final MailboxStore store,
      final Executor executor,
      final int partitionId,
      final String nodeId,
      final String zone) {
    this.store = store;
    this.executor = executor;
    this.partitionId = partitionId;
    this.nodeId = nodeId;
    this.zone = zone;
  }

  /** Records this node as the leader of the term. */
  public void publish(final long term) {
    run(
        () -> {
          if (!store.putLeader(new PartitionLeader(partitionId, nodeId, zone, term))) {
            LOG.debug("Partition {} has a leader with a higher term than {}", partitionId, term);
          }
        },
        "record the leader of partition " + partitionId);
  }

  /** Removes the record of this node as the leader of the term. */
  public void withdraw(final long term) {
    run(() -> store.removeLeader(partitionId, nodeId, term), "remove leader of " + partitionId);
  }

  private void run(final Runnable task, final String description) {
    tail =
        tail.thenRunAsync(
            () -> {
              try {
                task.run();
              } catch (final RuntimeException e) {
                LOG.warn("Could not {} in the mailbox", description, e);
              }
            },
            executor);
  }
}
