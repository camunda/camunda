/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class RandomizedRaftTest {

  private static final int OPERATION_SIZE = 10000;
  private static final Logger LOG = LoggerFactory.getLogger(RandomizedRaftTest.class);
  private ControllableRaftContexts raftContexts;
  private List<RaftOperation> defaultOperations;
  private List<RaftOperation> operationsWithSnapshot;
  private List<RaftOperation> operationsWithRestarts;
  private List<RaftOperation> operationsWithSnapshotsAndRestarts;
  private List<RaftOperation> operationsWithSnapshotsAndRestartsWithDataLoss;

  private List<MemberId> raftMembers;
  private Path raftDataDirectory;

  @BeforeEach
  public void initOperations() {
    // Need members ids to generate pair operations
    final var servers =
        IntStream.range(0, 3)
            .mapToObj(String::valueOf)
            .map(MemberId::from)
            .collect(Collectors.toList());
    defaultOperations = RaftOperation.getDefaultRaftOperations();
    operationsWithSnapshot = RaftOperation.getRaftOperationsWithSnapshot();
    operationsWithRestarts = RaftOperation.getRaftOperationsWithRestarts();
    operationsWithSnapshotsAndRestarts = RaftOperation.getRaftOperationsWithSnapshotsAndRestarts();
    operationsWithSnapshotsAndRestartsWithDataLoss =
        RaftOperation.getRaftOperationsWithSnapshotsAndRestartsWithDataLoss();
    raftMembers = servers;
  }

  @HegelTest(
      testCases = 10,
      phases = {Phase.EXPLICIT, Phase.REUSE, Phase.GENERATE},
      derandomize = OptBoolean.FALSE,
      suppressHealthCheck = HealthCheck.TOO_SLOW)
  void consistencyTestWithNoSnapshot(final TestCase tc) throws Exception {
    runRandomized(tc, defaultOperations, this::consistencyTest);
  }

  @HegelTest(
      testCases = 10,
      phases = {Phase.EXPLICIT, Phase.REUSE, Phase.GENERATE},
      derandomize = OptBoolean.FALSE,
      suppressHealthCheck = HealthCheck.TOO_SLOW)
  void consistencyTestWithSnapshot(final TestCase tc) throws Exception {
    runRandomized(tc, operationsWithSnapshot, this::consistencyTest);
  }

  @HegelTest(
      testCases = 10,
      phases = {Phase.EXPLICIT, Phase.REUSE, Phase.GENERATE},
      derandomize = OptBoolean.FALSE,
      suppressHealthCheck = HealthCheck.TOO_SLOW)
  void consistencyTestWithRestarts(final TestCase tc) throws Exception {
    runRandomized(tc, operationsWithRestarts, this::consistencyTest);
  }

  @HegelTest(
      testCases = 10,
      phases = {Phase.EXPLICIT, Phase.REUSE, Phase.GENERATE},
      derandomize = OptBoolean.FALSE,
      suppressHealthCheck = HealthCheck.TOO_SLOW)
  void consistencyTestWithSnapshotsAndRestarts(final TestCase tc) throws Exception {
    runRandomized(tc, operationsWithSnapshotsAndRestarts, this::consistencyTest);
  }

  @HegelTest(
      testCases = 1,
      phases = {Phase.EXPLICIT, Phase.REUSE, Phase.GENERATE},
      derandomize = OptBoolean.FALSE,
      suppressHealthCheck = HealthCheck.TOO_SLOW)
  void consistencyTestAfterDataLoss(final TestCase tc) throws Exception {
    runRandomized(tc, operationsWithSnapshotsAndRestartsWithDataLoss, this::consistencyTest);
  }

  @HegelTest(
      testCases = 10,
      phases = {Phase.EXPLICIT, Phase.REUSE, Phase.GENERATE},
      derandomize = OptBoolean.FALSE,
      suppressHealthCheck = HealthCheck.TOO_SLOW)
  void livenessTestWithRestarts(final TestCase tc) throws Exception {
    runRandomized(tc, operationsWithRestarts, this::livenessTest);
  }

  @HegelTest(
      testCases = 10,
      phases = {Phase.EXPLICIT, Phase.REUSE, Phase.GENERATE},
      derandomize = OptBoolean.FALSE,
      suppressHealthCheck = HealthCheck.TOO_SLOW)
  void livenessTestWithRestartsAndSnapshots(final TestCase tc) throws Exception {
    runRandomized(tc, operationsWithSnapshotsAndRestarts, this::livenessTest);
  }

  @HegelTest(
      testCases = 10,
      phases = {Phase.EXPLICIT, Phase.REUSE, Phase.GENERATE},
      derandomize = OptBoolean.FALSE,
      suppressHealthCheck = HealthCheck.TOO_SLOW)
  void livenessTestWithNoSnapshot(final TestCase tc) throws Exception {
    runRandomized(tc, defaultOperations, this::livenessTest);
  }

  @HegelTest(
      testCases = 10,
      phases = {Phase.EXPLICIT, Phase.REUSE, Phase.GENERATE},
      derandomize = OptBoolean.FALSE,
      suppressHealthCheck = HealthCheck.TOO_SLOW)
  void livenessTestWithSnapshot(final TestCase tc) throws Exception {
    runRandomized(tc, operationsWithSnapshot, this::livenessTest);
  }

  @HegelTest(
      testCases = 10,
      phases = {Phase.EXPLICIT, Phase.REUSE, Phase.GENERATE},
      derandomize = OptBoolean.FALSE,
      suppressHealthCheck = HealthCheck.TOO_SLOW)
  void livenessTestWithSnapshotAndSingleRestart(final TestCase tc) throws Exception {
    runRandomized(
        tc,
        operationsWithSnapshot,
        (raftOperations, raftMembers) -> {
          // After all operations, restart all members once
          final var modifiedOperations = new ArrayList<>(raftOperations);
          for (final var member : this.raftMembers) {
            modifiedOperations.add(
                RaftOperation.of("Restart member", ControllableRaftContexts::restart));
          }

          final var modifiedMemberList = new ArrayList<>(raftMembers);
          modifiedMemberList.addAll(this.raftMembers);

          livenessTest(modifiedOperations, modifiedMemberList);
        });
  }

  /**
   * Runs one test case: the seed drawn from Hegel determines the operation sequence, the members
   * each operation is applied to, and the raft nodes' own randomness, so a reported seed replays
   * the whole case.
   */
  private void runRandomized(
      final TestCase tc, final List<RaftOperation> operations, final RaftTest test)
      throws Exception {
    final long seed = tc.draw(longs(), "seed");
    LOG.info("Running test case with seed {}", seed);
    final var random = new Random(seed);
    final var raftOperations = RandomSequence.of(random, operations, OPERATION_SIZE);
    final var members = RandomSequence.of(random, raftMembers, OPERATION_SIZE);
    setUpRaftNodes(random);
    try {
      test.run(raftOperations, members);
    } finally {
      shutDownRaftNodes();
    }
  }

  private void consistencyTest(
      final List<RaftOperation> raftOperations, final List<MemberId> raftMembers) throws Exception {
    int step = 0;
    final var memberIter = raftMembers.iterator();
    for (final RaftOperation operation : raftOperations) {
      step++;

      final MemberId member = memberIter.next();
      try (final var ignored = MDC.putCloseable("actor-scheduler", member.toString())) {
        LOG.info("{} on {}", operation, member);
      }
      operation.run(raftContexts, member);
      raftContexts.assertAtMostOneLeader();
      raftContexts.assertAtMostOneVotePerMemberAndTerm();

      if (step % 100 == 0) { // reading logs after every operation can be too slow
        raftContexts.assertAllLogsEqual();
        step = 0;
      }
    }

    raftContexts.assertAllLogsEqual();
    raftContexts.assertNoGapsInLog();
    raftContexts.assertNoJournalAppendErrors();
    raftContexts.assertNoDataLoss();
  }

  private void livenessTest(
      final List<RaftOperation> raftOperations, final List<MemberId> raftMembers) throws Exception {
    // given - when there are failures such as message loss
    final var memberIter = raftMembers.iterator();
    for (final RaftOperation operation : raftOperations) {
      final MemberId member = memberIter.next();
      LOG.info("{} on {}", operation, member);
      operation.run(raftContexts, member);
    }

    raftContexts.assertAtMostOneLeader();
    raftContexts.assertAtMostOneVotePerMemberAndTerm();
    raftContexts.assertAllLogsEqual();

    // when - no more message loss

    // hoping that 2000 iterations are enough to replicate all entries
    int maxStepsToReplicateEntries = 2000;
    while (!(raftContexts.hasLeaderAtTheLatestTerm()
            && raftContexts.hasReplicatedAllEntries()
            && raftContexts.hasCommittedAllEntries()
            && raftContexts.allMembersAreReady())
        && maxStepsToReplicateEntries-- > 0) {
      raftContexts.runUntilDone();
      raftContexts.processAllMessage();
      raftContexts.tickHeartbeatTimeout();
    }

    // then - eventually all entries are replicated to all followers and all entries are committed

    // eventually a leader should be elected
    assertThat(raftContexts.hasLeaderAtTheLatestTerm())
        .describedAs("Leader election should be completed if there are no messages lost.")
        .isTrue();

    // All member are be ready
    raftContexts.assertAllMembersAreReady();

    // Verify all entries are replicated and committed in all replicas
    raftContexts.assertAllLogsEqual();
    raftContexts.assertAllEntriesCommittedAndReplicatedToAll();
    raftContexts.assertNoGapsInLog();
    raftContexts.assertNoJournalAppendErrors();
    raftContexts.assertNoDataLoss();
  }

  private void setUpRaftNodes(final Random random) throws Exception {
    // Could not make @TempDir annotation work
    raftDataDirectory = Files.createTempDirectory(null);
    raftContexts = new ControllableRaftContexts(3);
    raftContexts.setup(raftDataDirectory, random);
  }

  private void shutDownRaftNodes() throws IOException {
    raftContexts.shutdown();
    FileUtil.deleteFolder(raftDataDirectory);
    raftDataDirectory = null;
  }

  @FunctionalInterface
  private interface RaftTest {
    void run(List<RaftOperation> raftOperations, List<MemberId> raftMembers) throws Exception;
  }
}
