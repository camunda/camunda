/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.hub;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.mutable.MutableHubEventState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the Hub events of partition 1 under their Hub sequence, at most three: the last finished
 * one, the last applied one if that is an earlier one, and the one in progress after them. A new
 * event in progress replaces the one in progress. A finished event replaces every other entry,
 * except the last applied one if the new event is rejected.
 */
@NullMarked
public final class DbHubEventState implements MutableHubEventState {

  private final boolean receivesHubEvents;
  private final DbLong seqNoKey = new DbLong();
  private final PersistedHubEvent hubEvent = new PersistedHubEvent();
  private final ColumnFamily<DbLong, PersistedHubEvent> hubEventColumnFamily;

  public DbHubEventState(
      final int partitionId,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext) {
    receivesHubEvents = partitionId == Protocol.DEPLOYMENT_PARTITION;
    hubEventColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.HUB_EVENT, transactionContext, seqNoKey, hubEvent);
  }

  @Override
  public Optional<HubEvent> getLast() {
    return findLast(status -> true);
  }

  @Override
  public Optional<HubEvent> getLastFinished() {
    return findLast(HubEventStatus::isFinished);
  }

  @Override
  public OptionalLong getLastAppliedSeqNo() {
    return findLast(status -> status == HubEventStatus.APPLIED)
        .map(event -> OptionalLong.of(event.seqNo()))
        .orElse(OptionalLong.empty());
  }

  @Override
  public void setStatus(
      final long seqNo,
      final HubEventType type,
      final HubEventStatus status,
      final @Nullable String reason) {
    if (!receivesHubEvents) {
      return;
    }
    final var lastFinished = getLastFinished();
    if (lastFinished.isPresent() && seqNo <= lastFinished.get().seqNo()) {
      return;
    }

    final OptionalLong keptApplied =
        status == HubEventStatus.REJECTED ? getLastAppliedSeqNo() : OptionalLong.empty();
    final var obsolete = new ArrayList<Long>();
    hubEventColumnFamily.forEach(
        (key, event) -> {
          final long storedSeqNo = key.getValue();
          final boolean kept =
              status.isFinished()
                  ? keptApplied.isPresent() && keptApplied.getAsLong() == storedSeqNo
                  : event.getStatus().isFinished();
          if (!kept) {
            obsolete.add(storedSeqNo);
          }
        });
    obsolete.forEach(
        storedSeqNo -> {
          seqNoKey.wrapLong(storedSeqNo);
          hubEventColumnFamily.deleteExisting(seqNoKey);
        });

    hubEvent.set(seqNo, type, status, reason);
    seqNoKey.wrapLong(seqNo);
    hubEventColumnFamily.upsert(seqNoKey, hubEvent);
  }

  /** The stored event with the highest Hub sequence whose status matches. */
  private Optional<HubEvent> findLast(final Predicate<HubEventStatus> matches) {
    final var found = new HubEvent[1];
    seqNoKey.wrapLong(Long.MAX_VALUE);
    hubEventColumnFamily.whileTrueReverse(
        seqNoKey,
        (key, event) -> {
          if (!matches.test(event.getStatus())) {
            return true;
          }
          found[0] = event.toHubEvent();
          return false;
        });
    return Optional.ofNullable(found[0]);
  }
}
