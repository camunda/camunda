/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.rdbms;

import io.camunda.zeebe.mailbox.api.CleanupResult;
import io.camunda.zeebe.mailbox.api.GatewayId;
import io.camunda.zeebe.mailbox.api.InboxMessage;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.mailbox.api.NewInboxMessage;
import io.camunda.zeebe.mailbox.api.OutboxResponse;
import io.camunda.zeebe.mailbox.api.PartitionLeader;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * A {@link MailboxStore} in a relational database. Each operation is one transaction.
 *
 * <p>Claim timeouts and expiry use the clock of the database, read at the start of each transaction
 * that needs it, so the nodes of both regions agree on them whatever their own clocks say. A claim
 * locks the messages it hands out, so concurrent claimers never take the same one.
 */
public final class MyBatisMailboxStore implements MailboxStore {

  private final Transactions transactions;
  private final Supplier<Duration> clockOffset;

  public MyBatisMailboxStore(final SqlSessionFactory sessionFactory) {
    this(sessionFactory, () -> Duration.ZERO);
  }

  /**
   * @param clockOffset added to the database clock. Lets a test move the clock of the store.
   */
  MyBatisMailboxStore(
      final SqlSessionFactory sessionFactory, final Supplier<Duration> clockOffset) {
    transactions = new Transactions(sessionFactory);
    this.clockOffset = clockOffset;
  }

  @Override
  public boolean insert(final NewInboxMessage message) {
    return insertUnlessExists(
        "insert a message into the inbox of partition " + message.partitionId(),
        (session, now) ->
            mapper(session)
                .insertMessage(
                    message.partitionId(),
                    message.kind().name(),
                    message.sender(),
                    message.requestId(),
                    message.payload(),
                    now));
  }

  @Override
  public List<InboxMessage> claim(
      final int partitionId,
      final String claimerId,
      final Set<MessageKind> kinds,
      final int maxMessages,
      final Duration claimTimeout) {
    if (claimerId.isBlank()) {
      throw new IllegalArgumentException("claimerId must not be blank");
    }
    if (kinds.isEmpty()) {
      throw new IllegalArgumentException("kinds must not be empty");
    }
    if (maxMessages < 1) {
      throw new IllegalArgumentException("maxMessages must be at least 1");
    }

    final var kindNames = kinds.stream().map(Enum::name).toList();
    return transactions.run(
        "claim messages of partition " + partitionId,
        session -> {
          final var mapper = mapper(session);
          final long now = now(mapper);
          final var sequences =
              mapper.selectAvailableSequencesForUpdate(partitionId, kindNames, now, maxMessages);
          if (sequences.isEmpty()) {
            return List.of();
          }
          mapper.claimMessages(sequences, claimerId, now + claimTimeout.toMillis());
          return mapper.selectMessages(sequences);
        });
  }

  @Override
  public int acknowledge(
      final int partitionId, final String claimerId, final Collection<Long> sequences) {
    if (sequences.isEmpty()) {
      return 0;
    }
    final var distinct = Set.copyOf(sequences);
    return transactions.run(
        "acknowledge messages of partition " + partitionId,
        session -> mapper(session).deleteClaimedMessages(partitionId, claimerId, distinct));
  }

  @Override
  public int releaseClaims(final int partitionId, final String claimerId) {
    return transactions.run(
        "release the claims on partition " + partitionId,
        session -> mapper(session).releaseClaims(partitionId, claimerId));
  }

  @Override
  public boolean putResponse(final OutboxResponse response) {
    return insertUnlessExists(
        "put a response into the outbox of gateway " + response.gatewayId().value(),
        (session, now) ->
            mapper(session)
                .insertResponse(
                    response.gatewayId().value(), response.requestId(), response.payload(), now));
  }

  @Override
  public List<OutboxResponse> takeResponses(final GatewayId gatewayId, final Set<Long> requestIds) {
    if (requestIds.isEmpty()) {
      return List.of();
    }
    return transactions.run(
        "take the responses of gateway " + gatewayId.value(),
        session -> {
          final var mapper = mapper(session);
          final var rows = mapper.selectResponsesForUpdate(gatewayId.value(), requestIds);
          if (rows.isEmpty()) {
            return List.of();
          }
          mapper.deleteResponses(
              gatewayId.value(), rows.stream().map(MailboxMapper.ResponseRow::requestId).toList());
          return rows.stream()
              .map(row -> new OutboxResponse(gatewayId, row.requestId(), row.payload()))
              .toList();
        });
  }

  /**
   * Locks the record of the partition to compare the terms. If there is none, a peer may insert one
   * at the same time; the insert then fails on the key and the comparison starts over.
   */
  @Override
  public boolean putLeader(final PartitionLeader leader) {
    while (true) {
      final var stored =
          transactions.run(
              "record the leader of partition " + leader.partitionId(),
              session -> putLeader(session, leader));
      if (stored.isPresent()) {
        return stored.get();
      }
    }
  }

  @Override
  public boolean removeLeader(final int partitionId, final String nodeId, final long term) {
    return transactions.run(
        "remove the leader of partition " + partitionId,
        session -> mapper(session).deleteLeader(partitionId, nodeId, term) == 1);
  }

  @Override
  public Optional<PartitionLeader> findLeader(final int partitionId) {
    return transactions.run(
        "find the leader of partition " + partitionId,
        session -> Optional.ofNullable(mapper(session).selectLeader(partitionId)));
  }

  @Override
  public List<PartitionLeader> findLeaders() {
    return transactions.run(
        "find the leaders of all partitions", session -> mapper(session).selectLeaders());
  }

  @Override
  public CleanupResult deleteExpired(final Duration inboxTtl, final Duration outboxTtl) {
    return transactions.run(
        "delete expired messages and responses",
        session -> {
          final var mapper = mapper(session);
          final long now = now(mapper);
          return new CleanupResult(
              mapper.deleteMessagesCreatedAtOrBefore(now - inboxTtl.toMillis()),
              mapper.deleteResponsesCreatedAtOrBefore(now - outboxTtl.toMillis()));
        });
  }

  /** Empty if a peer inserted the record at the same time, so the caller must try again. */
  private Optional<Boolean> putLeader(final SqlSession session, final PartitionLeader leader) {
    final var mapper = mapper(session);
    final var term = mapper.selectLeaderTermForUpdate(leader.partitionId());
    if (term != null) {
      if (term > leader.term()) {
        return Optional.of(false);
      }
      mapper.updateLeader(leader);
      return Optional.of(true);
    }
    try {
      mapper.insertLeader(leader);
      return Optional.of(true);
    } catch (final PersistenceException e) {
      if (!Transactions.isDuplicateKey(e)) {
        throw e;
      }
      session.rollback(true);
      return Optional.empty();
    }
  }

  /** Runs the insert, and returns false instead of failing if the key exists already. */
  private boolean insertUnlessExists(final String operation, final Insert insert) {
    return transactions.run(
        operation,
        session -> {
          final long now = now(mapper(session));
          try {
            insert.run(session, now);
            return true;
          } catch (final PersistenceException e) {
            if (!Transactions.isDuplicateKey(e)) {
              throw e;
            }
            session.rollback(true);
            return false;
          }
        });
  }

  private long now(final MailboxMapper mapper) {
    return mapper.selectNow().plus(clockOffset.get()).toEpochMilli();
  }

  private static MailboxMapper mapper(final SqlSession session) {
    return session.getMapper(MailboxMapper.class);
  }

  @FunctionalInterface
  private interface Insert {
    void run(SqlSession session, long now);
  }
}
