/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft;

import static dev.hegel.Generators.longs;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hegel.HealthCheck;
import dev.hegel.HegelTest;
import dev.hegel.OptBoolean;
import dev.hegel.Phase;
import dev.hegel.TestCase;
import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.impl.RaftContext;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomizedRaftJoinTest {

  private static final Logger LOG = LoggerFactory.getLogger(RandomizedRaftJoinTest.class);
  private static final int OPERATION_SIZE = 1000;

  private ControllableRaftContexts raftContexts;
  private Path raftDataDirectory;
  private MemberId member0;
  private MemberId member1;
  private List<RaftOperation> operationsWithRestarts;

  @BeforeEach
  public void initMembers() {
    // Initialize the two member IDs for the 2-node cluster
    member0 = MemberId.from("0");
    member1 = MemberId.from("1");
    operationsWithRestarts = RaftOperation.getRaftOperationsWithRestarts();
  }

  @HegelTest(
      testCases = 10,
      phases = {Phase.EXPLICIT, Phase.REUSE, Phase.GENERATE},
      derandomize = OptBoolean.FALSE,
      suppressHealthCheck = HealthCheck.TOO_SLOW)
  void joinCompletes(final TestCase tc) throws Exception {
    // The seed drawn from Hegel determines the operation sequence, the members each operation is
    // applied to, and the raft nodes' own randomness, so a reported seed replays the whole case.
    final long seed = tc.draw(longs(), "seed");
    LOG.info("Running test case with seed {}", seed);
    final var random = new Random(seed);
    final var raftOperations = RandomSequence.of(random, operationsWithRestarts, OPERATION_SIZE);
    final var raftMembers = RandomSequence.of(random, List.of(member0, member1), OPERATION_SIZE);
    setUpRaftNodes(random);
    try {
      var joinFuture = raftContexts.join(member1, Set.of(member0, member1));

      // given - when there are failures such as message loss
      final var memberIter = raftMembers.iterator();
      for (final RaftOperation operation : raftOperations) {
        final MemberId member = memberIter.next();
        LOG.info("{} on {}", operation, member);
        operation.run(raftContexts, member);
        // sample the safety invariant on every step: it records the vote of each member at the term
        // it is currently in, so a vote that is overwritten between two steps is only observable
        // while it is still recorded
        raftContexts.assertAtMostOneVotePerMemberAndTerm();
        if (joinFuture.isCompletedExceptionally()) {
          // retry join
          LOG.info("Join failed. Retrying...");
          joinFuture = raftContexts.join(member1, Set.of(member0, member1));
        }
      }

      raftContexts.runUntilDone();
      raftContexts.processAllMessage();
      raftContexts.tickHeartbeatTimeout();

      // when - no more message loss or restarts

      LOG.info("Stopping failures, waiting for join to complete");

      // hoping that 2000 iterations are enough to complete the join process
      int maxStepsToReplicateEntries = 10100;
      while (!((joinFuture.isDone() && !joinFuture.isCompletedExceptionally())
              && raftContexts.allMembersAreReady()
              && raftContexts.hasLeaderAtTheLatestTerm())
          && maxStepsToReplicateEntries-- > 0) {

        if (joinFuture.isCompletedExceptionally()) {
          // retry join
          LOG.info("Join failed. Retrying...");
          joinFuture = raftContexts.join(member1, Set.of(member0, member1));
        }

        raftContexts.runUntilDone();
        raftContexts.processAllMessage();
        raftContexts.tickHeartbeatTimeout();
      }

      // then
      assertThat(joinFuture).describedAs("Join of member 1 should be completed").isCompleted();
      assertThat(raftContexts.hasLeaderAtTheLatestTerm()).describedAs("There is a leader").isTrue();
      raftContexts.assertAllMembersAreReady();
      raftContexts.assertAtMostOneVotePerMemberAndTerm();
    } finally {
      shutDownRaftNodes();
    }
  }

  @HegelTest(
      testCases = 10,
      phases = {Phase.EXPLICIT, Phase.REUSE, Phase.GENERATE},
      derandomize = OptBoolean.FALSE,
      suppressHealthCheck = HealthCheck.TOO_SLOW)
  void joinThenPromoteCompletes(final TestCase tc) throws Exception {
    // The seed drawn from Hegel determines the operation sequence, the members each operation is
    // applied to, and the raft nodes' own randomness, so a reported seed replays the whole case.
    final long seed = tc.draw(longs(), "seed");
    LOG.info("Running test case with seed {}", seed);
    final var random = new Random(seed);
    final var raftOperations = RandomSequence.of(random, operationsWithRestarts, OPERATION_SIZE);
    final var raftMembers = RandomSequence.of(random, List.of(member0, member1), OPERATION_SIZE);
    setUpRaftNodes(random);
    try {
      var joinFuture =
          raftContexts.join(member1, RaftMember.Type.PROMOTABLE, Set.of(member0, member1));
      CompletableFuture<Void> promoteFuture = null;

      // given - when there are failures such as message loss, including restarts of the joined
      // member: the join future only completes once the admitting configuration entry is durably
      // in this node's own log (see ReconfigurationHelper#awaitLocalDurability), so a restart right
      // after completion always finds that entry via reloadConfigurationFromLog and never falls
      // back to treating this node as a fresh, unconfigured one.
      final var memberIter = raftMembers.iterator();
      for (final RaftOperation operation : raftOperations) {
        final MemberId member = memberIter.next();
        LOG.info("{} on {}", operation, member);
        operation.run(raftContexts, member);
        // sample the safety invariant on every step: it records the vote of each member at the
        // term it is currently in, so a vote that is overwritten between two steps is only
        // observable while it is still recorded
        raftContexts.assertAtMostOneVotePerMemberAndTerm();
        if (joinFuture.isCompletedExceptionally()) {
          // retry join
          LOG.info("Join failed. Retrying...");
          joinFuture =
              raftContexts.join(member1, RaftMember.Type.PROMOTABLE, Set.of(member0, member1));
        } else if (joinFuture.isDone() && shouldRetryPromote(promoteFuture)) {
          LOG.info("Promoting member 1...");
          promoteFuture = raftContexts.promote(member1);
        }
      }

      raftContexts.runUntilDone();
      raftContexts.processAllMessage();
      raftContexts.tickHeartbeatTimeout();

      // when - no more message loss or restarts

      LOG.info("Stopping failures, waiting for join and promotion to complete");

      int maxStepsToReplicateEntries = 10100;
      while (!(joinFuture.isDone()
              && !joinFuture.isCompletedExceptionally()
              && promoteFuture != null
              && promoteFuture.isDone()
              && !promoteFuture.isCompletedExceptionally()
              && member1IsActiveInLeadersConfiguration()
              && raftContexts.allMembersAreReady()
              && raftContexts.hasLeaderAtTheLatestTerm())
          && maxStepsToReplicateEntries-- > 0) {

        if (joinFuture.isCompletedExceptionally()) {
          // retry join
          LOG.info("Join failed. Retrying...");
          joinFuture =
              raftContexts.join(member1, RaftMember.Type.PROMOTABLE, Set.of(member0, member1));
        } else if (joinFuture.isDone() && shouldRetryPromote(promoteFuture)) {
          LOG.info("Promoting member 1...");
          promoteFuture = raftContexts.promote(member1);
        }

        raftContexts.runUntilDone();
        raftContexts.processAllMessage();
        raftContexts.tickHeartbeatTimeout();
      }

      // then
      assertThat(joinFuture).describedAs("Join of member 1 should be completed").isCompleted();
      assertThat(promoteFuture)
          .describedAs("Promotion of member 1 should be completed")
          .isCompleted();
      assertThat(raftContexts.hasLeaderAtTheLatestTerm()).describedAs("There is a leader").isTrue();
      assertThat(member1IsActiveInLeadersConfiguration())
          .describedAs("Member 1 is ACTIVE in the leader's configuration")
          .isTrue();
      raftContexts.assertAllMembersAreReady();
    } finally {
      shutDownRaftNodes();
    }
  }

  private void shutDownRaftNodes() throws IOException {
    if (raftContexts != null) {
      raftContexts.shutdown();
    }
    if (raftDataDirectory != null) {
      FileUtil.deleteFolder(raftDataDirectory);
      raftDataDirectory = null;
    }
  }

  /**
   * The promotion fails fast on CONFIGURATION_ERROR - not caught up yet, another change in
   * progress, or a stale configuration view - so it is simply re-issued until it completes. A
   * promotion can also complete trivially without effect: after a restart with an empty log, the
   * member falls back to the initial all-ACTIVE configuration and already considers itself ACTIVE
   * until the leader's next configure request corrects it. Such a completed promotion is retried as
   * long as the leader's configuration does not have member 1 as ACTIVE.
   */
  private boolean shouldRetryPromote(final CompletableFuture<Void> promoteFuture) {
    return promoteFuture == null
        || promoteFuture.isCompletedExceptionally()
        || (promoteFuture.isDone() && !member1IsActiveInLeadersConfiguration());
  }

  private boolean member1IsActiveInLeadersConfiguration() {
    return raftContexts.getRaftServers().values().stream()
        .filter(RaftContext::isLeader)
        .findAny()
        .map(leader -> leader.getCluster().getConfiguration())
        .map(
            configuration ->
                configuration.newMembers().stream()
                    .anyMatch(
                        member ->
                            member.memberId().equals(member1)
                                && member.getType() == RaftMember.Type.ACTIVE))
        .orElse(false);
  }

  private void setUpRaftNodes(final Random random) throws Exception {
    // Create temporary directory for raft data
    raftDataDirectory = Files.createTempDirectory(null);

    // Create ControllableRaftContexts with 2 nodes. Reduce the quorum response timeout to make
    // the wall-clock gated leader step-down reachable under the deterministic scheduler: a leader
    // then steps down after minStepDownFailureCount consecutive failures to reach the joining
    // member, as it does in production when the joiner is unreachable for two election timeouts.
    //
    // Per test case, not per property: shutdown() runs after every case and closes the harness'
    // meter registry, so a reused instance would register the next case's contexts into a closed
    // registry and carry its predecessor's data-loss bookkeeping over into an unrelated cluster.
    raftContexts =
        new ControllableRaftContexts(
            2, config -> config.setMaxQuorumResponseTimeout(Duration.ofMillis(1)));

    // Bootstrap only with member 0 (single node cluster initially)
    raftContexts.setup(raftDataDirectory, random, Set.of(0));

    LOG.info("Set up 2-node raft cluster, bootstrapped with member 0 only");
  }
}
