/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.hub;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.system.partitions.PolicyStateReader;
import io.camunda.zeebe.broker.system.partitions.PolicyStateReader.PolicyStatus;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Connects Hub to the engine of one physical tenant (ADR 0015). It runs while its broker leads
 * partition 1 of the tenant: it polls Hub from the stored sequence, hands each policy update to the
 * {@link PolicyInPort}, and polls again only once the engine has committed the update.
 *
 * <p>The receiver reads the policy state through the {@link PolicyStateReader} of partition 1. A
 * read can be a little behind the log, so the receiver never decides anything that has to be exact
 * from it; it only waits until no update is open.
 *
 * <p>Failures are logged, and the receiver starts over after a delay: the stored sequence moves
 * only with a commit, so the same update is polled again. Reporting results to Hub is not done yet.
 */
@NullMarked
public final class HubUpdateReceiver extends Actor implements PartitionListener {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final String physicalTenantId;
  private final HubClient hubClient;
  private final PolicyInPort policyInPort;
  private final Supplier<Optional<PolicyStateReader>> stateReaderLookup;
  private final Duration waitInterval;

  // incremented on every leader change, so callbacks of an earlier leadership are dropped
  private long generation;
  private boolean leading;
  private @Nullable ScheduledTimer timer;

  public HubUpdateReceiver(
      final String physicalTenantId,
      final HubClient hubClient,
      final PolicyInPort policyInPort,
      final Supplier<Optional<PolicyStateReader>> stateReaderLookup,
      final Duration waitInterval) {
    super("HubUpdateReceiver", null, Map.of(ACTOR_PROP_PHYSICAL_TENANT, physicalTenantId));
    this.physicalTenantId = physicalTenantId;
    this.hubClient = hubClient;
    this.policyInPort = policyInPort;
    this.stateReaderLookup = stateReaderLookup;
    this.waitInterval = waitInterval;
  }

  @Override
  public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
    if (partitionId == Protocol.DEPLOYMENT_PARTITION) {
      actor.run(this::stop);
    }
    return CompletableActorFuture.completed();
  }

  @Override
  public ActorFuture<Void> onBecomingLeader(
      final int partitionId,
      final long term,
      final LogStream logStream,
      final QueryService queryService) {
    // a failed future would fail the leader transition, so the receiver starts on its own actor
    if (partitionId == Protocol.DEPLOYMENT_PARTITION) {
      actor.run(this::start);
    }
    return CompletableActorFuture.completed();
  }

  @Override
  public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
    if (partitionId == Protocol.DEPLOYMENT_PARTITION) {
      actor.run(this::stop);
    }
    return CompletableActorFuture.completed();
  }

  @Override
  protected void onActorClosing() {
    stop();
    hubClient.close();
  }

  private void start() {
    stop();
    leading = true;
    LOG.debug("Start receiving Hub policy updates for physical tenant {}", physicalTenantId);
    awaitNoOpenUpdate(generation, this::poll);
  }

  private void stop() {
    if (leading) {
      LOG.debug("Stop receiving Hub policy updates for physical tenant {}", physicalTenantId);
    }
    leading = false;
    generation++;
    cancelTimer();
  }

  /** Reads the policy state until no update is open, then continues with it. */
  private void awaitNoOpenUpdate(final long run, final Consumer<PolicyStatus> next) {
    readPolicyState(
        run,
        status -> {
          if (status.hasOpenUpdate()) {
            LOG.debug(
                "Wait for policy update '{}' ({}) of physical tenant {} to be committed",
                status.openUpdateId(),
                status.updateStatus(),
                physicalTenantId);
            runLater(run, () -> awaitNoOpenUpdate(run, next));
          } else {
            next.accept(status);
          }
        });
  }

  private void poll(final PolicyStatus status) {
    final long run = generation;
    final long afterSeqNo = status.committedSeqNo();
    onActor(
        run,
        hubClient.poll(afterSeqNo),
        updates -> forward(run, afterSeqNo, new ArrayDeque<>(updates)),
        error -> retry(run, "poll Hub after sequence " + afterSeqNo, error));
  }

  /** Hands the updates to the engine one after another, each once the previous is committed. */
  private void forward(
      final long run, final long afterSeqNo, final Queue<PolicyUpdateRecord> updates) {
    final var update = updates.poll();
    if (update == null) {
      awaitNoOpenUpdate(run, this::poll);
      return;
    }
    if (update.getSeqNo() <= afterSeqNo) {
      // Hub returned an update that is already committed; it must not be applied again
      forward(run, afterSeqNo, updates);
      return;
    }
    onActor(
        run,
        policyInPort.apply(update),
        ignored ->
            awaitCommitted(run, update, status -> forward(run, status.committedSeqNo(), updates)),
        error -> retry(run, "send policy update with sequence " + update.getSeqNo(), error));
  }

  /** Reads the policy state until the update is committed and no other update is open. */
  private void awaitCommitted(
      final long run, final PolicyUpdateRecord update, final Consumer<PolicyStatus> next) {
    awaitNoOpenUpdate(
        run,
        status -> {
          if (status.committedSeqNo() >= update.getSeqNo()) {
            next.accept(status);
          } else {
            // the response arrived before the state shows the update; read again
            runLater(run, () -> awaitCommitted(run, update, next));
          }
        });
  }

  private void readPolicyState(final long run, final Consumer<PolicyStatus> next) {
    final var reader = stateReaderLookup.get();
    if (reader.isEmpty()) {
      retry(run, "find partition 1", null);
      return;
    }
    actor.runOnCompletion(
        reader.get().getPolicyState(),
        (status, error) -> {
          if (run != generation) {
            return;
          }
          if (error != null) {
            retry(run, "read the policy state", error);
          } else if (status.isEmpty()) {
            retry(run, "read the policy state, which is not open yet,", null);
          } else {
            next.accept(status.get());
          }
        });
  }

  /** Continues on this actor once the future completes, unless the leadership changed meanwhile. */
  private <T> void onActor(
      final long run,
      final CompletableFuture<T> future,
      final Consumer<T> onSuccess,
      final Consumer<Throwable> onFailure) {
    future.whenCompleteAsync(
        (result, error) -> {
          if (run != generation) {
            return;
          }
          if (error != null) {
            onFailure.accept(error);
          } else {
            onSuccess.accept(result);
          }
        },
        actor);
  }

  private void retry(final long run, final String action, final @Nullable Throwable error) {
    LOG.warn(
        "Failed to {} for physical tenant {}; retrying in {}",
        action,
        physicalTenantId,
        waitInterval,
        error);
    runLater(run, () -> awaitNoOpenUpdate(run, this::poll));
  }

  private void runLater(final long run, final Runnable action) {
    cancelTimer();
    timer =
        actor.schedule(
            waitInterval,
            () -> {
              timer = null;
              if (run == generation) {
                action.run();
              }
            });
  }

  private void cancelTimer() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
  }
}
