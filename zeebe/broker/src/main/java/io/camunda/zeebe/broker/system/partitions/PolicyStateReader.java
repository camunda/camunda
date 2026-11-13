/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.immutable.PolicyState.CommittedPolicy;
import io.camunda.zeebe.engine.state.policy.DbPolicyState;
import io.camunda.zeebe.engine.state.policy.DbPolicyUpdateState;
import io.camunda.zeebe.engine.state.policy.PersistedPolicyUpdate;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Reads the policy state of a partition outside the stream processor, for the Hub receiver (ADR
 * 0015, D2). A read runs on the partition's actor and uses the {@link ZeebeDb} that is open at that
 * moment, as {@link ZeebePartitionAdminAccess} does for the migration status.
 *
 * <p>A read shows the state after the last processed command. It must not decide anything that has
 * to be exact; the engine decides those while it processes a command.
 */
@NullMarked
public final class PolicyStateReader {

  private final ConcurrencyControl concurrencyControl;
  private final PartitionAdminControl adminControl;

  // Built on the first read and reused, because each build opens a transaction context that is
  // never closed. Rebuilt only when the partition opens another ZeebeDb, e.g. after a snapshot.
  private @Nullable ZeebeDb<ZbColumnFamilies> stateDb;
  private @Nullable DbPolicyState policyState;
  private @Nullable DbPolicyUpdateState policyUpdateState;

  PolicyStateReader(
      final ConcurrencyControl concurrencyControl, final PartitionAdminControl adminControl) {
    this.concurrencyControl = requireNonNull(concurrencyControl);
    this.adminControl = requireNonNull(adminControl);
  }

  /** The policy state of the partition, or empty if the partition has no open state yet. */
  public ActorFuture<Optional<PolicyStatus>> getPolicyState() {
    final ActorFuture<Optional<PolicyStatus>> future = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          try {
            future.complete(readPolicyState());
          } catch (final Exception e) {
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  @SuppressWarnings("unchecked")
  private Optional<PolicyStatus> readPolicyState() {
    final ZeebeDb<ZbColumnFamilies> zeebeDb = adminControl.getZeebeDb();
    if (zeebeDb == null) {
      return Optional.empty();
    }
    if (zeebeDb != stateDb || policyState == null || policyUpdateState == null) {
      final var context = zeebeDb.createContext();
      policyState = new DbPolicyState(zeebeDb, context);
      policyUpdateState = new DbPolicyUpdateState(zeebeDb, context);
      stateDb = zeebeDb;
    }

    final var openUpdate = policyUpdateState.getOpenUpdate();
    return Optional.of(
        new PolicyStatus(
            policyState.getCurrentPolicy(),
            openUpdate.map(PolicyStateReader::statusOf).orElse(UpdateStatus.NONE),
            openUpdate.map(update -> update.getUpdate().getUpdateId()).orElse(null)));
  }

  private static UpdateStatus statusOf(final PersistedPolicyUpdate update) {
    return update.isApplying() ? UpdateStatus.APPLYING : UpdateStatus.PENDING;
  }

  /** The status of the update that is open on the partition. */
  public enum UpdateStatus {
    /** No update is open: the last one was committed, or there was none. */
    NONE,
    /** Some chunks of the update are buffered, but not all of them. */
    PENDING,
    /** All chunks of the update are buffered, and the update is applied but not committed yet. */
    APPLYING
  }

  /**
   * The policy state of a partition.
   *
   * @param committed the committed policy, if a policy update was committed
   * @param updateStatus the status of the open update
   * @param openUpdateId the id of the open update, or {@code null} if no update is open
   */
  public record PolicyStatus(
      Optional<CommittedPolicy> committed,
      UpdateStatus updateStatus,
      @Nullable String openUpdateId) {

    /** Whether an update is open, so the Hub receiver must not send the next one. */
    public boolean hasOpenUpdate() {
      return updateStatus != UpdateStatus.NONE;
    }

    /** The Hub sequence of the committed policy, or 0 if no policy update was committed. */
    public long committedSeqNo() {
      return committed.map(CommittedPolicy::seqNo).orElse(0L);
    }
  }
}
