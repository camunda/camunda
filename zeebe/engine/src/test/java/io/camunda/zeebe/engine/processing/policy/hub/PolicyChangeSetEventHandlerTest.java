/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.changeSet;
import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.delta;
import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.noDeletions;
import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.noUpserts;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.fanout.protocol.FanoutProtocol;
import io.camunda.fanout.protocol.model.ApplyReportV1;
import io.camunda.fanout.protocol.model.ApplyResult;
import io.camunda.fanout.protocol.model.CloudEvent;
import io.camunda.fanout.protocol.model.LifecycleState;
import io.camunda.fanout.protocol.model.PolicyChangeSetKind;
import io.camunda.zeebe.engine.processing.policy.hub.PolicyUpdateOutcome.Applied;
import io.camunda.zeebe.engine.processing.policy.hub.PolicyUpdateOutcome.Rejected;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class PolicyChangeSetEventHandlerTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final PolicyUpdateInbox inbox = new PolicyUpdateInbox();
  private final StubFanoutClient client =
      new StubFanoutClient(
          new PolicyChangeSetEventHandler(
              new PolicyChangeSetSplitter(PolicyChangeSetSplitter.DEFAULT_MAX_CHUNK_SIZE),
              inbox,
              "8.10.0"));

  @Test
  void shouldHandOverTheChunksAndWaitForTheOutcome() {
    // given
    final var changeSet = delta(noUpserts(), noDeletions());

    // when
    final var report = client.send(changeSet, 12L);

    // then
    final var pending = inbox.peek();
    assertThat(pending).isNotNull();
    assertThat(pending.seqNo()).isEqualTo(12L);
    assertThat(pending.updateId()).isEqualTo(PolicyChangeSetMapper.updateId(changeSet, 12L));
    assertThat(pending.chunks()).hasSize(1);
    assertThat(report).isNotDone();
  }

  @Test
  void shouldReportAnAppliedUpdate() {
    // given
    final var changeSet = delta(noUpserts(), noDeletions());
    final var report = client.send(changeSet, 12L);

    // when
    inbox.complete(inbox.peek(), new Applied());

    // then
    assertThat(report)
        .succeedsWithin(TIMEOUT)
        .isEqualTo(
            Optional.of(
                new ApplyReportV1(
                    12L,
                    changeSet.basePolicyVersion(),
                    changeSet.policyVersion(),
                    ApplyResult.APPLIED,
                    null,
                    LifecycleState.MANAGED,
                    "8.10.0")));
  }

  @Test
  void shouldReportARejectedUpdateWithTheReasonOfTheEngine() {
    // given
    final var report = client.send(delta(noUpserts(), noDeletions()), 12L);

    // when
    inbox.complete(inbox.peek(), new Rejected("the base version is outdated"));

    // then
    assertThat(report)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result -> {
              assertThat(result.orElseThrow().result()).isEqualTo(ApplyResult.REJECTED);
              assertThat(result.orElseThrow().reason()).isEqualTo("the base version is outdated");
            });
  }

  @Test
  void shouldRejectAChangeSetThatCannotBeSplitWithoutHandingItOver() {
    // given
    final var changeSet = changeSet(PolicyChangeSetKind.SNAPSHOT, noUpserts(), noDeletions());

    // when
    final var report = client.send(changeSet, 3L);

    // then
    assertThat(report)
        .succeedsWithin(TIMEOUT)
        .satisfies(
            result -> {
              assertThat(result.orElseThrow().seq()).isEqualTo(3L);
              assertThat(result.orElseThrow().result()).isEqualTo(ApplyResult.REJECTED);
              assertThat(result.orElseThrow().reason()).contains("SNAPSHOT");
            });
    assertThat(inbox.peek()).isNull();
  }

  @Test
  void shouldFailWhenTheUpdateIsGivenUp() {
    // given
    final var report = client.send(delta(noUpserts(), noDeletions()), 12L);

    // when
    inbox.giveUpPending(new IllegalStateException("leader stepped down"));

    // then the client sends the event again later
    assertThat(report).failsWithin(TIMEOUT);
  }

  @Test
  void shouldFailASecondEventWhileOneIsPending() {
    // given
    client.send(delta(noUpserts(), noDeletions()), 1L);

    // when
    final var second = client.send(delta(noUpserts(), noDeletions()), 2L);

    // then
    assertThat(second).failsWithin(TIMEOUT);
  }

  @Test
  void shouldFailAnEventThatIsNoPolicyChangeSet() {
    // given
    final var handler =
        new PolicyChangeSetEventHandler(
            new PolicyChangeSetSplitter(PolicyChangeSetSplitter.DEFAULT_MAX_CHUNK_SIZE),
            inbox,
            "8.10.0");

    // when
    final var report =
        handler
            .handle(
                new CloudEvent(
                    FanoutProtocol.SPEC_VERSION,
                    "event-1",
                    "hub",
                    FanoutProtocol.POLICY_CHANGE_SET_V1,
                    "1",
                    null,
                    null,
                    "not a change set"))
            .toCompletableFuture();

    // then
    assertThat(report).isInstanceOf(CompletableFuture.class).failsWithin(TIMEOUT);
    assertThat(inbox.peek()).isNull();
  }
}
