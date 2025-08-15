/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.google.common.collect.Maps;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Builder;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.primitive.TestMember;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.TestRaftProtocolFactory;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.raft.snapshot.TestSnapshotStore;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.InitialEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.atomix.utils.concurrent.ThreadContext;
import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthReport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.jodah.concurrentunit.ConcurrentTestCase;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.AutoClose;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Raft test. */
public class RaftTest extends ConcurrentTestCase {
  private static final Logger LOGGER = LoggerFactory.getLogger(RaftTest.class);

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private volatile int nextId;
  private volatile List<RaftMember> members;
  private volatile List<RaftServer> servers = new ArrayList<>();
  private volatile TestRaftProtocolFactory protocolFactory;
  private volatile ThreadContext context;
  private volatile long position = 0;
  private Path directory;
  private final Map<MemberId, TestRaftServerProtocol> serverProtocols = Maps.newConcurrentMap();
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Before
  @After
  public void clearTests() throws Exception {
    servers.forEach(
        s -> {
          try {
            if (s.isRunning()) {
              s.shutdown().get(10, TimeUnit.SECONDS);
            }
          } catch (final Exception e) {
            // its fine..
          }
        });

    directory = temporaryFolder.newFolder().toPath();

    if (context != null) {
      context.close();
    }

    members = new ArrayList<>();
    nextId = 0;
    servers = new ArrayList<>();
    context = new SingleThreadContext("raft-test-messaging-%d");
    protocolFactory = new TestRaftProtocolFactory();
  }

  /** Creates a set of Raft servers. */
  private List<RaftServer> createServers(final int nodes) throws Throwable {
    final List<RaftServer> servers = new ArrayList<>();

    for (int i = 0; i < nodes; i++) {
      members.add(nextMember(RaftMember.Type.ACTIVE));
    }

    final CountDownLatch latch = new CountDownLatch(nodes);

    for (int i = 0; i < nodes; i++) {
      final RaftServer server = createServer(members.get(i).memberId());
      if (members.get(i).getType() == RaftMember.Type.ACTIVE) {
        server
            .bootstrap(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
            .thenRun(latch::countDown);
      }
      servers.add(server);
    }

    assertThat(latch.await(30L * nodes, TimeUnit.SECONDS)).isTrue();

    return servers;
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

  /** Creates a Raft server. */
  private RaftServer createServer(final MemberId memberId) {
    return createServer(memberId, b -> b.withStorage(createStorage(memberId)));
  }

  private RaftServer createServer(
      final MemberId memberId,
      final Function<RaftServer.Builder, RaftServer.Builder> configurator) {
    final TestRaftServerProtocol protocol = protocolFactory.newServerProtocol(memberId);
    final RaftServer.Builder defaults =
        RaftServer.builder(memberId)
            .withMembershipService(mock(ClusterMembershipService.class))
            .withProtocol(protocol)
            .withPartitionConfig(
                new RaftPartitionConfig()
                    .setElectionTimeout(Duration.ofSeconds(1))
                    .setHeartbeatInterval(Duration.ofMillis(100)))
            .withMeterRegistry(meterRegistry);

    final RaftServer server = configurator.apply(defaults).build();

    serverProtocols.put(memberId, protocol);
    servers.add(server);
    return server;
  }

  private RaftStorage createStorage(final MemberId memberId) {
    return createStorage(memberId, Function.identity());
  }

  private RaftStorage createStorage(
      final MemberId memberId,
      final Function<RaftStorage.Builder, RaftStorage.Builder> configurator) {
    final var directory = new File(this.directory.toFile(), memberId.toString());
    final RaftStorage.Builder defaults =
        RaftStorage.builder(meterRegistry)
            .withDirectory(directory)
            .withSnapshotStore(new TestSnapshotStore(new AtomicReference<>()))
            .withMaxSegmentSize(1024 * 10);
    return configurator.apply(defaults).build();
  }

  /** Tests transferring leadership. */
  @Test
  @Ignore
  public void testTransferLeadership() throws Throwable {
    final List<RaftServer> servers = createServers(3);
    final var leader = getLeader(servers).orElseThrow();
    awaitAppendEntries(leader, 1000);
    final RaftServer follower = servers.stream().filter(RaftServer::isFollower).findFirst().get();
    follower.promote().thenRun(this::resume);
    await(15000, 1001);
    assertThat(follower.isLeader()).isTrue();
  }

  /** Tests demoting the leader. */
  @Test
  public void testDemoteLeader() throws Throwable {
    final List<RaftServer> servers = createServers(3);

    final var leader = servers.stream().filter(RaftServer::isLeader).findFirst().orElseThrow();
    final var leaderId = leader.cluster().getLocalMember().memberId();

    final var follower = servers.stream().filter(RaftServer::isFollower).findFirst().orElseThrow();
    final var followerId = follower.cluster().getLocalMember().memberId();

    follower
        .cluster()
        .getMember(leaderId)
        .addTypeChangeListener(
            t -> {
              threadAssertEquals(t, RaftMember.Type.PASSIVE);
              resume();
              LOGGER.debug("Leader {} changed to passive on {}", leaderId, followerId);
            });
    leader
        .cluster()
        .getLocalMember()
        .demote(RaftMember.Type.PASSIVE)
        .whenComplete(
            (ignored, error) -> {
              threadAssertNull(error);
              resume();
              LOGGER.debug("Leader {} demoted to passive", leaderId);
            });
    await(15000, 2);
  }

  /** Tests submitting a command. */
  @Test
  public void testTwoOfThreeNodeSubmitCommand() throws Throwable {
    testSubmitCommand(2, 3);
  }

  /** Tests submitting a command to a partial cluster. */
  private void testSubmitCommand(final int live, final int total) throws Throwable {
    final var leader = getLeader(createServers(live, total));

    appendEntry(leader.orElseThrow());
  }

  /** Creates a set of Raft servers. */
  private List<RaftServer> createServers(final int live, final int total) throws Throwable {
    final List<RaftServer> servers = new ArrayList<>();

    for (int i = 0; i < total; i++) {
      members.add(nextMember(RaftMember.Type.ACTIVE));
    }

    for (int i = 0; i < live; i++) {
      final RaftServer server = createServer(members.get(i).memberId());
      if (members.get(i).getType() == RaftMember.Type.ACTIVE) {
        server
            .bootstrap(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
            .thenRun(this::resume);
      }
      servers.add(server);
    }

    await(30000L * live, live);

    return servers;
  }

  /** Tests submitting a command. */
  @Test
  public void testThreeOfFourNodeSubmitCommand() throws Throwable {
    testSubmitCommand(3, 4);
  }

  /** Tests submitting a command. */
  @Test
  public void testThreeOfFiveNodeSubmitCommand() throws Throwable {
    testSubmitCommand(3, 5);
  }

  @Test
  public void testThreeNodesSequentiallyStart() throws Throwable {
    // given
    for (int i = 0; i < 3; i++) {
      members.add(nextMember(RaftMember.Type.ACTIVE));
    }

    // wait between bootstraps to produce more realistic environment
    for (int i = 0; i < 3; i++) {
      final RaftServer server = createServer(members.get(i).memberId());
      server
          .bootstrap(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
          .thenRun(this::resume);
      Thread.sleep(500);
    }

    // then expect that all come up in time
    await(2000 * 3, 3);
  }

  @Test
  public void testThreeNodeManyEventsDoNotMissHeartbeats() throws Throwable {
    // given
    createServers(3);
    final var leader = getLeader(servers).orElseThrow();

    appendEntry(leader);

    final var roleMetrics = leader.getContext().getRaftRoleMetrics();
    final double startMissedHeartBeats = roleMetrics.getHeartbeatMissCount();

    // when
    appendEntries(leader, 1000);

    // then
    final double missedHeartBeats = roleMetrics.getHeartbeatMissCount();
    assertThat(0.0, is(missedHeartBeats - startMissedHeartBeats));
  }

  @Test
  public void testRoleChangeNotificationAfterInitialEntryOnLeader() throws Throwable {
    // given
    final List<RaftServer> servers = createServers(3);
    final RaftServer previousLeader = getLeader(servers).get();
    final long previousLeaderTerm = previousLeader.getTerm();

    final CountDownLatch transitionCompleted = new CountDownLatch(1);

    servers.forEach(
        server ->
            server.addRoleChangeListener(
                (role, term) -> {
                  if (term > previousLeaderTerm) {
                    assertLastReadInitialEntry(role, term, server, transitionCompleted);
                  }
                }));

    // when
    previousLeader.stepDown();

    // then
    assertThat(transitionCompleted.await(1000, TimeUnit.SECONDS)).isTrue();
  }

  private Optional<RaftServer> getLeader(final List<RaftServer> servers) {
    return servers.stream().filter(s -> s.getRole() == Role.LEADER).findFirst();
  }

  private List<RaftServer> getFollowers(final List<RaftServer> servers) {
    return servers.stream().filter(s -> s.getRole() == Role.FOLLOWER).collect(Collectors.toList());
  }

  private void assertLastReadInitialEntry(
      final Role role,
      final long term,
      final RaftServer server,
      final CountDownLatch transitionCompleted) {
    if (role == Role.LEADER) {
      final RaftLog raftLog = server.getContext().getLog();
      final RaftLogReader raftLogReader = raftLog.openCommittedReader();
      raftLogReader.seek(raftLog.getLastIndex());
      final IndexedRaftLogEntry entry = raftLogReader.next();

      assertThat(entry.entry()).isInstanceOf(InitialEntry.class);
      assertThat(entry.term()).isEqualTo(term);
      transitionCompleted.countDown();
    }
  }

  @Test
  public void testNotifyOnFailure() throws Throwable {
    // given
    final List<RaftServer> servers = createServers(1);
    final RaftServer server = servers.get(0);
    final CountDownLatch firstLatch = new CountDownLatch(1);
    final CountDownLatch secondLatch = new CountDownLatch(1);

    server.addFailureListener(new LatchFailureListener(firstLatch));
    server.addFailureListener(new LatchFailureListener(secondLatch));

    // when
    // inject failures
    server
        .getContext()
        .getThreadContext()
        .execute(
            () -> {
              throw new RuntimeException("injected failure");
            });

    // then
    assertThat(firstLatch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(secondLatch.await(1, TimeUnit.SECONDS)).isTrue();

    assertThat(server.getRole()).isEqualTo(Role.INACTIVE);
  }

  @Test
  public void shouldDetectCorruptionOnStart() throws Throwable {
    // given
    final RaftServer leader = createServers(1).get(0);
    final File directory = leader.getContext().getStorage().directory();
    appendEntry(leader);

    final Optional<File> optLog =
        Arrays.stream(directory.listFiles()).filter(f -> f.getName().endsWith(".log")).findFirst();
    assertThat(optLog).isPresent();
    final File log = optLog.get();

    // when
    leader.shutdown().join();
    // TODO: write tests with controllable journal - how to corrupt it is implementation detail
    Files.writeString(log.toPath(), "i am become corrupt, destroyer of worlds");

    // then
    final MemberId memberId = members.get(0).memberId();
    assertThatThrownBy(() -> recreateServer(leader, memberId))
        .isInstanceOf(CorruptedJournalException.class);
  }

  private RaftServer recreateServer(final RaftServer server, final MemberId memberId) {
    final Function<RaftStorage.Builder, RaftStorage.Builder> storageConfig =
        c -> c.withDirectory(server.getContext().getStorage().directory());
    final RaftStorage storage = createStorage(memberId, storageConfig);
    final Function<Builder, Builder> serverConfig = b -> b.withStorage(storage);

    return createServer(memberId, serverConfig);
  }

  @Test
  public void shouldTriggerHeartbeatTimeouts() throws Throwable {
    // given
    final List<RaftServer> servers = createServers(3);
    Awaitility.await("A leader exists")
        .until(() -> servers.stream().filter(server -> server.isLeader()).findAny().isPresent());

    final List<RaftServer> followers = getFollowers(servers);
    final RaftServer follower = followers.get(0);
    final MemberId followerId = follower.getContext().getCluster().getLocalMember().memberId();
    final var pollCount = new LongAdder();

    // when
    final TestRaftServerProtocol followerServer = serverProtocols.get(followerId);
    followerServer.interceptRequest(
        PollRequest.class,
        r -> {
          pollCount.increment();
          return CompletableFuture.failedFuture(new ConnectException());
        });
    // Disconnect one way so that follower do not receive any messages from the leader.
    protocolFactory.blockMessagesTo(followerId);

    // then
    // With priority election enabled the lowest priority node can wait upto 3 * electionTimeout
    // before triggering election.
    final var timeout = follower.getContext().getElectionTimeout().multipliedBy(4).toMillis();

    // should send poll requests to 2 nodes
    Awaitility.await()
        .timeout(Duration.ofMillis(timeout))
        .untilAdder(pollCount, greaterThanOrEqualTo(2L));
  }

  @Test
  public void shouldReSendPollRequestOnTimeouts() throws Throwable {
    final List<RaftServer> servers = createServers(3);
    final List<RaftServer> followers = getFollowers(servers);
    final MemberId followerId =
        followers.get(0).getContext().getCluster().getLocalMember().memberId();
    final var pollCount = new LongAdder();

    // when
    final TestRaftServerProtocol followerServer = serverProtocols.get(followerId);
    // Disconnect one way so that follower do not receive any messages from the leader.
    protocolFactory.blockMessagesTo(followerId);
    followerServer.interceptRequest(
        PollRequest.class,
        r -> {
          pollCount.increment();
          return CompletableFuture.failedFuture(new TimeoutException());
        });
    Awaitility.await().timeout(Duration.ofSeconds(5)).untilAdder(pollCount, greaterThan(2L));
    pollCount.reset();

    // then
    // no response for previous poll requests, so send them again
    Awaitility.await().timeout(Duration.ofSeconds(5)).untilAdder(pollCount, greaterThan(2L));
  }

  @Test
  public void shouldNotifyListenerWhenNoTransitionIsOngoing() throws Throwable {
    // given
    final var listenerLatch = new CountDownLatch(1);
    final AtomicReference<Role> roleWithinListener = new AtomicReference<>(null);
    final AtomicLong termWithinListener = new AtomicLong(-1L);

    final var server = createServers(1).get(0);

    // expect
    assertThat(server.isLeader()).isTrue();

    // when
    server.addRoleChangeListener(
        (role, term) -> {
          roleWithinListener.set(role);
          termWithinListener.set(term);
          listenerLatch.countDown();
        });

    // then
    assertThat(listenerLatch.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(roleWithinListener.get()).isEqualTo(server.getRole());
    assertThat(termWithinListener.get()).isEqualTo(server.getTerm());
  }

  private void appendEntries(final RaftServer leader, final int count) {
    for (int i = 0; i < count; i++) {
      appendEntryAsync(leader, 1024);
    }
  }

  private long appendEntry(final RaftServer leader) throws Exception {
    return appendEntry(leader, 1024);
  }

  private long appendEntry(final RaftServer leader, final int entrySize) throws Exception {
    final var raftRole = leader.getContext().getRaftRole();
    if (raftRole instanceof LeaderRole) {
      final var appendListener = appendEntry(entrySize, (LeaderRole) raftRole);
      return appendListener.awaitCommit();
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

  private void awaitAppendEntries(final RaftServer newLeader, final int i) throws Exception {
    // this call is async
    appendEntries(newLeader, i - 1);

    // this awaits the last append
    appendEntry(newLeader);
  }

  private static final class TestAppendListener implements ZeebeLogAppender.AppendListener {

    private final CompletableFuture<Long> commitFuture = new CompletableFuture<>();

    @Override
    public void onWriteError(final Throwable error) {
      fail("Unexpected write error: " + error.getMessage());
    }

    @Override
    public void onCommit(final long index, final long highestPosition) {
      commitFuture.complete(index);
    }

    @Override
    public void onCommitError(final long index, final Throwable error) {
      fail("Unexpected write error: " + error.getMessage());
    }

    public long awaitCommit() throws Exception {
      return commitFuture.get(30, TimeUnit.SECONDS);
    }
  }

  private static class LatchFailureListener implements FailureListener {

    private final CountDownLatch latch;

    LatchFailureListener(final CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    public void onFailure(final HealthReport report) {
      latch.countDown();
    }

    @Override
    public void onRecovered(final HealthReport report) {}

    @Override
    public void onUnrecoverableFailure(final HealthReport report) {}
  }
}
