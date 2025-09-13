/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.Set;
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

@PropertyDefaults(tries = 10, shrinking = ShrinkingMode.OFF, edgeCases = EdgeCasesMode.NONE)
public class RandomizedRaftJoinTest {

  private static final Logger LOG = LoggerFactory.getLogger(RandomizedRaftJoinTest.class);
  private static final int OPERATION_SIZE = 1000;

  private ControllableRaftContexts raftContexts;
  private Path raftDataDirectory;
  private MemberId member0;
  private MemberId member1;
  private List<RaftOperation> operationsWithRestarts;

  @BeforeProperty
  public void initMembers() {
    // Initialize the two member IDs for the 2-node cluster
    member0 = MemberId.from("0");
    member1 = MemberId.from("1");
    // Create ControllableRaftContexts with 2 nodes
    raftContexts = new ControllableRaftContexts(2);
    operationsWithRestarts = RaftOperation.getRaftOperationsWithRestarts();
  }

  @AfterTry
  public void shutDownRaftNodes() throws IOException {
    if (raftContexts != null) {
      raftContexts.shutdown();
    }
    if (raftDataDirectory != null) {
      FileUtil.deleteFolder(raftDataDirectory);
      raftDataDirectory = null;
    }
  }

  @Property
  void joinCompletes(
      @ForAll("raftOperations") final List<RaftOperation> raftOperations,
      @ForAll("raftMembers") final List<MemberId> raftMembers,
      @ForAll("seeds") final long seed)
      throws Exception {
    setUpRaftNodes(new Random(seed));

    var joinFuture = raftContexts.join(member1, Set.of(member0));

    // given - when there are failures such as message loss
    final var memberIter = raftMembers.iterator();
    for (final RaftOperation operation : raftOperations) {
      final MemberId member = memberIter.next();
      LOG.info("{} on {}", operation, member);
      operation.run(raftContexts, member);
      if (joinFuture.isCompletedExceptionally()) {
        // retry join
        LOG.info("Join failed. Retrying...");
        joinFuture = raftContexts.join(member1, Set.of(member0));
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
        joinFuture = raftContexts.join(member1, Set.of(member0));
      }

      raftContexts.runUntilDone();
      raftContexts.processAllMessage();
      raftContexts.tickHeartbeatTimeout();
    }

    // then
    assertThat(joinFuture).describedAs("Join of member 1 should be completed").isCompleted();
    assertThat(raftContexts.hasLeaderAtTheLatestTerm()).describedAs("There is a leader").isTrue();
    raftContexts.assertAllMembersAreReady();
  }

  private void setUpRaftNodes(final Random random) throws Exception {
    // Create temporary directory for raft data
    raftDataDirectory = Files.createTempDirectory(null);

    // Bootstrap only with member 0 (single node cluster initially)
    raftContexts.setup(raftDataDirectory, random, Set.of(0));

    LOG.info("Set up 2-node raft cluster, bootstrapped with member 0 only");
  }

  @Provide
  Arbitrary<List<RaftOperation>> raftOperations() {
    final var operation = Arbitraries.of(operationsWithRestarts);
    return operation.list().ofSize(OPERATION_SIZE);
  }

  @Provide
  Arbitrary<List<MemberId>> raftMembers() {
    final var members = Arbitraries.of(member0, member1);
    return members.list().ofSize(OPERATION_SIZE);
  }

  @Provide
  Arbitrary<Long> seeds() {
    return Arbitraries.longs();
  }
}
