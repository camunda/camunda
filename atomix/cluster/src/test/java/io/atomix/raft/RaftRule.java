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
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Builder;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.partition.impl.RaftNamespaces;
import io.atomix.raft.primitive.TestMember;
import io.atomix.raft.protocol.TestRaftProtocolFactory;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.zeebe.ZeebeEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.atomix.storage.StorageLevel;
import io.atomix.storage.journal.Indexed;
import io.atomix.storage.journal.JournalReader.Mode;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.atomix.utils.concurrent.ThreadContext;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class RaftRule extends ExternalResource {

  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private volatile int nextId;
  private volatile List<RaftMember> members;
  private Map<String, Long> memberLog;
  private final List<RaftServer> servers = new ArrayList<>();
  private volatile TestRaftProtocolFactory protocolFactory;
  private volatile ThreadContext context;
  private Path directory;
  private final int nodeCount;
  private volatile long highestCommit;
  private final AtomicReference<CommitAwaiter> commitAwaiterRef = new AtomicReference<>();
  private long position;

  private RaftRule(final int nodeCount) {
    this.nodeCount = nodeCount;
  }

  public static RaftRule withBootstrappedNodes(final int nodeCount) {
    if (nodeCount < 1) {
      throw new IllegalArgumentException("Expected to have at least one node to configure.");
    }
    return new RaftRule(nodeCount);
  }

  public static RaftRule withoutNodes() {
    return new RaftRule(-1);
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
    nextId = 0;
    context = new SingleThreadContext("raft-test-messaging-%d");
    protocolFactory = new TestRaftProtocolFactory(context);

    if (nodeCount > 0) {
      createServers(nodeCount);
    }
  }

  @Override
  protected void after() {
    try {
      CompletableFuture.allOf(
              servers.stream()
                  .filter(RaftServer::isRunning)
                  .map(RaftServer::shutdown)
                  .toArray(CompletableFuture[]::new))
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
  private List<RaftServer> createServers(final int nodes) throws Exception {
    final List<RaftServer> servers = new ArrayList<>();

    for (int i = 0; i < nodes; i++) {
      members.add(nextMember(RaftMember.Type.ACTIVE));
    }

    final CountDownLatch latch = new CountDownLatch(nodes);

    for (int i = 0; i < nodes; i++) {
      final var raftMember = members.get(i);
      final RaftServer server = createServer(raftMember.memberId());
      server
          .bootstrap(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
          .thenAccept(this::addCommitListener)
          .thenRun(latch::countDown);
      servers.add(server);
    }

    latch.await(30, TimeUnit.SECONDS);

    return servers;
  }

  public void shutdownFollower() throws Exception {
    final var follower = getFollower().orElseThrow();
    shutdownServer(follower);
  }

  public RaftServer shutdownLeader() throws Exception {
    final var leader = getLeader().orElseThrow();
    shutdownServer(leader);
    return leader;
  }

  public void restartLeader() throws Exception {
    awaitNewLeader();
    final var leader = shutdownLeader();

    final RaftMember leaderMember = getRaftMember(leader.name());
    createServer(leaderMember.memberId())
        .join(getMemberIds())
        .thenAccept(this::addCommitListener)
        .get(30, TimeUnit.SECONDS);
  }

  private List<MemberId> getMemberIds() {
    return members.stream().map(RaftMember::memberId).collect(Collectors.toList());
  }

  public void shutdownServer(final String memberId) throws Exception {
    final var raftServer = getRaftServer(memberId);
    shutdownServer(raftServer);
  }

  private RaftServer getRaftServer(final String memberId) {
    return servers.stream()
        .filter(server -> server.name().equals(memberId))
        .findFirst()
        .orElseThrow();
  }

  public void shutdownServer(final RaftServer raftServer) throws Exception {
    raftServer.shutdown().get(30, TimeUnit.SECONDS);
    servers.remove(raftServer);
    memberLog.remove(raftServer.name());
  }

  public CompletableFuture<RaftServer> startServer(final String memberId) {
    final RaftMember raftMember = getRaftMember(memberId);
    final var server = createServer(raftMember.memberId());
    return server.join(getMemberIds());
  }

  private RaftMember getRaftMember(final String memberId) {
    return members.stream()
        .filter(member -> member.memberId().id().equals(memberId))
        .findFirst()
        .orElseThrow();
  }

  public CompletableFuture<Void> tryToCompactLogsOnServersExcept(
      final String memberId, final long index) {

    final var servers =
        this.servers.stream()
            .filter(server -> !server.name().equals(memberId))
            .collect(Collectors.toList());

    final List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (final RaftServer server : servers) {
      futures.add(tryToCompactLogOnServer(server, index));
    }

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
  }

  private CompletableFuture<Void> tryToCompactLogOnServer(
      final RaftServer raftServer, final long index) {
    raftServer.getContext().getServiceManager().setCompactableIndex(index);
    return raftServer.compact();
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
              public <T extends RaftLogEntry> void onCommit(final Indexed<T> entry) {
                final var currentIndex = entry.index();

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

  public Map<String, List<Indexed<?>>> getMemberLogs() {

    final Map<String, List<Indexed<?>>> memberLogs = new HashMap<>();

    for (final var server : servers) {
      if (server.isRunning()) {

        final var log = server.getContext().getLog();
        final List<Indexed<?>> entryList = new ArrayList<>();
        try (final var raftLogReader = log.openReader(1, Mode.ALL)) {

          while (raftLogReader.hasNext()) {
            final var indexedEntry = raftLogReader.next();
            entryList.add(indexedEntry);
          }
        }

        memberLogs.put(server.name(), entryList);
      }
    }

    return memberLogs;
  }

  public void awaitSameLogSizeOnAllNodes() {
    waitUntil(
        () -> memberLog.values().stream().distinct().count() == 1, () -> memberLog.toString());
  }

  private void waitUntil(final BooleanSupplier condition, final Supplier<String> errorMessage) {
    waitUntil(condition, 100, errorMessage);
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

  /** Creates a Raft server. */
  private RaftServer createServer(final MemberId memberId) {
    return createServer(memberId, b -> b.withStorage(createStorage(memberId)));
  }

  private RaftServer createServer(
      final MemberId memberId, final UnaryOperator<Builder> configurator) {
    final TestRaftServerProtocol protocol = protocolFactory.newServerProtocol(memberId);
    final RaftServer.Builder defaults =
        RaftServer.builder(memberId)
            .withMembershipService(mock(ClusterMembershipService.class))
            .withProtocol(protocol);
    final RaftServer server = configurator.apply(defaults).build();

    servers.add(server);
    return server;
  }

  private RaftStorage createStorage(final MemberId memberId) {
    return createStorage(memberId, Function.identity());
  }

  private RaftStorage createStorage(
      final MemberId memberId,
      final Function<RaftStorage.Builder, RaftStorage.Builder> configurator) {
    final RaftStorage.Builder defaults =
        RaftStorage.builder()
            .withStorageLevel(StorageLevel.DISK)
            .withDirectory(new File(directory.toFile(), memberId.toString()))
            .withMaxEntriesPerSegment(10)
            .withMaxSegmentSize(1024 * 10)
            .withNamespace(RaftNamespaces.RAFT_STORAGE);
    return configurator.apply(defaults).build();
  }

  private Optional<RaftServer> getLeader() {
    return servers.stream().filter(s -> s.getRole() == Role.LEADER).findFirst();
  }

  private Optional<RaftServer> getFollower() {
    return servers.stream().filter(s -> s.getRole() == Role.FOLLOWER).findFirst();
  }

  public long appendEntries(final int count) throws Exception {
    final var leader = getLeader().orElseThrow();

    for (int i = 0; i < count - 1; i++) {
      appendEntryAsync(leader, 1024);
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
    leaderRole.appendEntry(
        position,
        position + 10,
        ByteBuffer.wrap(RandomStringUtils.random(entrySize).getBytes()),
        appendListener);
    position += 10;
    return appendListener;
  }

  @Override
  public String toString() {
    return "RaftRule with " + nodeCount + " nodes.";
  }

  private static final class CommitAwaiter {

    private final long awaitedIndex;
    private final CountDownLatch latch = new CountDownLatch(1);

    public CommitAwaiter(final long index) {
      this.awaitedIndex = index;
    }

    public boolean reachedCommit(final long currentIndex) {
      if (this.awaitedIndex <= currentIndex) {
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
    public void onWrite(final Indexed<ZeebeEntry> indexed) {}

    @Override
    public void onWriteError(final Throwable error) {
      fail("Unexpected write error: " + error.getMessage());
    }

    @Override
    public void onCommit(final Indexed<ZeebeEntry> indexed) {
      commitFuture.complete(indexed.index());
    }

    @Override
    public void onCommitError(final Indexed<ZeebeEntry> indexed, final Throwable error) {
      fail("Unexpected write error: " + error.getMessage());
    }

    public long awaitCommit() throws Exception {
      return commitFuture.get(30, TimeUnit.SECONDS);
    }
  }
}
