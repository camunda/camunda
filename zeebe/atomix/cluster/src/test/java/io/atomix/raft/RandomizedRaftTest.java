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

import static org.assertj.core.api.Assertions.assertThat;

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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.EdgeCasesMode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.PropertyDefaults;
import net.jqwik.api.Provide;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@PropertyDefaults(tries = 10, shrinking = ShrinkingMode.OFF, edgeCases = EdgeCasesMode.NONE)
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

  @BeforeProperty
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

  @AfterTry
  public void shutDownRaftNodes() throws IOException {
    raftContexts.shutdown();
    FileUtil.deleteFolder(raftDataDirectory);
    raftDataDirectory = null;
  }

  @Property
  void consistencyTestWithNoSnapshot(
      @ForAll("raftOperations") final List<RaftOperation> raftOperations,
      @ForAll("raftMembers") final List<MemberId> raftMembers,
      @ForAll("seeds") final long seed)
      throws Exception {

    consistencyTest(raftOperations, raftMembers, seed);
  }

  @Property
  void consistencyTestWithSnapshot(
      @ForAll("raftOperationsWithSnapshot") final List<RaftOperation> raftOperations,
      @ForAll("raftMembers") final List<MemberId> raftMembers,
      @ForAll("seeds") final long seed)
      throws Exception {

    consistencyTest(raftOperations, raftMembers, seed);
  }

  @Property
  void consistencyTestWithRestarts(
      @ForAll("raftOperationsWithRestarts") final List<RaftOperation> raftOperations,
      @ForAll("raftMembers") final List<MemberId> raftMembers,
      @ForAll("seeds") final long seed)
      throws Exception {

    consistencyTest(raftOperations, raftMembers, seed);
  }

  @Property
  void consistencyTestWithSnapshotsAndRestarts(
      @ForAll("raftOperationsWithSnapshotsAndRestarts") final List<RaftOperation> raftOperations,
      @ForAll("raftMembers") final List<MemberId> raftMembers,
      @ForAll("seeds") final long seed)
      throws Exception {

    consistencyTest(raftOperations, raftMembers, seed);
  }

  @Property(tries = 1)
  void consistencyTestAfterDataLoss(
      @ForAll("raftOperationsWithSnapshotsAndRestartsWithDataLoss")
          final List<RaftOperation> raftOperations,
      @ForAll("raftMembers") final List<MemberId> raftMembers,
      @ForAll("seeds") final long seed)
      throws Exception {

    consistencyTest(raftOperations, raftMembers, seed);
  }

  @Property
  void livenessTestWithRestarts(
      @ForAll("raftOperationsWithRestarts") final List<RaftOperation> raftOperations,
      @ForAll("raftMembers") final List<MemberId> raftMembers,
      @ForAll("seeds") final long seed)
      throws Exception {
    livenessTest(raftOperations, raftMembers, seed);
  }

  @Property
  void livenessTestWithRestartsAndSnapshots(
      @ForAll("raftOperationsWithSnapshotsAndRestarts") final List<RaftOperation> raftOperations,
      @ForAll("raftMembers") final List<MemberId> raftMembers,
      @ForAll("seeds") final long seed)
      throws Exception {
    livenessTest(raftOperations, raftMembers, seed);
  }

  @Property
  void livenessTestWithNoSnapshot(
      @ForAll("raftOperations") final List<RaftOperation> raftOperations,
      @ForAll("raftMembers") final List<MemberId> raftMembers,
      @ForAll("seeds") final long seed)
      throws Exception {

    livenessTest(raftOperations, raftMembers, seed);
  }

  @Property
  void livenessTestWithSnapshot(
      @ForAll("raftOperationsWithSnapshot") final List<RaftOperation> raftOperations,
      @ForAll("raftMembers") final List<MemberId> raftMembers,
      @ForAll("seeds") final long seed)
      throws Exception {

    livenessTest(raftOperations, raftMembers, seed);
  }

  @Property
  void livenessTestWithSnapshotAndSingleRestart(
      @ForAll("raftOperationsWithSnapshot") final List<RaftOperation> raftOperations,
      @ForAll("raftMembers") final List<MemberId> raftMembers,
      @ForAll("seeds") final long seed)
      throws Exception {

    // After all operations, restart all members once
    final var modifiedOperations = new ArrayList<>(raftOperations);
    for (final var member : this.raftMembers) {
      modifiedOperations.add(RaftOperation.of("Restart member", ControllableRaftContexts::restart));
    }

    final var modifiedMemberList = new ArrayList<>(raftMembers);
    modifiedMemberList.addAll(this.raftMembers);

    livenessTest(modifiedOperations, modifiedMemberList, seed);
  }

  private void consistencyTest(
      final List<RaftOperation> raftOperations, final List<MemberId> raftMembers, final long seed)
      throws Exception {
    setUpRaftNodes(new Random(seed));

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
      final List<RaftOperation> raftOperations, final List<MemberId> raftMembers, final long seed)
      throws Exception {
    setUpRaftNodes(new Random(seed));

    // given - when there are failures such as message loss
    final var memberIter = raftMembers.iterator();
    for (final RaftOperation operation : raftOperations) {
      final MemberId member = memberIter.next();
      LOG.info("{} on {}", operation, member);
      operation.run(raftContexts, member);
    }

    raftContexts.assertAtMostOneLeader();
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

  /** Basic raft operations without snapshotting, compaction or restart */
  @Provide
  Arbitrary<List<RaftOperation>> raftOperations() {
    final var operation = Arbitraries.of(defaultOperations);
    return operation.list().ofSize(OPERATION_SIZE);
  }

  /** Basic raft operation with snapshotting and compaction */
  @Provide
  Arbitrary<List<RaftOperation>> raftOperationsWithSnapshot() {
    final var operation = Arbitraries.of(operationsWithSnapshot);
    return operation.list().ofSize(OPERATION_SIZE);
  }

  @Provide
  Arbitrary<List<RaftOperation>> raftOperationsWithRestarts() {
    return Arbitraries.of(operationsWithRestarts).list().ofSize(OPERATION_SIZE);
  }

  @Provide
  Arbitrary<List<RaftOperation>> raftOperationsWithSnapshotsAndRestarts() {
    return Arbitraries.of(operationsWithSnapshotsAndRestarts).list().ofSize(OPERATION_SIZE);
  }

  @Provide
  Arbitrary<List<RaftOperation>> raftOperationsWithSnapshotsAndRestartsWithDataLoss() {
    return Arbitraries.of(operationsWithSnapshotsAndRestartsWithDataLoss)
        .list()
        .ofSize(OPERATION_SIZE);
  }

  @Provide
  Arbitrary<List<MemberId>> raftMembers() {
    final var members = Arbitraries.of(raftMembers);
    return members.list().ofSize(OPERATION_SIZE);
  }

  @Provide
  Arbitrary<Long> seeds() {
    return Arbitraries.longs();
  }

  private void setUpRaftNodes(final Random random) throws Exception {
    // Could not make @TempDir annotation work
    raftDataDirectory = Files.createTempDirectory(null);
    raftContexts = new ControllableRaftContexts(3);
    raftContexts.setup(raftDataDirectory, random);
  }
}
