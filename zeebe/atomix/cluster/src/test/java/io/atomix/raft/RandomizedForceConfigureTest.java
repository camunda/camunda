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

import static io.atomix.raft.cluster.RaftMember.Type.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.impl.ReconfigurationHelper;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.EdgeCasesMode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RandomizedForceConfigureTest {

  private static final int OPERATION_SIZE = 2000;
  private static final int NODE_COUNT = 4;
  private static final Logger LOG = LoggerFactory.getLogger(RandomizedForceConfigureTest.class);
  private ControllableRaftContexts raftContexts;
  private List<RaftOperation> defaultOperations;

  private final ForceConfigureOperation forceConfigureOperation = new ForceConfigureOperation();

  private List<MemberId> raftMembers;
  private Path raftDataDirectory;

  @BeforeProperty
  public void initOperations() {
    // Need members ids to generate pair operations
    final var servers =
        IntStream.range(0, NODE_COUNT)
            .mapToObj(String::valueOf)
            .map(MemberId::from)
            .collect(Collectors.toList());
    defaultOperations = RaftOperation.getRaftOperationsWithSnapshot();
    defaultOperations.add(RaftOperation.of("Force configure (0,2)", forceConfigureOperation::run));
    raftMembers = servers;
  }

  @AfterTry
  public void shutDownRaftNodes() throws IOException {
    raftContexts.shutdown();
    FileUtil.deleteFolder(raftDataDirectory);
    raftDataDirectory = null;
    // reset the future, so it can be run again in the next try
    forceConfigureOperation.reset();
    LOG.info("=== Try completed ===");
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF, edgeCases = EdgeCasesMode.NONE)
  public void correctnessTest(
      @ForAll("raftOperations") final List<RaftOperation> raftOperations,
      @ForAll("raftMembers") final List<MemberId> raftMembers,
      @ForAll("seeds") final long seed)
      throws Exception {
    // given
    setUpRaftNodes(new Random(seed));

    // Wait until all members are ready. It doesn't make sense to force configure before all members
    // have bootstrapped.
    while (!(raftContexts.allMembersAreReady())) {
      raftContexts.runUntilDone();
      raftContexts.processAllMessage();
      raftContexts.tickHeartbeatTimeout();
    }

    // when - execute operations including force configure (0,2)
    final var memberIter = raftMembers.iterator();
    for (final RaftOperation operation : raftOperations) {
      final MemberId member = memberIter.next();
      LOG.info("{} on {}", operation, member);
      operation.run(raftContexts, member);
    }

    // Run force configure once again in case the previous ones timed out
    forceConfigureOperation.run(raftContexts, MemberId.from("0"));
    // run until force configure is completed
    for (int i = 0; i < 10; i++) {
      raftContexts.runUntilDone();
      raftContexts.processAllMessage();
      raftContexts.tickHeartbeatTimeout();
    }

    final var newMembers =
        Map.of(
            MemberId.from("0"),
            raftContexts.getRaftContext(0),
            MemberId.from("2"),
            raftContexts.getRaftContext(2));
    runUntilMembersAreInSync(newMembers);

    // then

    assertThatConfigurationContainsOnly0and2(0);
    assertThatConfigurationContainsOnly0and2(2);

    // eventually a leader should be elected
    assertThat(raftContexts.hasLeaderAtTheLatestTerm())
        .describedAs("Leader election should be completed if there are no messages lost.")
        .isTrue();

    raftContexts.assertAllEntriesCommittedAndReplicatedToAll(newMembers);
    raftContexts.assertAllLogsEqual();
    raftContexts.assertNoGapsInLog();
    raftContexts.assertNoJournalAppendErrors();
    raftContexts.assertNoDataLoss();
  }

  private void runUntilMembersAreInSync(final Map<MemberId, RaftContext> newMembers) {
    int maxStepsToReplicateEntries = 2000;
    while (!(raftContexts.hasLeaderAtTheLatestTerm()
            && raftContexts.hasReplicatedAllEntries(newMembers)
            && raftContexts.hasCommittedAllEntries(newMembers))
        && maxStepsToReplicateEntries-- > 0) {
      raftContexts.runUntilDone();
      raftContexts.processAllMessage();
      raftContexts.tickHeartbeatTimeout();
    }
  }

  private void assertThatConfigurationContainsOnly0and2(final int memberId) {
    final var members =
        raftContexts.getRaftContext(memberId).getCluster().getConfiguration().allMembers().stream()
            .map(r -> r.memberId().id())
            .toList();
    assertThat(members)
        .describedAs("Configuration must have only members 0 and 2")
        .containsExactlyInAnyOrder("0", "2");
  }

  @Provide
  Arbitrary<List<RaftOperation>> raftOperations() {
    final var operation = Arbitraries.of(defaultOperations);
    return operation.list().ofSize(OPERATION_SIZE);
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
    // Couldnot make @TempDir annotation work
    raftDataDirectory = Files.createTempDirectory(null);
    raftContexts = new ControllableRaftContexts(NODE_COUNT);
    raftContexts.setup(raftDataDirectory, random);
  }

  private static final class ForceConfigureOperation {
    private CompletableFuture<Void> forceConfigureCompleted = new CompletableFuture<>();

    public void reset() {
      forceConfigureCompleted = new CompletableFuture<>();
    }

    public void run(final ControllableRaftContexts raftContexts, final MemberId memberId) {
      if (forceConfigureCompleted.isDone()) {
        // only apply force configure once
        return;
      }

      new ReconfigurationHelper(raftContexts.getRaftContext(0))
          // Always use the same configuration for simplifying the tests
          .forceConfigure(Map.of(MemberId.from("0"), ACTIVE, MemberId.from("2"), ACTIVE))
          .thenApply(ignore -> forceConfigureCompleted.complete(null));
    }
  }
}
