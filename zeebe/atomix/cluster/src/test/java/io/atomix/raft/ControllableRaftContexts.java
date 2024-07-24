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

import static io.atomix.raft.impl.RaftContext.State.READY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.partition.RaftElectionConfig;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.protocol.ControllableRaftServerProtocol;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.zeebe.EntryValidator.NoopEntryValidator;
import io.atomix.raft.zeebe.ZeebeLogAppender.AppendListener;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.snapshots.testing.TestFileBasedSnapshotStore;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.collection.Tuple;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses a DeterministicScheduler and controllable messaging layer to get a deterministic execution
 * of raft threads.
 */
public final class ControllableRaftContexts {

  private static final Logger LOG = LoggerFactory.getLogger("TEST");

  private final Map<MemberId, ControllableRaftServerProtocol> serverProtocols = new HashMap<>();
  private final Map<MemberId, Queue<Tuple<Runnable, CompletableFuture<?>>>> messageQueue =
      new HashMap<>();
  private final Map<MemberId, DeterministicSingleThreadContext> deterministicExecutors =
      new HashMap<>();

  private Path directory;
  private Random random;

  private final int nodeCount;
  private final Map<MemberId, RaftContext> raftServers = new HashMap<>();
  private final Map<MemberId, TestFileBasedSnapshotStore> snapshotStores = new HashMap<>();
  private Duration electionTimeout;
  private Duration heartbeatTimeout;
  private int nextEntry = 0;

  // Used only for verification. Map[term -> leader]
  private final NavigableMap<Long, MemberId> leadersAtTerms = new TreeMap<>();
  private final AppendListener appendListener = mock(AppendListener.class);
  private final DataLossChecker dataLossChecker = new DataLossChecker(appendListener);

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
    this.random = random;
    if (nodeCount > 0) {
      createRaftContexts(nodeCount, random);
    }
    joinRaftServers();
    electionTimeout = getRaftContext(0).getElectionTimeout();
    heartbeatTimeout = getRaftContext(0).getHeartbeatInterval();

    // expecting 0 to be the leader
    tickHeartbeatTimeout(0);
  }

  public void shutdown() throws IOException {
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
      createRaftContextForMember(random, i);
    }
  }

  private RaftContext createRaftContextForMember(final Random random, final int nodeId) {
    final var memberId = MemberId.from(String.valueOf(nodeId));
    final var snapshotStore =
        new TestFileBasedSnapshotStore(
            nodeId,
            getMemberDirectory(directory, memberId.toString()).toPath().resolve("snapshots"),
            new TestConcurrencyControl());
    snapshotStores.put(memberId, snapshotStore);
    final RaftContext raftContext =
        createRaftContext(
            memberId, random, createStorage(memberId, cfg -> cfg.withSnapshotStore(snapshotStore)));
    raftServers.put(memberId, raftContext);
    return raftContext;
  }

  public RaftContext createRaftContext(
      final MemberId memberId, final Random random, final RaftStorage storage) {
    final var raft =
        new RaftContext(
            memberId.id() + "-partition-1",
            1,
            memberId,
            mock(ClusterMembershipService.class, withSettings().stubOnly()),
            new ControllableRaftServerProtocol(memberId, serverProtocols, messageQueue),
            storage,
            getRaftThreadContextFactory(memberId),
            () -> random,
            RaftElectionConfig.ofPriorityElection(nodeCount, Integer.parseInt(memberId.id()) + 1),
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

  private RaftStorage createStorage(
      final MemberId memberId,
      final Function<RaftStorage.Builder, RaftStorage.Builder> configurator) {
    final var memberDirectory = getMemberDirectory(directory, memberId.toString());
    final RaftStorage.Builder defaults =
        RaftStorage.builder()
            .withDirectory(memberDirectory)
            .withMaxSegmentSize(1024 * 10)
            .withFreeDiskSpace(100);
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

  // run until there are no more tasks to process on member's scheduler
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

  // Submit the next message from the incoming queue to the scheduler of memberId.
  public void processNextMessage(final MemberId memberId) {
    getServerProtocol(memberId).receiveNextMessage();
  }

  public void tickElectionTimeout(final int memberId) {
    tick(memberId, electionTimeout);
  }

  public void tickElectionTimeout(final MemberId memberId) {
    tick(memberId, electionTimeout);
  }

  public void tickHeartbeatTimeout(final int memberId) {
    tick(memberId, heartbeatTimeout);
  }

  public void tickHeartbeatTimeout(final MemberId memberId) {
    tick(memberId, heartbeatTimeout);
  }

  public void tickHeartbeatTimeout() {
    tick(heartbeatTimeout);
  }

  public void tick(final Duration time) {
    final var serverIds = raftServers.keySet();
    serverIds.forEach(memberId -> tick(memberId, time));
  }

  public void tick(final int memberId, final Duration time) {
    getDeterministicScheduler(memberId).tick(time.toMillis(), TimeUnit.MILLISECONDS);
    getServerProtocol(memberId).tick(time.toMillis());
  }

  public void tick(final MemberId memberId, final Duration time) {
    getDeterministicScheduler(memberId).tick(time.toMillis(), TimeUnit.MILLISECONDS);
    getServerProtocol(memberId).tick(time.toMillis());
  }

  // Execute an append on memberId. If memberId is not the leader, the append request will be
  // rejected.
  private void clientAppend(final MemberId memberId) {
    final var role = getRaftContext(memberId).getRaftRole();
    if (role instanceof final LeaderRole leaderRole) {
      LOG.info("Appending on leader {}", memberId.id());
      final ByteBuffer data = ByteBuffer.allocate(Integer.BYTES).putInt(0, nextEntry++);
      leaderRole.appendEntry(nextEntry, nextEntry, data, dataLossChecker);
    }
  }

  // Find current leader and execute an append request
  public void clientAppendOnLeader() {
    final var leaderTerm = leadersAtTerms.keySet().stream().max(Long::compareTo);
    if (leaderTerm.isPresent()) {
      final var leader = leadersAtTerms.get(leaderTerm.get());
      if (leader != null) {
        clientAppend(leader);
      }
    }
  }

  public void snapshotAndCompact(final MemberId memberId) {
    final RaftContext raftContext = raftServers.get(memberId);
    // Take snapshot at an index between lastSnapshotIndex and current commitIndex
    final TestFileBasedSnapshotStore testSnapshotStore = snapshotStores.get(memberId);
    final var startIndex =
        Math.max(
            raftContext.getLog().getFirstIndex(), testSnapshotStore.getCurrentSnapshotIndex() + 1);
    if (startIndex >= raftContext.getCommitIndex()) {
      // cannot take snapshot
      return;
    }
    final long snapshotIndex = random.nextLong(startIndex, raftContext.getCommitIndex());
    try (final RaftLogReader reader = raftContext.getLog().openCommittedReader()) {
      reader.seek(snapshotIndex);
      final long term = reader.next().term();

      testSnapshotStore.newSnapshot(snapshotIndex, term, random.nextInt(1, 10), random);

      LOG.info(
          "Snapshot taken at index {}. Current commit index is {}",
          snapshotIndex,
          raftContext.getCommitIndex());
    }

    raftContext.getThreadContext().execute(() -> raftContext.getLog().deleteUntil(snapshotIndex));
  }

  public void restart(final MemberId memberId) {
    raftServers.get(memberId).close();
    deterministicExecutors.remove(memberId).close();
    snapshotStores.get(memberId).close();
    final var newContext = createRaftContextForMember(random, Integer.parseInt(memberId.id()));
    newContext.getCluster().bootstrap(raftServers.keySet());
  }

  // This is a different from other operations, as it restarts the node and force operations on
  // other
  // nodes to wait until this node is ready. This is required because we can only tolerate data loss
  // of one node at a time.
  public void restartWithDataLoss(final MemberId memberId) {
    LOG.info("Shutting down member {}", memberId.id());
    raftServers.get(memberId).close();
    deterministicExecutors.remove(memberId).close();
    final var nodeBeforeRestart = raftServers.remove(memberId);

    // Wait until the other two members become a quorum and elect a leader. Otherwise, the restarted
    // node and the follower without the latest log can become a quorum and result in data loss.
    waitUntilThereIsAnotherLeader(memberId);

    // Restart the node with empty state
    final var dataDirectory = nodeBeforeRestart.getStorage().directory().toPath();
    LOG.info("Deleting directory {} of member {}", dataDirectory, memberId.id());
    try {
      FileUtil.deleteFolderIfExists(dataDirectory);
    } catch (final IOException e) {
      LOG.error("Failed to delete directory {} of member {}", dataDirectory, memberId.id());
      throw new UncheckedIOException(e);
    }
    final var newContext = createRaftContextForMember(random, Integer.parseInt(memberId.id()));
    newContext.getCluster().bootstrap(raftServers.keySet());

    raftServers.put(memberId, newContext);

    // Wait until member has recovered lost data and have caught up with the leader
    waitUntilMembersIsReadyIn(newContext, 100);
  }

  private void waitUntilThereIsAnotherLeader(final MemberId memberId) {
    int steps = 500;

    while (steps-- > 0) {
      final Entry<Long, MemberId> currentLeaderEntry = leadersAtTerms.lowerEntry(Long.MAX_VALUE);
      final var currentLeader =
          currentLeaderEntry != null ? currentLeaderEntry.getValue().id() : null;
      // If the node was leader before restart, wait until new leader is elected &&
      // Ensure that other nodes in the quorum is up-to-date. Otherwise, after restart this node
      // can form a quorum with a follower which is not up-to-date.
      if (!memberId.id().equals(currentLeader)
          && hasLeaderAtTheLatestTerm()
          && hasReplicatedAllEntries()) {
        final var leader = leadersAtTerms.lowerEntry(Long.MAX_VALUE).getValue();
        LOG.info("Current leader is {}. ", leader.id());
        break;
      } else {
        tick(Duration.ofMillis(50));
        processAllMessage();
        runUntilDone();
      }
    }
    assertThat(hasLeaderAtTheLatestTerm()).isTrue();
    assertThat(hasReplicatedAllEntries()).isTrue();
  }

  private void waitUntilMembersIsReadyIn(final RaftContext member, final int steps) {
    int iter = steps;
    while (member.getState() != READY && iter-- > 0) {
      runUntilDone();
      processAllMessage();
      tickHeartbeatTimeout();
    }
    assertThat(member.getState())
        .describedAs(
            "Member should be ready in %d steps, if there are no failures or timeouts"
                .formatted(steps))
        .isEqualTo(READY);
  }

  // ----------------------- Verifications -----------------------------

  // Verify that committed entries in all logs are equal
  public void assertAllLogsEqual() {
    final var readers =
        raftServers.values().stream()
            .collect(Collectors.toMap(Function.identity(), s -> s.getLog().openCommittedReader()));
    long index =
        raftServers.values().stream()
                .map(s -> s.getLog().getFirstIndex())
                .min(Long::compareTo)
                .orElse(1L)
            - 1;

    final long commitIndexOnLeader =
        raftServers.values().stream()
            .map(RaftContext::getCommitIndex)
            .max(Long::compareTo)
            .orElseThrow();

    while (index < commitIndexOnLeader) {
      final var nextIndex = index + 1;
      final var entries =
          readers.keySet().stream()
              .filter(s -> readers.get(s).hasNext())
              // only compared not compacted entries
              .filter(s -> s.getLog().getFirstIndex() <= nextIndex)
              .collect(Collectors.toMap(RaftContext::getName, s -> readers.get(s).next()));

      assertThat(entries.values().stream().distinct().count())
          .withFailMessage(
              () ->
                  "Expected to find the same entry at a committed index on all nodes, but found %s"
                      .formatted(entries))
          .isLessThanOrEqualTo(1);
      index++;
    }

    readers.values().forEach(RaftLogReader::close);
  }

  public void assertAtMostOneLeader() {
    raftServers.values().forEach(this::updateAndVerifyLeaderTerm);
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

  // If a node is in CandidateRole, then it will update the term. But until the election is
  // completed, there is no leader at that term.
  public boolean hasLeaderAtTheLatestTerm() {
    // update leadersAtTerms
    assertAtMostOneLeader();

    final var currentTerm =
        raftServers.values().stream().map(RaftContext::getTerm).max(Long::compareTo).orElseThrow();
    return leadersAtTerms.get(currentTerm) != null;
  }

  boolean hasCommittedAllEntries() {
    return hasCommittedAllEntries(raftServers);
  }

  boolean hasCommittedAllEntries(final Map<MemberId, RaftContext> raftServers) {
    return raftServers.values().stream()
        .allMatch(
            s -> {
              final var lastCommittedEntry = getLastCommittedEntry(s);
              final var lastUncommittedEntry = getLastUncommittedEntry(s);

              return lastUncommittedEntry != null
                  && lastUncommittedEntry.equals(lastCommittedEntry);
            });
  }

  boolean hasReplicatedAllEntries() {
    return hasReplicatedAllEntries(raftServers);
  }

  boolean hasReplicatedAllEntries(final Map<MemberId, RaftContext> raftMembers) {
    final boolean allReplicasHaveSameLastIndex =
        raftMembers.values().stream()
                .map(RaftContext::getLog)
                .map(RaftLog::getLastIndex)
                .distinct()
                .count()
            == 1;
    final boolean allReplicasHaveSameLastEntry =
        raftMembers.values().stream().map(this::getLastUncommittedEntry).distinct().count() == 1;
    // should check both to cover cases where one log is empty
    return allReplicasHaveSameLastIndex && allReplicasHaveSameLastEntry;
  }

  void assertAllEntriesCommittedAndReplicatedToAll() {
    assertAllEntriesCommittedAndReplicatedToAll(raftServers);
  }

  void assertAllEntriesCommittedAndReplicatedToAll(final Map<MemberId, RaftContext> raftServers) {
    raftServers.forEach(
        (memberId, raftServer) -> {
          final var lastCommittedEntry = getLastCommittedEntry(raftServer);
          final var lastUncommittedEntry = getLastUncommittedEntry(raftServer);

          assertThat(lastCommittedEntry)
              .describedAs("All entries should be committed in %s", memberId.id())
              .isEqualTo(lastUncommittedEntry);
        });

    assertThat(hasReplicatedAllEntries(raftServers))
        .describedAs("All entries are replicated to all followers")
        .isTrue();
  }

  private IndexedRaftLogEntry getLastUncommittedEntry(final RaftContext s) {
    try (final var uncommittedReader = s.getLog().openUncommittedReader()) {
      uncommittedReader.seekToLast();
      if (uncommittedReader.hasNext()) {
        return uncommittedReader.next();
      }
      return null;
    }
  }

  private IndexedRaftLogEntry getLastCommittedEntry(final RaftContext s) {
    try (final var committedReader = s.getLog().openCommittedReader()) {
      committedReader.seekToLast();
      if (committedReader.hasNext()) {
        return committedReader.next();
      }
      return null;
    }
  }

  public void assertNoGapsInLog() {
    raftServers.keySet().forEach(this::assertNoGapsInLog);
  }

  private void assertNoGapsInLog(final MemberId memberId) {
    final RaftContext s = raftServers.get(memberId);
    final long firstIndex = s.getLog().getFirstIndex();
    long nextIndex = firstIndex;
    try (final var reader = s.getLog().openCommittedReader()) {
      while (reader.hasNext()) {
        assertThat(reader.next().index())
            .describedAs("There is no gap in the log %s", memberId.id())
            .isEqualTo(nextIndex);
        nextIndex++;
      }
    }

    if (firstIndex != 1) {
      final var currentSnapshotIndex = snapshotStores.get(memberId).getCurrentSnapshotIndex();
      assertThat(currentSnapshotIndex)
          .describedAs("The log is compacted in %s. Hence a snapshot must exist.")
          .isGreaterThanOrEqualTo(firstIndex - 1);
    }
  }

  public void assertAllMembersAreReady() {
    assertThat(allMembersAreReady()).isTrue();
  }

  public boolean allMembersAreReady() {
    return raftServers.values().stream()
        .map(RaftContext::getState)
        .filter(state -> !READY.equals(state))
        .findAny()
        .isEmpty();
  }

  public void assertNoJournalAppendErrors() {
    verify(appendListener, times(0)).onWriteError(any(JournalException.class));
  }

  public void assertNoDataLoss() {
    assertThat(dataLossChecker.hasDataLoss())
        .withFailMessage(dataLossChecker.getFailMessage())
        .isFalse();
  }

  // AppendListener that verifies that no committed entries are overwritten. This checker keeps
  // track of committed entries and its checksum. After a leader change or restart, if the new
  // leader commits a new entry at the same index because it is missing already committed entries,
  // the checker can detect it.
  static class DataLossChecker implements AppendListener {

    final AppendListener delegate;

    // Keep track of committed entries and its checksum.
    final Map<Long, Long> committedIndexToChecksumMap = new HashMap<>();

    final Map<Long, Long> pendingWriteToBeCommitted = new HashMap<>();

    private String failMessage = "";
    private boolean dataloss = false;

    DataLossChecker(final AppendListener delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onWrite(final IndexedRaftLogEntry indexed) {
      final long index = indexed.index();
      final var entryChecksum = indexed.getPersistedRaftRecord().checksum();
      pendingWriteToBeCommitted.put(index, entryChecksum);
      delegate.onWrite(indexed);
    }

    @Override
    public void onWriteError(final Throwable error) {
      delegate.onWriteError(error);
    }

    @Override
    public void onCommit(final long index, final long highestPosition) {
      if (committedIndexToChecksumMap.containsKey(index)
          && pendingWriteToBeCommitted.containsKey(index)
          && !pendingWriteToBeCommitted.get(index).equals(committedIndexToChecksumMap.get(index))) {
        failMessage =
            "Committed entry at index %d checksum %d is being overwritten by entry with checksum %d"
                .formatted(
                    index,
                    committedIndexToChecksumMap.get(index),
                    pendingWriteToBeCommitted.get(index));
        LOG.info(failMessage);
        dataloss = true;
      }
      committedIndexToChecksumMap.put(index, pendingWriteToBeCommitted.remove(index));
      delegate.onCommit(index, highestPosition);
    }

    @Override
    public void onCommitError(final long index, final Throwable error) {
      delegate.onCommitError(index, error);
    }

    public String getFailMessage() {
      return failMessage;
    }

    public boolean hasDataLoss() {
      return dataloss;
    }
  }
}
