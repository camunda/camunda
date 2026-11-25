/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import io.camunda.fanout.client.port.EventHandler;
import io.camunda.fanout.protocol.FanoutProtocol;
import io.camunda.fanout.protocol.model.ApplyReportV1;
import io.camunda.fanout.protocol.model.ApplyResult;
import io.camunda.fanout.protocol.model.CloudEvent;
import io.camunda.fanout.protocol.model.LifecycleState;
import io.camunda.fanout.protocol.model.PolicyChangeSetV1;
import io.camunda.zeebe.engine.processing.policy.hub.PolicyUpdateOutcome.Applied;
import io.camunda.zeebe.engine.processing.policy.hub.PolicyUpdateOutcome.Rejected;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Receives the policy change sets from the fan-out client, and hands each one over to the scheduler
 * as the chunks of a policy update.
 *
 * <p>The returned stage completes once the outcome of the update is committed in the log of
 * partition 1: with an {@code APPLIED} report once its policy version is committed, or a {@code
 * REJECTED} report once the engine failed it. A change set that cannot be split is rejected at
 * once, without writing anything. The stage completes exceptionally if the scheduler gives the
 * update up, for example when its leader steps down; the fan-out client then sends the event again.
 *
 * <p>The fan-out client calls the handler on its own thread. The handler never reads engine state;
 * the stage completes on the common pool, so the client never runs on the engine's actors.
 */
@NullMarked
final class PolicyChangeSetEventHandler implements EventHandler {

  private final PolicyChangeSetSplitter splitter;
  private final PolicyUpdateInbox inbox;
  private final String targetVersion;

  PolicyChangeSetEventHandler(
      final PolicyChangeSetSplitter splitter,
      final PolicyUpdateInbox inbox,
      final String targetVersion) {
    this.splitter = splitter;
    this.inbox = inbox;
    this.targetVersion = targetVersion;
  }

  @Override
  public String eventType() {
    return FanoutProtocol.POLICY_CHANGE_SET_V1;
  }

  @Override
  public CompletionStage<Optional<ApplyReportV1>> handle(final CloudEvent event) {
    if (!(event.data() instanceof final PolicyChangeSetV1 changeSet)) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException(
              "Expected the data of event '%s' to be a policy change set, but it is %s"
                  .formatted(event.id(), event.data().getClass().getName())));
    }
    final long seqNo;
    try {
      seqNo = Long.parseLong(event.sequence());
    } catch (final NumberFormatException e) {
      return CompletableFuture.failedFuture(e);
    }

    final var chunks = splitter.split(changeSet, seqNo);
    if (chunks.isLeft()) {
      return CompletableFuture.completedFuture(
          Optional.of(report(seqNo, changeSet, new Rejected(chunks.getLeft()))));
    }
    return inbox
        .offer(seqNo, chunks.get())
        .thenApplyAsync(outcome -> Optional.of(report(seqNo, changeSet, outcome)));
  }

  private ApplyReportV1 report(
      final long seqNo, final PolicyChangeSetV1 changeSet, final PolicyUpdateOutcome outcome) {
    final @Nullable String reason =
        switch (outcome) {
          case Applied() -> null;
          case Rejected(final String rejection) -> rejection;
        };
    return new ApplyReportV1(
        seqNo,
        changeSet.basePolicyVersion(),
        changeSet.policyVersion(),
        reason == null ? ApplyResult.APPLIED : ApplyResult.REJECTED,
        reason,
        // the management lifecycle is not tracked yet; a cluster that receives change sets is
        // managed
        LifecycleState.MANAGED,
        targetVersion);
  }
}
