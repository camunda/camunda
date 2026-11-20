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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.camunda.zeebe.engine.processing.policy.PolicyStatus;
import io.camunda.zeebe.engine.processing.policy.PolicyStatus.UpdateStatus;
import io.camunda.zeebe.engine.state.immutable.PolicyState.CommittedPolicy;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class HubUpdateReceiverTest {

  private static final String TENANT = "pt-1";
  private static final Duration RETRY_INTERVAL = Duration.ofSeconds(5);

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  private final FakeHubClient hubClient = new FakeHubClient();
  private final PolicyInPort policyInPort = mock(PolicyInPort.class);
  private final ClusterMembershipService membershipService = mock(ClusterMembershipService.class);
  private final Member localMember = new Member(new MemberConfig().setId("broker-0"));
  private final List<ClusterMembershipEventListener> listeners = new ArrayList<>();
  private final GossipPolicyStateNotifier localEngine =
      new GossipPolicyStateNotifier(membershipService, TENANT);

  private HubUpdateReceiver receiver;

  @BeforeEach
  void setUp() {
    when(membershipService.getLocalMember()).thenReturn(localMember);
    doAnswer(invocation -> listeners.add(invocation.getArgument(0)))
        .when(membershipService)
        .addListener(any());
    when(policyInPort.apply(any())).thenReturn(CompletableFuture.completedFuture(null));
    localEngine.publish(committed(0));
    receiver =
        new HubUpdateReceiver(TENANT, hubClient, policyInPort, membershipService, RETRY_INTERVAL);
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
    gossip(committed(42));

    // when
    becomeLeader(1);

    // then
    assertThat(hubClient.polls).containsExactly(42L);
  }

  @Test
  void shouldWaitUntilThePolicyStateIsGossiped() {
    // given
    localEngine.clear();
    gossipLocalChange();
    becomeLeader(1);
    assertThat(hubClient.polls).isEmpty();

    // when partition 1 has recovered and publishes its state
    gossip(committed(5));

    // then
    assertThat(hubClient.polls).containsExactly(5L);
  }

  @Test
  void shouldNotPollWhileAnUpdateIsOpen() {
    // given
    gossip(open(UpdateStatus.APPLYING));
    becomeLeader(1);
    assertThat(hubClient.polls).isEmpty();

    // when the update is committed
    gossip(committed(7));

    // then
    assertThat(hubClient.polls).containsExactly(7L);
  }

  @Test
  void shouldIgnoreThePolicyStateOfOtherMembers() {
    // given
    gossip(open(UpdateStatus.APPLYING));
    becomeLeader(1);

    // when another member gossips a state without an open update
    final var otherMember = new Member(new MemberConfig().setId("broker-1"));
    new GossipPolicyStateNotifier(memberService(otherMember), TENANT).publish(committed(7));
    listeners.forEach(
        listener -> listener.event(new ClusterMembershipEvent(Type.METADATA_CHANGED, otherMember)));
    scheduler.workUntilDone();

    // then
    assertThat(hubClient.polls).isEmpty();
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
    gossip(open(UpdateStatus.APPLYING));

    // then
    assertThat(hubClient.polls).containsExactly(0L);

    // when the update is committed
    gossip(committed(1));

    // then
    assertThat(hubClient.polls).containsExactly(0L, 1L);
  }

  @Test
  void shouldNotForwardAnUpdateThatIsAlreadyCommitted() {
    // given
    gossip(committed(3));
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
    passRetryInterval();

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
    passRetryInterval();

    // then the committed sequence did not move, so the receiver polls from it again
    assertThat(hubClient.polls).containsExactly(0L, 0L);
  }

  private void becomeLeader(final int partitionId) {
    receiver.onBecomingLeader(partitionId, 1, null, null);
    scheduler.workUntilDone();
  }

  /** Publishes the state as the engine of the local partition 1 does, and gossips the change. */
  private void gossip(final PolicyStatus status) {
    localEngine.publish(status);
    gossipLocalChange();
  }

  /** Tells the listeners about the local member's properties, as a SWIM gossip round does. */
  private void gossipLocalChange() {
    final var snapshot = new Member(new MemberConfig().setId("broker-0"));
    snapshot.properties().putAll(localMember.properties());
    listeners.forEach(
        listener -> listener.event(new ClusterMembershipEvent(Type.METADATA_CHANGED, snapshot)));
    scheduler.workUntilDone();
  }

  private void passRetryInterval() {
    scheduler.updateClock(RETRY_INTERVAL);
    scheduler.workUntilDone();
  }

  private static ClusterMembershipService memberService(final Member member) {
    final var service = mock(ClusterMembershipService.class);
    when(service.getLocalMember()).thenReturn(member);
    return service;
  }

  private static PolicyStatus committed(final long seqNo) {
    final var committed = seqNo == 0 ? null : new CommittedPolicy("policy-1", seqNo, seqNo);
    return new PolicyStatus(committed, UpdateStatus.NONE, null);
  }

  private static PolicyStatus open(final UpdateStatus status) {
    return new PolicyStatus(null, status, "update-1");
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
