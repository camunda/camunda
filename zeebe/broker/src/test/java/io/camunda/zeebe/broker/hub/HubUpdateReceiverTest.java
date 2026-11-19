/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.hub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.system.partitions.PolicyStateReader;
import io.camunda.zeebe.broker.system.partitions.PolicyStateReader.PolicyStatus;
import io.camunda.zeebe.broker.system.partitions.PolicyStateReader.UpdateStatus;
import io.camunda.zeebe.engine.state.immutable.PolicyState.CommittedPolicy;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class HubUpdateReceiverTest {

  private static final Duration WAIT_INTERVAL = Duration.ofSeconds(5);

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  private final FakeHubClient hubClient = new FakeHubClient();
  private final PolicyInPort policyInPort = mock(PolicyInPort.class);
  private final PolicyStateReader stateReader = mock(PolicyStateReader.class);
  private final AtomicReference<Optional<PolicyStateReader>> stateReaderLookup =
      new AtomicReference<>(Optional.of(stateReader));
  private final AtomicReference<PolicyStatus> policyState = new AtomicReference<>(committed(0));

  private HubUpdateReceiver receiver;

  @BeforeEach
  void setUp() {
    when(stateReader.getPolicyState())
        .thenAnswer(invocation -> CompletableActorFuture.completed(Optional.of(policyState.get())));
    when(policyInPort.apply(any())).thenReturn(CompletableFuture.completedFuture(null));
    receiver =
        new HubUpdateReceiver(
            "pt-1", hubClient, policyInPort, stateReaderLookup::get, WAIT_INTERVAL);
    scheduler.submitActor(receiver);
    scheduler.workUntilDone();
  }

  @Test
  void shouldIgnoreOtherPartitions() {
    // when
    becomeLeader(2);

    // then
    assertThat(hubClient.polls).isEmpty();
  }

  @Test
  void shouldPollFromTheCommittedSequence() {
    // given
    policyState.set(committed(42));

    // when
    becomeLeader(1);

    // then
    assertThat(hubClient.polls).containsExactly(42L);
  }

  @Test
  void shouldBecomeLeaderWithoutPartitionState() {
    // given
    stateReaderLookup.set(Optional.empty());

    // when
    final var becameLeader = receiver.onBecomingLeader(1, 1, null, null);
    scheduler.workUntilDone();

    // then
    assertThat(becameLeader).isDone();
    assertThat(hubClient.polls).isEmpty();
  }

  @Test
  void shouldNotPollWhileAnUpdateIsOpen() {
    // given
    policyState.set(open(UpdateStatus.APPLYING));
    becomeLeader(1);
    assertThat(hubClient.polls).isEmpty();

    // when the update is committed
    policyState.set(committed(7));
    passWaitInterval();

    // then
    assertThat(hubClient.polls).containsExactly(7L);
  }

  @Test
  void shouldPollAgainOnlyOnceTheForwardedUpdateIsCommitted() {
    // given
    final var update = update(1);
    hubClient.nextResult(CompletableFuture.completedFuture(List.of(update)));
    becomeLeader(1);
    verify(policyInPort).apply(update);
    assertThat(hubClient.polls).containsExactly(0L);

    // when the update is not committed yet
    passWaitInterval();

    // then
    assertThat(hubClient.polls).containsExactly(0L);

    // when the update is committed
    policyState.set(committed(1));
    passWaitInterval();

    // then
    assertThat(hubClient.polls).containsExactly(0L, 1L);
  }

  @Test
  void shouldNotForwardAnUpdateThatIsAlreadyCommitted() {
    // given
    policyState.set(committed(3));
    hubClient.nextResult(CompletableFuture.completedFuture(List.of(update(3))));

    // when
    becomeLeader(1);

    // then
    verify(policyInPort, never()).apply(any());
    assertThat(hubClient.polls).containsExactly(3L, 3L);
  }

  @Test
  void shouldStopWhenBecomingFollower() {
    // given
    final var pendingPoll = new CompletableFuture<List<PolicyUpdateRecord>>();
    hubClient.nextResult(pendingPoll);
    becomeLeader(1);

    // when
    receiver.onBecomingFollower(1, 2);
    scheduler.workUntilDone();
    pendingPoll.complete(List.of(update(1)));
    scheduler.workUntilDone();

    // then
    verify(policyInPort, never()).apply(any());
  }

  @Test
  void shouldRetryAfterAFailedPoll() {
    // given
    hubClient.nextResult(CompletableFuture.failedFuture(new RuntimeException("Hub is down")));
    becomeLeader(1);
    assertThat(hubClient.polls).containsExactly(0L);

    // when
    passWaitInterval();

    // then
    assertThat(hubClient.polls).containsExactly(0L, 0L);
  }

  @Test
  void shouldPollTheSameUpdateAgainAfterItWasRejected() {
    // given
    final var update = update(1);
    hubClient.nextResult(CompletableFuture.completedFuture(List.of(update)));
    when(policyInPort.apply(update))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("rejected")));
    becomeLeader(1);

    // when
    passWaitInterval();

    // then the stored sequence did not move, so the receiver polls from it again
    assertThat(hubClient.polls).containsExactly(0L, 0L);
  }

  private void becomeLeader(final int partitionId) {
    receiver.onBecomingLeader(partitionId, 1, null, null);
    scheduler.workUntilDone();
  }

  private void passWaitInterval() {
    scheduler.updateClock(WAIT_INTERVAL);
    scheduler.workUntilDone();
  }

  private static PolicyStatus committed(final long seqNo) {
    final var committed =
        seqNo == 0
            ? Optional.<CommittedPolicy>empty()
            : Optional.of(new CommittedPolicy("policy-1", seqNo, seqNo));
    return new PolicyStatus(committed, UpdateStatus.NONE, null);
  }

  private static PolicyStatus open(final UpdateStatus status) {
    return new PolicyStatus(Optional.empty(), status, "update-1");
  }

  private static PolicyUpdateRecord update(final long seqNo) {
    return new PolicyUpdateRecord()
        .setPolicyId("policy-1")
        .setBasePolicyVersion(seqNo - 1)
        .setPolicyVersion(seqNo)
        .setSeqNo(seqNo)
        .setUpdateId("update-" + seqNo)
        .setChunkIndex(0)
        .setChunkCount(1);
  }

  /**
   * Records every poll. A poll returns the results given with {@link #nextResult} in order, and
   * then a result that never completes, like a long poll that Hub keeps open.
   */
  private static final class FakeHubClient implements HubClient {

    private final List<Long> polls = new ArrayList<>();
    private final List<CompletableFuture<List<PolicyUpdateRecord>>> results = new ArrayList<>();

    void nextResult(final CompletableFuture<List<PolicyUpdateRecord>> result) {
      results.add(result);
    }

    @Override
    public CompletableFuture<List<PolicyUpdateRecord>> poll(final long afterSeqNo) {
      polls.add(afterSeqNo);
      return results.isEmpty() ? new CompletableFuture<>() : results.removeFirst();
    }

    @Override
    public void close() {}
  }
}
