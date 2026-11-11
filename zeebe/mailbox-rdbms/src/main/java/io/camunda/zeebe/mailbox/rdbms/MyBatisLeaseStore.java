/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.rdbms;

import io.camunda.zeebe.mailbox.api.leadership.LeaseStore;
import io.camunda.zeebe.mailbox.api.leadership.PartitionLease;
import io.camunda.zeebe.mailbox.rdbms.LeaseMapper.PartitionTerm;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * A {@link LeaseStore} in a relational database. Each operation is one transaction. A claim is a
 * conditional update on the term and renewal counter that the caller observed, so of several nodes
 * that claim at once, the database lets exactly one win.
 */
public final class MyBatisLeaseStore implements LeaseStore {

  private final Transactions transactions;

  public MyBatisLeaseStore(final SqlSessionFactory sessionFactory) {
    transactions = new Transactions(sessionFactory);
  }

  @Override
  public Optional<PartitionLease> claim(
      final int partitionId,
      final String nodeId,
      final String zone,
      final long observedTerm,
      final long observedCounter) {
    return transactions.run(
        "claim the lease of partition " + partitionId,
        session -> {
          final var mapper = mapper(session);
          final boolean claimed =
              observedTerm == NO_LEASE
                  ? insertFirstLease(session, partitionId, nodeId, zone)
                  : mapper.takeOverLease(partitionId, nodeId, zone, observedTerm, observedCounter)
                      == 1;
          return claimed ? Optional.ofNullable(mapper.selectLease(partitionId)) : Optional.empty();
        });
  }

  /**
   * Renews with one update for all partitions, then reads which of them the node still holds: the
   * update holds the row locks, so the read sees exactly the rows it changed.
   */
  @Override
  public Set<Integer> renew(final String nodeId, final Map<Integer, Long> termsByPartition) {
    if (termsByPartition.isEmpty()) {
      return Set.of();
    }
    final var terms =
        termsByPartition.entrySet().stream()
            .map(entry -> new PartitionTerm(entry.getKey(), entry.getValue()))
            .toList();
    return transactions.run(
        "renew the leases of " + nodeId,
        session -> {
          final var mapper = mapper(session);
          if (mapper.renewLeases(nodeId, terms) == 0) {
            return Set.of();
          }
          return Set.copyOf(mapper.selectHeldPartitions(nodeId, terms));
        });
  }

  @Override
  public boolean release(final int partitionId, final String nodeId, final long term) {
    return transactions.run(
        "release the lease of partition " + partitionId,
        session -> mapper(session).releaseLease(partitionId, nodeId, term) == 1);
  }

  @Override
  public Optional<PartitionLease> findLease(final int partitionId) {
    return transactions.run(
        "find the lease of partition " + partitionId,
        session -> Optional.ofNullable(mapper(session).selectLease(partitionId)));
  }

  @Override
  public List<PartitionLease> findLeases() {
    return transactions.run(
        "find the leases of all partitions", session -> mapper(session).selectLeases());
  }

  /** False if another node inserted the first lease of the partition before. */
  private static boolean insertFirstLease(
      final SqlSession session, final int partitionId, final String nodeId, final String zone) {
    try {
      mapper(session).insertLease(partitionId, nodeId, zone);
      return true;
    } catch (final PersistenceException e) {
      if (!Transactions.isDuplicateKey(e)) {
        throw e;
      }
      session.rollback(true);
      return false;
    }
  }

  private static LeaseMapper mapper(final SqlSession session) {
    return session.getMapper(LeaseMapper.class);
  }
}
