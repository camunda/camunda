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

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Builder;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.primitive.TestMember;
import io.atomix.raft.protocol.PersistedRaftRecord;
import io.atomix.raft.protocol.TestRaftProtocolFactory;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.raft.snapshot.InMemorySnapshot;
import io.atomix.raft.snapshot.TestSnapshotStore;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.log.entry.RaftEntry;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.atomix.raft.zeebe.EntryValidator;
import io.atomix.raft.zeebe.EntryValidator.NoopEntryValidator;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.atomix.utils.AbstractIdentifier;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.atomix.utils.concurrent.ThreadContext;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class RaftRule extends ExternalResource {

  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private volatile int nextId;
  private volatile List<RaftMember> members;
  private Map<String, Long> memberLog;
  private final Map<String, RaftServer> servers = new HashMap<>();
  private volatile TestRaftProtocolFactory protocolFactory;
  private volatile ThreadContext context;
  private Path directory;
  private final int nodeCount;
  private volatile long highestCommit;
  private final AtomicReference<CommitAwaiter> commitAwaiterRef = new AtomicReference<>();
  private long position;
  private EntryValidator entryValidator = new NoopEntryValidator();
  // Keep a reference to the snapshots to ensure they are persisted across the restarts.
  private Map<String, AtomicReference<InMemorySnapshot>> snapshots;
  private Map<String, TestSnapshotStore> snapshotStores;
  private final BiFunction<MemberId, Builder, Builder> serverConfigurator;
  private final Random random = new Random();

  private RaftRule(
      final int nodeCount, final BiFunction<MemberId, Builder, Builder> serverConfigurator) {
    this.nodeCount = nodeCount;
    this.serverConfigurator = serverConfigurator;
  }

  public static RaftRule withBootstrappedNodes(
      final int nodeCount, final BiFunction<MemberId, Builder, Builder> serverConfigurator) {
    if (nodeCount < 1) {
      throw new IllegalArgumentException("Expected to have at least one node to configure.");
    }
    return new RaftRule(nodeCount, serverConfigurator);
  }

  public static RaftRule withBootstrappedNodes(final int nodeCount) {
    return new RaftRule(nodeCount, (m, b) -> b);
  }

  public static RaftRule withoutNodes() {
    return new RaftRule(-1, (m, b) -> b);
  }

  public RaftRule setEntryValidator(final EntryValidator entryValidator) {
    this.entryValidator = entryValidator;
    return this;
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    final var statement = super.apply(base, description);
    return temporaryFolder.apply(statement, description);
  }

  @Override
  protected void before() throws Throwable {
    directory = temporaryFolder.newFolder().toPath();

    position = 0;
    members = new ArrayList<>();
    memberLog = new ConcurrentHashMap<>();
    snapshotStores = new HashMap<>();
    snapshots = new HashMap<>();
    nextId = 0;
    context = new SingleThreadContext("raft-test-messaging-%d");
    protocolFactory = new TestRaftProtocolFactory(context);

    if (nodeCount > 0) {
      createServers(nodeCount, serverConfigurator);
    }
  }

  @Override
  protected void after() {
    try {
      CompletableFuture.allOf(
              servers.values().stream().map(RaftServer::shutdown).toArray(CompletableFuture[]::new))
          .get(30, TimeUnit.SECONDS);
    } catch (final Exception e) {
      // we failed to shutdown server
    }

    servers.clear();
    context.close();
    context = null;
    members.clear();
    nextId = 0;
    protocolFactory = null;
    highestCommit = 0;
    commitAwaiterRef.set(null);
    memberLog.clear();
    memberLog = null;
    position = 0;
    directory = null;
  }

  /**
   * Returns the next server address.
   *
   * @param type The startup member type.
   * @return The next server address.
   */
  private RaftMember nextMember(final RaftMember.Type type) {
    return new TestMember(nextNodeId(), type);
  }

  /**
   * Returns the next unique member identifier.
   *
   * @return The next unique member identifier.
   */
  private MemberId nextNodeId() {
    return MemberId.from(String.valueOf(++nextId));
  }

  /** Creates a set of Raft servers. */
  private List<RaftServer> createServers(
      final int nodes, final BiFunction<MemberId, Builder, Builder> serverConfigurator)
      throws Exception {
    final List<RaftServer> servers = new ArrayList<>();

    for (int i = 0; i < nodes; i++) {
      members.add(nextMember(RaftMember.Type.ACTIVE));
    }

    final CountDownLatch latch = new CountDownLatch(nodes);

    for (int i = 0; i < nodes; i++) {
      final var raftMember = members.get(i);
      final RaftServer server = createServer(raftMember.memberId(), serverConfigurator);
      server
          .bootstrap(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
          .thenAccept(this::addCommitListener)
          .thenRun(latch::countDown);
      servers.add(server);
    }

    latch.await(30, TimeUnit.SECONDS);

    return servers;
  }

  public String shutdownFollower() throws Exception {
    final var follower = getFollower().orElseThrow();
    shutdownServer(follower);
    return follower.name();
  }

  public Set<String> getNodes() {
    return servers.keySet();
  }

  public void joinCluster(final String nodeId) throws Exception {
    final RaftMember member = getRaftMember(nodeId);
    createServer(member.memberId(), serverConfigurator)
        .bootstrap(getMemberIds())
        .thenAccept(this::addCommitListener)
        .get(30, TimeUnit.SECONDS);
  }

  public void bootstrapNode(final String nodeId) throws Exception {
    final RaftMember member = getRaftMember(nodeId);
    createServer(member.memberId(), serverConfigurator)
        .bootstrap(getMemberIds())
        .thenAccept(this::addCommitListener)
        .get(30, TimeUnit.SECONDS);
  }

  public void bootstrapNodeWithMemberIds(final String nodeId, final List<MemberId> memberIds)
      throws Exception {
    final RaftMember member = getRaftMember(nodeId);
    createServer(member.memberId(), serverConfigurator)
        .bootstrap(memberIds)
        .thenAccept(this::addCommitListener)
        .get(30, TimeUnit.SECONDS);
  }

  public String shutdownLeader() throws Exception {
    final var leader = getLeader().orElseThrow();
    shutdownServer(leader);
    return leader.name();
  }

  public void restartLeader() throws Exception {
    awaitNewLeader();
    final var leader = shutdownLeader();
    joinCluster(leader);
  }

  public List<MemberId> getMemberIds() {
    return members.stream().map(RaftMember::memberId).collect(Collectors.toList());
  }

  public Collection<RaftServer> getServers() {
    return servers.values();
  }

  public void shutdownServer(final RaftServer raftServer) throws Exception {
    shutdownServer(raftServer.name());
  }

  public void shutdownServer(final String nodeName) throws Exception {
    servers.remove(nodeName).shutdown().get(30, TimeUnit.SECONDS);
    memberLog.remove(nodeName);
    snapshotStores.remove(nodeName);
  }

  private RaftMember getRaftMember(final String memberId) {
    return members.stream()
        .filter(member -> member.memberId().id().equals(memberId))
        .findFirst()
        .orElseThrow();
  }

  public void doSnapshot(final long index) {
    doSnapshot(index, 1);
  }

  public void doSnapshot(final long index, final int size) {
    awaitNewLeader();

    // we write on all nodes the same snapshot
    // this is similar to our current logic where leader takes a snapshot and replicates it
    // in the end all call the method #newSnapshot and the snapshot listener is triggered to compact

    for (final RaftServer raftServer : servers.values()) {
      doSnapshotOnMember(raftServer, index, size);
    }
  }

  public void doSnapshotOnMember(final RaftServer raftServer, final long index, final int size) {
    doSnapshotOnMember(raftServer, index, size, true);
  }

  public void doSnapshotOnMemberWithoutCompaction(
      final RaftServer raftServer, final long index, final int size) {
    doSnapshotOnMember(raftServer, index, size, false);
  }

  private void doSnapshotOnMember(
      final RaftServer raftServer, final long index, final int size, final boolean shouldCompact) {
    if (!raftServer.isRunning()) {
      return;
    }
    final var raftContext = raftServer.getContext();
    final var memberId = raftServer.cluster().getLocalMember().memberId();
    final var snapshotStore = getSnapshotStore(memberId.id());
    final var snapshot =
        InMemorySnapshot.newPersistedSnapshot(index, raftContext.getTerm(), size, snapshotStore);

    if (shouldCompact) {
      compact(memberId, snapshot);
    }
  }

  private void compact(final MemberId memberId, final PersistedSnapshot persistedSnapshot) {
    final var raftServer = servers.get(memberId.id());
    if (raftServer != null) {
      final var raftContext = raftServer.getContext();
      final var serviceManager = raftContext.getLogCompactor();
      serviceManager.setCompactableIndex(persistedSnapshot.getIndex());
      CompletableFuture.runAsync(
              serviceManager::compactIgnoringReplicationThreshold, raftContext.getThreadContext())
          .join();
    }
  }

  private TestSnapshotStore getSnapshotStore(final String memberId) {
    return snapshotStores.get(memberId);
  }

  private AtomicReference<InMemorySnapshot> getOrCreatePersistedSnapshot(final String memberId) {
    return snapshots.computeIfAbsent(memberId, i -> new AtomicReference<>());
  }

  public boolean allNodesHaveSnapshotWithIndex(final long index) {
    return servers.values().stream()
            .map(RaftServer::getContext)
            .map(RaftContext::getPersistedSnapshotStore)
            .map(PersistedSnapshotStore::getCurrentSnapshotIndex)
            .filter(idx -> idx == index)
            .count()
        == servers.values().size();
  }

  public PersistedSnapshot getSnapshotFromLeader() {
    final var leader = getLeader().orElseThrow();
    final var context = leader.getContext();
    final var snapshotStore = context.getPersistedSnapshotStore();
    return snapshotStore.getLatestSnapshot().orElseThrow();
  }

  public PersistedSnapshot getSnapshotOnNode(final String nodeId) {
    final var raftServer = servers.get(nodeId);
    final var context = raftServer.getContext();
    final var snapshotStore = context.getPersistedSnapshotStore();
    return snapshotStore.getLatestSnapshot().orElseThrow();
  }

  public void awaitNewLeader() {
    waitUntil(() -> getLeader().isPresent(), 100);
  }

  private void addCommitListener(final RaftServer raftServer) {
    raftServer
        .getContext()
        .addCommitListener(
            new RaftCommitListener() {
              @Override
              public void onCommit(final long index) {
                final var currentIndex = index;

                memberLog.put(raftServer.name(), currentIndex);
                if (highestCommit < currentIndex) {
                  highestCommit = currentIndex;
                }

                final var commitAwaiter = commitAwaiterRef.get();
                if (commitAwaiter != null && commitAwaiter.reachedCommit(currentIndex)) {
                  commitAwaiterRef.set(null);
                }
              }
            });
  }

  public Map<String, List<IndexedRaftLogEntry>> getMemberLogs() {

    final Map<String, List<IndexedRaftLogEntry>> memberLogs = new HashMap<>();

    for (final var server : servers.values()) {
      if (server.isRunning()) {

        final var log = server.getContext().getLog();
        final List<IndexedRaftLogEntry> entryList = new ArrayList<>();
        try (final var raftLogReader = log.openUncommittedReader()) {
          while (raftLogReader.hasNext()) {
            final var indexedEntry = raftLogReader.next();
            entryList.add(CopiedRaftLogEntry.of(indexedEntry));
          }
        }

        memberLogs.put(server.name(), entryList);
      }
    }

    return memberLogs;
  }

  public void awaitSameLogSizeOnAllNodes(final long lastIndex) {
    Awaitility.await("awaitSameLogSizeOnAllNodes")
        .until(
            () -> memberLog.values().stream().distinct().collect(Collectors.toList()),
            lastIndexes -> lastIndexes.size() == 1 && lastIndexes.get(0) == lastIndex);
  }

  private void waitUntil(final BooleanSupplier condition, final int retries) {
    waitUntil(condition, retries, () -> null);
  }

  private void waitUntil(
      final BooleanSupplier condition, int retries, final Supplier<String> errorMessage) {
    try {
      while (!condition.getAsBoolean() && retries > 0) {
        Thread.sleep(100);
        retries--;
      }
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }

    assertTrue(errorMessage.get(), condition.getAsBoolean());
  }

  public void awaitCommit(final long commitIndex) throws Exception {
    if (highestCommit >= commitIndex) {
      return;
    }

    final var commitAwaiter = new CommitAwaiter(commitIndex);
    commitAwaiterRef.set(commitAwaiter);

    commitAwaiter.awaitCommit();
  }

  private RaftServer createServer(
      final MemberId memberId, final BiFunction<MemberId, Builder, Builder> configurator) {
    final TestRaftServerProtocol protocol = protocolFactory.newServerProtocol(memberId);
    final var storage = createStorage(memberId);

    final RaftServer.Builder defaults =
        RaftServer.builder(memberId)
            .withPartitionConfig(
                new RaftPartitionConfig()
                    .setElectionTimeout(Duration.ofSeconds(1))
                    .setHeartbeatInterval(Duration.ofMillis(100)))
            .withMembershipService(mock(ClusterMembershipService.class))
            .withProtocol(protocol)
            .withEntryValidator(entryValidator)
            .withStorage(storage);
    final RaftServer server = configurator.apply(memberId, defaults).build();

    servers.put(memberId.id(), server);
    return server;
  }

  private RaftStorage createStorage(final MemberId memberId) {
    return createStorage(memberId, Function.identity());
  }

  public void copySnapshotOffline(final String sourceNode, final String targetNode) {
    final var snapshotOnNode = getSnapshotOnNode(sourceNode);
    final var targetSnapshotStore = new TestSnapshotStore(getOrCreatePersistedSnapshot(sourceNode));
    final var receivedSnapshot = targetSnapshotStore.newReceivedSnapshot(snapshotOnNode.getId());
    for (final var reader = snapshotOnNode.newChunkReader(); reader.hasNext(); ) {
      receivedSnapshot.apply(reader.next());
    }
    receivedSnapshot.persist();
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
            .withSnapshotStore(
                snapshotStores.compute(
                    memberId.id(),
                    (k, v) -> new TestSnapshotStore(getOrCreatePersistedSnapshot(memberId.id()))));
    return configurator.apply(defaults).build();
  }

  private File getMemberDirectory(final Path directory, final String s) {
    return new File(directory.toFile(), s);
  }

  public Optional<RaftServer> getLeader() {
    return servers.values().stream().filter(s -> s.getRole() == Role.LEADER).findFirst();
  }

  public Optional<RaftServer> getFollower() {
    return servers.values().stream().filter(s -> s.getRole() == Role.FOLLOWER).findFirst();
  }

  public long appendEntries(final int count) throws Exception {
    final var leader = getLeader().orElseThrow();

    for (int i = 0; i < count - 1; i++) {
      appendEntry();
    }

    return appendEntry();
  }

  public long appendEntry() throws Exception {
    final var leader = getLeader().orElseThrow();

    return appendEntry(leader, 1024);
  }

  private long appendEntry(final RaftServer leader, final int entrySize) throws Exception {
    final var raftRole = leader.getContext().getRaftRole();
    if (raftRole instanceof LeaderRole) {
      final var testAppendListener = appendEntry(entrySize, (LeaderRole) raftRole);
      return testAppendListener.awaitCommit();
    }
    throw new IllegalArgumentException(
        "Expected to append entry on leader, "
            + leader.getContext().getName()
            + " was not the leader!");
  }

  private void appendEntryAsync(final RaftServer leader, final int entrySize) {
    final var raftRole = leader.getContext().getRaftRole();

    if (raftRole instanceof LeaderRole) {
      appendEntry(entrySize, (LeaderRole) raftRole);
      return;
    }

    throw new IllegalArgumentException(
        "Expected to append entry on leader, "
            + leader.getContext().getName()
            + " was not the leader!");
  }

  private TestAppendListener appendEntry(final int entrySize, final LeaderRole leaderRole) {
    final var appendListener = new TestAppendListener();
    position += 1;
    final var bytes = new byte[entrySize];
    random.nextBytes(bytes);
    leaderRole.appendEntry(position, position + 10, ByteBuffer.wrap(bytes), appendListener);
    position += 10;
    return appendListener;
  }

  @Override
  public String toString() {
    return "RaftRule with " + nodeCount + " nodes.";
  }

  public void triggerDataLossOnNode(final String node) throws IOException {
    final var member =
        members.stream()
            .map(RaftMember::memberId)
            .map(AbstractIdentifier::id)
            .filter(id -> id.equals(node))
            .findAny()
            .orElseThrow();

    final var memberDirectory = getMemberDirectory(directory, member);

    boolean deletedMemberDirectory = false;
    while (!deletedMemberDirectory) {
      try {
        FileUtil.deleteFolderIfExists(memberDirectory.toPath());
        deletedMemberDirectory = true;
      } catch (final DirectoryNotEmptyException e) {
        // Deleting the directory may fail when journal asynchronously creates the next segment. In
        // that case we can simply retry deleting the directory. Eventually we should be able to
        // delete during a timeframe where the journal is not concurrently creating the next
        // segment.
        FileUtil.deleteFolderIfExists(memberDirectory.toPath());
      }
    }

    // Clear in memory snapshots
    snapshots.remove(node);
  }

  public PersistedSnapshotStore getPersistedSnapshotStore(final String followerB) {
    return servers.get(followerB).getContext().getPersistedSnapshotStore();
  }

  public void addCommitListener(final RaftCommitListener raftCommitListener) {
    servers.forEach((id, raft) -> raft.getContext().addCommitListener(raftCommitListener));
  }

  public void addCommittedEntryListener(
      final RaftCommittedEntryListener raftCommittedEntryListener) {
    servers.forEach(
        (id, raft) -> raft.getContext().addCommittedEntryListener(raftCommittedEntryListener));
  }

  public void partition(final RaftServer follower) {
    protocolFactory.partition(follower.cluster().getLocalMember().memberId());
  }

  public void reconnect(final RaftServer follower) {
    protocolFactory.heal(follower.cluster().getLocalMember().memberId());
  }

  private static final class CommitAwaiter {

    private final long awaitedIndex;
    private final CountDownLatch latch = new CountDownLatch(1);

    public CommitAwaiter(final long index) {
      awaitedIndex = index;
    }

    public boolean reachedCommit(final long currentIndex) {
      if (awaitedIndex <= currentIndex) {
        latch.countDown();
        return true;
      }
      return false;
    }

    public void awaitCommit() throws Exception {
      latch.await(30, TimeUnit.SECONDS);
    }
  }

  private static final class TestAppendListener implements ZeebeLogAppender.AppendListener {

    private final CompletableFuture<Long> commitFuture = new CompletableFuture<>();

    @Override
    public void onWriteError(final Throwable error) {
      commitFuture.completeExceptionally(error);
    }

    @Override
    public void onCommit(final IndexedRaftLogEntry indexed) {
      commitFuture.complete(indexed.index());
    }

    @Override
    public void onCommitError(final IndexedRaftLogEntry indexed, final Throwable error) {
      commitFuture.completeExceptionally(error);
    }

    public long awaitCommit() throws Exception {
      return commitFuture.get(30, TimeUnit.SECONDS);
    }
  }

  private record CopiedRaftLogEntry(long index, long term, RaftEntry entry)
      implements IndexedRaftLogEntry {
    private static CopiedRaftLogEntry of(final IndexedRaftLogEntry entry) {
      final RaftEntry copiedEntry;

      if (entry.entry() instanceof SerializedApplicationEntry app) {
        copiedEntry =
            new SerializedApplicationEntry(
                app.lowestPosition(), app.highestPosition(), BufferUtil.cloneBuffer(app.data()));
      } else {
        copiedEntry = entry.entry();
      }

      return new CopiedRaftLogEntry(entry.index(), entry.term(), copiedEntry);
    }

    @Override
    public ApplicationEntry getApplicationEntry() {
      return (ApplicationEntry) entry;
    }

    @Override
    public PersistedRaftRecord getPersistedRaftRecord() {
      throw new UnsupportedOperationException();
    }
  }
}
