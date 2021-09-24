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
import static org.mockito.Mockito.mock;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.partition.RaftElectionConfig;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.protocol.ControllableRaftServerProtocol;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.raft.snapshot.TestSnapshotStore;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.zeebe.NoopEntryValidator;
import io.atomix.raft.zeebe.ZeebeLogAppender.AppendListener;
import io.camunda.zeebe.util.collection.Tuple;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.slf4j.LoggerFactory;

/**
 * Uses a DeterministicScheduler and controllable messaging layer to get a deterministic execution
 * of raft threads. Note:- Currently there is some non-determinism hidden in the raft. Hence the
 * resulting execution is not fully deterministic.
 */
public final class ControllableRaftContexts {

  private final Map<MemberId, ControllableRaftServerProtocol> serverProtocols = new HashMap<>();
  private final Map<MemberId, Queue<Tuple<Runnable, CompletableFuture>>> messageQueue =
      new HashMap<>();
  private final Map<MemberId, DeterministicSingleThreadContext> deterministicExecutors =
      new HashMap<>();

  private Path directory;

  private final int nodeCount;
  private final Map<MemberId, RaftContext> raftServers = new HashMap<>();
  private Duration electionTimeout;
  private Duration hearbeatTimeout;
  private int nextEntry = 0;

  // Used only for verification. Map[term -> leader]
  private final Map<Long, MemberId> leadersAtTerms = new HashMap<>();

  public ControllableRaftContexts(final int nodeCount) {
    this.nodeCount = nodeCount;
  }

  public Map<MemberId, RaftContext> getRaftServers() {
    return raftServers;
  }

  public RaftContext getRaftContext(final int memberId) {
    return raftServers.get(MemberId.from(String.valueOf(memberId)));
  }

  public RaftContext getRaftContext(final MemberId memberId) {
    return raftServers.get(memberId);
  }

  public void setup(final Path directory, final Random random) throws Exception {
    this.directory = directory;
    if (nodeCount > 0) {
      createRaftContexts(nodeCount, random);
    }
    joinRaftServers();
    electionTimeout = getRaftContext(0).getElectionTimeout();
    hearbeatTimeout = getRaftContext(0).getHeartbeatInterval();

    // expecting 0 to be the leader
    tickHeartbeatTimeout(0);
  }

  public void shudown() throws IOException {
    raftServers.forEach((m, c) -> c.close());
    raftServers.clear();
    serverProtocols.clear();
    deterministicExecutors.forEach((m, e) -> e.close());
    deterministicExecutors.clear();
    messageQueue.clear();
    leadersAtTerms.clear();
    directory = null;
  }

  private void joinRaftServers() throws InterruptedException, ExecutionException, TimeoutException {
    final Set<CompletableFuture<Void>> futures = new HashSet<>();
    final var servers = getRaftServers();
    final var serverIds = new ArrayList<>(servers.keySet());
    final long electionTimeout =
        servers.get(MemberId.from(String.valueOf(0))).getElectionTimeout().toMillis();
    Collections.sort(serverIds);
    servers.forEach(
        (memberId, raftContext) -> futures.add(raftContext.getCluster().bootstrap(serverIds)));

    runUntilDone(0);
    // trigger election on 0 so that 0 is initially the leader
    getDeterministicScheduler(MemberId.from(String.valueOf(0)))
        .tick(2 * electionTimeout, TimeUnit.MILLISECONDS);
    final var joinFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));

    // Should trigger executions and message delivery
    while (!joinFuture.isDone()) {
      processAllMessage();
      runUntilDone();
    }
    joinFuture.get(1, TimeUnit.SECONDS);
  }

  private void createRaftContexts(final int nodeCount, final Random random) {
    for (int i = 0; i < nodeCount; i++) {
      final var memberId = MemberId.from(String.valueOf(i));
      raftServers.put(memberId, createRaftContext(memberId, random));
    }
  }

  public RaftContext createRaftContext(final MemberId memberId, final Random random) {
    final var raft =
        new RaftContext(
            memberId.id() + "-partition-1",
            1,
            memberId,
            mock(ClusterMembershipService.class),
            new ControllableRaftServerProtocol(memberId, serverProtocols, messageQueue),
            createStorage(memberId),
            getRaftThreadContextFactory(memberId),
            () -> random,
            RaftElectionConfig.ofDefaultElection(),
            new RaftPartitionConfig());
    raft.setEntryValidator(new NoopEntryValidator());
    return raft;
  }

  private RaftThreadContextFactory getRaftThreadContextFactory(final MemberId memberId) {
    return (factory, uncaughtExceptionHandler) ->
        deterministicExecutors.computeIfAbsent(
            memberId,
            m ->
                (DeterministicSingleThreadContext)
                    DeterministicSingleThreadContext.createContext());
  }

  private RaftStorage createStorage(final MemberId memberId) {
    return createStorage(memberId, Function.identity());
  }

  private RaftStorage createStorage(
      final MemberId memberId,
      final Function<RaftStorage.Builder, RaftStorage.Builder> configurator) {
    final var memberDirectory = getMemberDirectory(directory, memberId.toString());
    final RaftStorage.Builder defaults =
        RaftStorage.builder()
            .withDirectory(memberDirectory)
            .withMaxSegmentSize(1024 * 10)
            .withFreeDiskSpace(100)
            .withSnapshotStore(new TestSnapshotStore(new AtomicReference<>()));
    return configurator.apply(defaults).build();
  }

  private File getMemberDirectory(final Path directory, final String s) {
    return new File(directory.toFile(), s);
  }

  public ControllableRaftServerProtocol getServerProtocol(final MemberId memberId) {
    return serverProtocols.get(memberId);
  }

  public ControllableRaftServerProtocol getServerProtocol(final int memberId) {
    return getServerProtocol(MemberId.from(String.valueOf(memberId)));
  }

  public DeterministicScheduler getDeterministicScheduler(final MemberId memberId) {
    return deterministicExecutors.get(memberId).getDeterministicScheduler();
  }

  public DeterministicScheduler getDeterministicScheduler(final int memberId) {
    return getDeterministicScheduler(MemberId.from(String.valueOf(memberId)));
  }

  // ------ Methods to control the execution of raft threads --------

  // run until there are no more task to process
  public void runUntilDone() {
    final var serverIds = raftServers.keySet();
    serverIds.forEach(memberId -> getDeterministicScheduler(memberId).runUntilIdle());
  }

  // run until there are no more tasks to processon member's scheduler
  public void runUntilDone(final int memberId) {
    getServerProtocol(memberId).receiveAll();
    getDeterministicScheduler(memberId).runUntilIdle();
  }

  public void runUntilDone(final MemberId memberId) {
    getDeterministicScheduler(memberId).runUntilIdle();
  }

  public void runNextTask(final MemberId memberId) {
    final var scheduler = getDeterministicScheduler(memberId);
    if (!scheduler.isIdle()) {
      scheduler.runNextPendingCommand();
    }
  }

  // Submit all messages from the incoming queue to the schedulers to process
  public void processAllMessage() {
    final var serverIds = raftServers.keySet();
    serverIds.forEach(memberId -> getServerProtocol(memberId).receiveAll());
  }

  public void processAllMessage(final MemberId memberId) {
    getServerProtocol(memberId).receiveAll();
  }

  // Submit the next message from the incoming queue to the scheduler of memberid.
  public void processNextMessage(final MemberId memberId) {
    getServerProtocol(memberId).receiveNextMessage();
  }

  public void tickElectionTimeout(final int memberId) {
    getDeterministicScheduler(memberId).tick(electionTimeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  public void tickElectionTimeout(final MemberId memberId) {
    getDeterministicScheduler(memberId).tick(electionTimeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  public void tickHeartbeatTimeout(final int memberId) {
    getDeterministicScheduler(memberId).tick(hearbeatTimeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  public void tickHeartbeatTimeout(final MemberId memberId) {
    getDeterministicScheduler(memberId).tick(hearbeatTimeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  public void tick(final MemberId memberId, final Duration time) {
    getDeterministicScheduler(memberId).tick(time.toMillis(), TimeUnit.MILLISECONDS);
  }

  // Execute an append on memberid. If memberid is not the the leader, the append will be rejected.
  private void clientAppend(final MemberId memberId) {
    final var role = getRaftContext(memberId).getRaftRole();
    if (role instanceof LeaderRole) {
      LoggerFactory.getLogger("TEST").info("Appending on leader {}", memberId.id());
      final ByteBuffer data = ByteBuffer.allocate(Integer.BYTES).putInt(0, nextEntry++);
      final LeaderRole leaderRole = (LeaderRole) role;
      leaderRole.appendEntry(0, 1, data, mock(AppendListener.class));
    }
  }

  // Find current leader and execute an append
  public void clientAppendOnLeader() {
    final var leaderTerm = leadersAtTerms.keySet().stream().max(Long::compareTo);
    if (leaderTerm.isPresent()) {
      final var leader = leadersAtTerms.get(leaderTerm.get());
      if (leader != null) {
        clientAppend(leader);
      }
    }
  }

  // ----------------------- Verifications -----------------------------

  // Verify that committed entries in all logs are equal
  public void assertAllLogsEqual() {
    final var readers =
        raftServers.values().stream()
            .collect(Collectors.toMap(Function.identity(), s -> s.getLog().openCommittedReader()));
    long index = 0;
    while (true) {
      final var entries =
          readers.keySet().stream()
              .filter(s -> readers.get(s).hasNext())
              .collect(Collectors.toMap(s -> s.getName(), s -> readers.get(s).next()));
      if (entries.size() == 0) {
        break;
      }
      assertThat(entries.values().stream().distinct().count())
          .withFailMessage(
              "Expected to find the same entry at a committed index on all nodes, but found %s",
              entries)
          .isEqualTo(1);
      index++;
    }
    final var commitIndexOnLeader =
        raftServers.values().stream()
            .map(RaftContext::getCommitIndex)
            .max(Long::compareTo)
            .orElseThrow();
    assertThat(index).isEqualTo(commitIndexOnLeader);
    readers.values().forEach(RaftLogReader::close);
  }

  public void assertOnlyOneLeader() {
    raftServers.values().forEach(s -> updateAndVerifyLeaderTerm(s));
  }

  private void updateAndVerifyLeaderTerm(final RaftContext s) {
    final long term = s.getTerm();
    if (s.getLeader() != null) {
      final var leader = s.getLeader().memberId();
      if (leadersAtTerms.containsKey(term)) {
        final var knownLeader = leadersAtTerms.get(term);
        assertThat(knownLeader)
            .withFailMessage("Found two leaders %s %s at term %s", knownLeader, leader, term)
            .isEqualTo(leader);
      } else {
        leadersAtTerms.put(term, leader);
      }
    }
  }
}
