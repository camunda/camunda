/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api.leadership;

import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Storage of the lease rows that decide who leads each partition.
 *
 * <p>An implementation must pass {@code LeaseStoreContractTest} from the test-jar of this module.
 * The rules of {@code MailboxStore} apply: calls may block, each operation is atomic, and failures
 * are thrown as {@link MailboxStoreException}. The store keeps no time; the lease duration is
 * measured by the nodes that observe the rows.
 */
public interface LeaseStore {

  /** The observed term to pass to {@link #claim} for a partition that has no lease row yet. */
  long NO_LEASE = 0;

  /**
   * Takes over a partition with a compare-and-set. It succeeds only if the row still has the term
   * and counter that the caller observed, so of several nodes that claim at the same time, at most
   * one wins.
   *
   * @param partitionId the partition
   * @param nodeId the node that claims, not blank
   * @param zone the zone of the node, not blank
   * @param observedTerm the term that the caller read, or {@link #NO_LEASE} if it read no row
   * @param observedCounter the renewal counter that the caller read. Ignored with {@link
   *     #NO_LEASE}.
   * @return the new lease, with the observed term plus one and a counter of 0, or empty if the row
   *     changed since the caller read it
   */
  Optional<PartitionLease> claim(
      int partitionId, String nodeId, String zone, long observedTerm, long observedCounter);

  /**
   * Renews the leases of a node, all in one transaction. A lease is renewed only if the node still
   * holds it in the given term and has not released it.
   *
   * @param nodeId the node that leads
   * @param termsByPartition the term in which the node leads each partition
   * @return the partitions that were renewed. A partition that is missing was taken by another node
   *     or released, and the node must stop leading it.
   */
  Set<Integer> renew(String nodeId, Map<Integer, Long> termsByPartition);

  /**
   * Gives a partition up, so that another node can claim it without waiting for the lease to run
   * out. The row keeps its term, and its counter increases.
   *
   * @return true if the node held the partition in that term
   */
  boolean release(int partitionId, String nodeId, long term);

  /** Returns the lease row of a partition, or empty if nobody has claimed it yet. */
  Optional<PartitionLease> findLease(int partitionId);

  /**
   * Returns all lease rows, one per partition, in any order. An observer reads them in one call.
   */
  List<PartitionLease> findLeases();
}
