/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.hub;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.engine.processing.policy.PolicyStatus;
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
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Connects Hub to the engine of one physical tenant (ADR 0015). It runs while its broker leads
 * partition 1 of the tenant: it polls Hub from the committed sequence, hands each policy update to
 * the {@link PolicyInPort}, and polls again only once the engine has committed the update.
 *
 * <p>The receiver learns the policy state from the gossip: the engine of partition 1 publishes it
 * as a property of its member (see {@link GossipPolicyStateNotifier}). The receiver runs on the
 * same broker as the leader of partition 1, so it reads the property of the local member and reacts
 * when it changes. The gossiped state can be a little behind the log, so the receiver never decides
 * anything that has to be exact from it; it only waits until no update is open.
 *
 * <p>Failures are logged, and the receiver starts over after a delay: the committed sequence moves
 * only with a commit, so the same update is polled again. Reporting results to Hub is not done yet.
 */
@NullMarked
public final class HubUpdateReceiver extends Actor implements PartitionListener {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final String physicalTenantId;
  private final HubClient hubClient;
  private final PolicyInPort policyInPort;
  private final ClusterMembershipService membershipService;
  private final Duration retryInterval;
  private final ClusterMembershipEventListener membershipListener = this::onMembershipEvent;

  // incremented on every leader change, so callbacks of an earlier leadership are dropped
  private long generation;
  private boolean leading;
  private @Nullable PolicyStatus policyState;
  private @Nullable Waiter waiter;
  private @Nullable ScheduledTimer timer;

  public HubUpdateReceiver(
      final String physicalTenantId,
      final HubClient hubClient,
      final PolicyInPort policyInPort,
      final ClusterMembershipService membershipService,
      final Duration retryInterval) {
    super("HubUpdateReceiver", null, Map.of(ACTOR_PROP_PHYSICAL_TENANT, physicalTenantId));
    this.physicalTenantId = physicalTenantId;
    this.hubClient = hubClient;
    this.policyInPort = policyInPort;
    this.membershipService = membershipService;
    this.retryInterval = retryInterval;
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
  protected void onActorStarted() {
    membershipService.addListener(membershipListener);
    policyState =
        PolicyStateGossip.read(membershipService.getLocalMember(), physicalTenantId).orElse(null);
  }

  @Override
  protected void onActorClosing() {
    membershipService.removeListener(membershipListener);
    stop();
    hubClient.close();
  }

  private void onMembershipEvent(final ClusterMembershipEvent event) {
    final var member = event.subject();
    if ((event.type() == Type.METADATA_CHANGED || event.type() == Type.MEMBER_ADDED)
        && member.id().equals(membershipService.getLocalMember().id())) {
      final var gossiped = PolicyStateGossip.read(member, physicalTenantId).orElse(null);
      actor.run(() -> onPolicyState(gossiped));
    }
  }

  private void onPolicyState(final @Nullable PolicyStatus gossiped) {
    policyState = gossiped;
    continueWaiter();
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
    waiter = null;
    cancelTimer();
  }

  /** Continues with the gossiped state once no update is open. */
  private void awaitNoOpenUpdate(final long run, final Consumer<PolicyStatus> next) {
    await(run, status -> !status.hasOpenUpdate(), next);
  }

  /** Continues with the gossiped state once the update is committed and no other update is open. */
  private void awaitCommitted(
      final long run, final PolicyUpdateRecord update, final Consumer<PolicyStatus> next) {
    await(
        run,
        status -> !status.hasOpenUpdate() && status.committedSeqNo() >= update.getSeqNo(),
        next);
  }

  private void await(
      final long run, final Predicate<PolicyStatus> condition, final Consumer<PolicyStatus> next) {
    waiter = new Waiter(run, condition, next);
    continueWaiter();
  }

  private void continueWaiter() {
    final var current = waiter;
    final var state = policyState;
    if (current == null || current.run() != generation) {
      return;
    }
    if (state == null || !current.condition().test(state)) {
      LOG.trace(
          "Wait for the policy state of physical tenant {} to change, it is {}",
          physicalTenantId,
          state);
      return;
    }
    waiter = null;
    current.next().accept(state);
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

  /** Continues on this actor once the future completes, unless the leadership changed meanwhile. */
  private <T> void onActor(
      final long run,
      final CompletableFuture<T> future,
      final Consumer<T> onSuccess,
      final Consumer<Throwable> onFailure) {
    future.whenComplete(
        (result, error) ->
            actor.run(
                () -> {
                  if (run != generation) {
                    return;
                  }
                  if (error != null) {
                    onFailure.accept(error);
                  } else {
                    onSuccess.accept(result);
                  }
                }));
  }

  private void retry(final long run, final String action, final Throwable error) {
    LOG.warn(
        "Failed to {} for physical tenant {}; retrying in {}",
        action,
        physicalTenantId,
        retryInterval,
        error);
    cancelTimer();
    timer =
        actor.schedule(
            retryInterval,
            () -> {
              timer = null;
              if (run == generation) {
                awaitNoOpenUpdate(run, this::poll);
              }
            });
  }

  private void cancelTimer() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
  }

  private record Waiter(long run, Predicate<PolicyStatus> condition, Consumer<PolicyStatus> next) {}
}
