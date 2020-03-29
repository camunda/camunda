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

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.PrimitiveInfo;
import io.atomix.primitive.PrimitiveRegistry;
import io.atomix.primitive.PrimitiveState;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.operation.OperationType;
import io.atomix.primitive.operation.PrimitiveOperation;
import io.atomix.primitive.operation.impl.DefaultOperationId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.proxy.ProxyClient;
import io.atomix.primitive.proxy.impl.DefaultProxyClient;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.primitive.session.SessionClient;
import io.atomix.primitive.session.SessionMetadata;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.RaftClusterEvent;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.metrics.RaftRoleMetrics;
import io.atomix.raft.primitive.FakeStateMachine;
import io.atomix.raft.primitive.TestMember;
import io.atomix.raft.primitive.TestPrimitive;
import io.atomix.raft.primitive.TestPrimitiveImpl;
import io.atomix.raft.primitive.TestPrimitiveService;
import io.atomix.raft.primitive.TestPrimitiveType;
import io.atomix.raft.protocol.TestRaftProtocolFactory;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.CloseSessionEntry;
import io.atomix.raft.storage.log.entry.CommandEntry;
import io.atomix.raft.storage.log.entry.ConfigurationEntry;
import io.atomix.raft.storage.log.entry.InitializeEntry;
import io.atomix.raft.storage.log.entry.KeepAliveEntry;
import io.atomix.raft.storage.log.entry.MetadataEntry;
import io.atomix.raft.storage.log.entry.OpenSessionEntry;
import io.atomix.raft.storage.log.entry.QueryEntry;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.storage.snapshot.Snapshot;
import io.atomix.raft.storage.snapshot.SnapshotChunk;
import io.atomix.raft.storage.snapshot.SnapshotChunkReader;
import io.atomix.raft.storage.snapshot.SnapshotStore;
import io.atomix.raft.storage.system.Configuration;
import io.atomix.raft.utils.LoadMonitor;
import io.atomix.storage.StorageLevel;
import io.atomix.storage.journal.JournalReader.Mode;
import io.atomix.storage.statistics.StorageStatistics;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.serializer.Namespace;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.jodah.concurrentunit.ConcurrentTestCase;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;

/** Raft test. */
public class RaftTest extends ConcurrentTestCase {

  public static AtomicLong snapshots = new AtomicLong(0);
  private static final Namespace NAMESPACE =
      Namespace.builder()
          .register(CloseSessionEntry.class)
          .register(CommandEntry.class)
          .register(ConfigurationEntry.class)
          .register(InitializeEntry.class)
          .register(KeepAliveEntry.class)
          .register(MetadataEntry.class)
          .register(OpenSessionEntry.class)
          .register(QueryEntry.class)
          .register(PrimitiveOperation.class)
          .register(DefaultOperationId.class)
          .register(OperationType.class)
          .register(ReadConsistency.class)
          .register(ArrayList.class)
          .register(HashSet.class)
          .register(DefaultRaftMember.class)
          .register(MemberId.class)
          .register(RaftMember.Type.class)
          .register(Instant.class)
          .register(Configuration.class)
          .register(byte[].class)
          .register(long[].class)
          .build();

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  protected volatile int nextId;
  protected volatile List<RaftMember> members;
  protected volatile List<RaftClient> clients = new ArrayList<>();
  protected volatile List<RaftServer> servers = new ArrayList<>();
  protected volatile TestRaftProtocolFactory protocolFactory;
  protected volatile ThreadContext context;
  private Path directory;

  @Before
  @After
  public void clearTests() throws Exception {
    snapshots = new AtomicLong(0);
    clients.forEach(
        c -> {
          try {
            c.close().get(10, TimeUnit.SECONDS);
          } catch (final Exception e) {
            // its fine..
          }
        });

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
    clients = new ArrayList<>();
    servers = new ArrayList<>();
    context = new SingleThreadContext("raft-test-messaging-%d");
    protocolFactory = new TestRaftProtocolFactory(context);
  }

  /** Tests getting session metadata. */
  @Test
  public void testSessionMetadata() throws Throwable {
    createServers(3);
    final RaftClient client = createClient();
    createPrimitive(client).write("Hello world!").join();
    createPrimitive(client).write("Hello world again!").join();
    assertNotNull(client.metadata().getLeader());
    assertNotNull(client.metadata().getServers());
    Set<SessionMetadata> typeSessions =
        client.metadata().getSessions(TestPrimitiveType.INSTANCE).join();
    assertEquals(2, typeSessions.size());
    typeSessions = client.metadata().getSessions(TestPrimitiveType.INSTANCE).join();
    assertEquals(2, typeSessions.size());
    Set<SessionMetadata> serviceSessions =
        client.metadata().getSessions(TestPrimitiveType.INSTANCE, "raft-test").join();
    assertEquals(2, serviceSessions.size());
    serviceSessions = client.metadata().getSessions(TestPrimitiveType.INSTANCE, "raft-test").join();
    assertEquals(2, serviceSessions.size());
  }

  /** Creates a set of Raft servers. */
  private List<RaftServer> createServers(final int nodes) throws Throwable {
    final List<RaftServer> servers = new ArrayList<>();

    for (int i = 0; i < nodes; i++) {
      members.add(nextMember(RaftMember.Type.ACTIVE));
    }

    for (int i = 0; i < nodes; i++) {
      final RaftServer server = createServer(members.get(i).memberId());
      if (members.get(i).getType() == RaftMember.Type.ACTIVE) {
        server
            .bootstrap(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
            .thenRun(this::resume);
      } else {
        server
            .listen(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
            .thenRun(this::resume);
      }
      servers.add(server);
    }

    await(30000 * nodes, nodes);

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
    final RaftServer.Builder defaults =
        RaftServer.builder(memberId)
            .withMembershipService(mock(ClusterMembershipService.class))
            .withProtocol(protocolFactory.newServerProtocol(memberId));
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
            .withNamespace(NAMESPACE);
    return configurator.apply(defaults).build();
  }

  /** Creates a Raft client. */
  private RaftClient createClient() throws Throwable {
    return createClient(members);
  }

  private RaftClient createClient(final List<RaftMember> members) throws Throwable {
    final MemberId memberId = nextNodeId();
    final List<MemberId> memberIds =
        members.stream().map(RaftMember::memberId).collect(Collectors.toList());
    final RaftClient client =
        RaftClient.builder()
            .withMemberId(memberId)
            .withPartitionId(PartitionId.from("test", 1))
            .withProtocol(protocolFactory.newClientProtocol(memberId))
            .build();
    client.connect(memberIds).thenRun(this::resume);
    await(30000);
    clients.add(client);
    return client;
  }

  /** Creates a new primitive instance. */
  private TestPrimitive createPrimitive(final RaftClient client) throws Exception {
    return createPrimitive(client, ReadConsistency.LINEARIZABLE);
  }

  /** Creates a new primitive instance. */
  private TestPrimitive createPrimitive(final RaftClient client, final ReadConsistency consistency)
      throws Exception {
    final SessionClient partition = createSession(client, consistency);
    final ProxyClient<TestPrimitiveService> proxy =
        new DefaultProxyClient<>(
            "test",
            TestPrimitiveType.INSTANCE,
            MultiRaftProtocol.builder().build(),
            TestPrimitiveService.class,
            Collections.singletonList(partition),
            (key, partitions) -> partitions.get(0));
    final PrimitiveRegistry registry = mock(PrimitiveRegistry.class);
    when(registry.createPrimitive(any(String.class), any(PrimitiveType.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new PrimitiveInfo("raft-test", TestPrimitiveType.INSTANCE)));
    when(registry.deletePrimitive(any(String.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    return new TestPrimitiveImpl(proxy, registry);
  }

  /** Creates a test session. */
  private SessionClient createSession(final RaftClient client, final ReadConsistency consistency)
      throws Exception {
    return client
        .sessionBuilder("raft-test", TestPrimitiveType.INSTANCE, new ServiceConfig())
        .withReadConsistency(consistency)
        .withMinTimeout(Duration.ofMillis(250))
        .withMaxTimeout(Duration.ofSeconds(5))
        .build()
        .connect()
        .get(10, TimeUnit.SECONDS);
  }

  /** Tests starting several members individually. */
  @Test
  public void testSingleMemberStart() throws Throwable {
    final RaftServer server = createServers(1).get(0);
    server.bootstrap().thenRun(this::resume);
    await(10000);
    final RaftServer joiner1 = createServer(nextNodeId());
    joiner1.join(server.cluster().getMember().memberId()).thenRun(this::resume);
    await(10000);
    final RaftServer joiner2 = createServer(nextNodeId());
    joiner2.join(server.cluster().getMember().memberId()).thenRun(this::resume);
    await(10000);
  }

  /** Tests joining a server after many entries have been committed. */
  @Test
  public void testActiveJoinLate() throws Throwable {
    testServerJoinLate(RaftMember.Type.ACTIVE, RaftServer.Role.FOLLOWER);
  }

  /** Tests joining a server after many entries have been committed. */
  @Test
  public void testPassiveJoinLate() throws Throwable {
    testServerJoinLate(RaftMember.Type.PASSIVE, RaftServer.Role.PASSIVE);
  }

  /** Tests joining a server after many entries have been committed. */
  private void testServerJoinLate(final RaftMember.Type type, final RaftServer.Role role)
      throws Throwable {
    createServers(3);
    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    submit(primitive, 100);
    await(15000, 100);
    final RaftServer joiner = createServer(nextNodeId());
    joiner.addRoleChangeListener(
        (s, t) -> {
          if (s == role) {
            resume();
          }
        });
    if (type == RaftMember.Type.ACTIVE) {
      joiner
          .join(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
          .thenRun(this::resume);
    } else {
      joiner
          .listen(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
          .thenRun(this::resume);
    }
    await(15000, 2);
    submit(primitive, 10);
    await(15000, 10);
    Thread.sleep(5000);
  }

  /** Tests transferring leadership. */
  @Test
  @Ignore
  public void testTransferLeadership() throws Throwable {
    final List<RaftServer> servers = createServers(3);
    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    submit(primitive, 1000);
    final RaftServer follower = servers.stream().filter(RaftServer::isFollower).findFirst().get();
    follower.promote().thenRun(this::resume);
    await(15000, 1001);
    assertTrue(follower.isLeader());
  }

  /** Submits a bunch of commands recursively. */
  private void submit(final TestPrimitive primitive, final int total) {
    for (int i = 0; i < total; i++) {
      primitive
          .write("Hello world!")
          .whenComplete(
              (result, error) -> {
                threadAssertNull(error);
                resume();
              });
    }
  }

  /** Tests joining a server to an existing cluster. */
  @Test
  public void testCrashRecover() throws Throwable {
    final List<RaftServer> servers = createServers(3);
    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    submit(primitive, 100);
    await(30000, 100);
    servers.get(0).shutdown().get(10, TimeUnit.SECONDS);
    final RaftServer server = createServer(members.get(0).memberId());
    server
        .join(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
        .thenRun(this::resume);
    await(30000);
    submit(primitive, 100);
    await(30000, 100);
  }

  /** Tests leaving a sever from a cluster. */
  @Test
  public void testServerLeave() throws Throwable {
    final List<RaftServer> servers = createServers(3);
    final RaftServer server = servers.get(0);
    server.leave().thenRun(this::resume);
    await(30000);
  }

  /** Tests leaving the leader from a cluster. */
  @Test
  public void testLeaderLeave() throws Throwable {
    final List<RaftServer> servers = createServers(3);
    final RaftServer server =
        servers.stream().filter(s -> s.getRole() == RaftServer.Role.LEADER).findFirst().get();
    server.leave().thenRun(this::resume);
    await(30000);
  }

  /** Tests keeping a client session alive. */
  @Test
  public void testClientKeepAlive() throws Throwable {
    createServers(3);
    final RaftClient client = createClient();
    final SessionClient session = createSession(client);
    Thread.sleep(Duration.ofSeconds(10).toMillis());
    threadAssertTrue(session.getState() == PrimitiveState.CONNECTED);
  }

  /** Creates a test session. */
  private SessionClient createSession(final RaftClient client) throws Exception {
    return createSession(client, ReadConsistency.LINEARIZABLE);
  }

  /** Tests an active member joining the cluster. */
  @Test
  public void testActiveJoin() throws Throwable {
    testServerJoin(RaftMember.Type.ACTIVE);
  }

  /** Tests a passive member joining the cluster. */
  @Test
  public void testPassiveJoin() throws Throwable {
    testServerJoin(RaftMember.Type.PASSIVE);
  }

  /** Tests a server joining the cluster. */
  private void testServerJoin(final RaftMember.Type type) throws Throwable {
    createServers(3);
    final RaftServer server = createServer(nextNodeId());
    if (type == RaftMember.Type.ACTIVE) {
      server
          .join(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
          .thenRun(this::resume);
    } else {
      server
          .listen(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
          .thenRun(this::resume);
    }
    await(15000);
  }

  /** Tests joining and leaving the cluster, resizing the quorum. */
  @Test
  public void testResize() throws Throwable {
    final RaftServer server = createServers(1).get(0);
    final RaftServer joiner = createServer(nextNodeId());
    joiner
        .join(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
        .thenRun(this::resume);
    await(15000);
    server.leave().thenRun(this::resume);
    await(15000);
    joiner.leave().thenRun(this::resume);
  }

  /** Tests an active member join event. */
  @Test
  public void testActiveJoinEvent() throws Throwable {
    testJoinEvent(RaftMember.Type.ACTIVE);
  }

  /** Tests a passive member join event. */
  @Test
  public void testPassiveJoinEvent() throws Throwable {
    testJoinEvent(RaftMember.Type.PASSIVE);
  }

  /** Tests a member join event. */
  private void testJoinEvent(final RaftMember.Type type) throws Throwable {
    final List<RaftServer> servers = createServers(3);

    final RaftMember member = nextMember(type);

    final RaftServer server = servers.get(0);
    server
        .cluster()
        .addListener(
            event -> {
              if (event.type() == RaftClusterEvent.Type.JOIN) {
                threadAssertEquals(event.subject().memberId(), member.memberId());
                resume();
              }
            });

    final RaftServer joiner = createServer(member.memberId());
    if (type == RaftMember.Type.ACTIVE) {
      joiner
          .join(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
          .thenRun(this::resume);
    } else {
      joiner
          .listen(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
          .thenRun(this::resume);
    }
    await(15000, 2);
  }

  /** Tests demoting the leader. */
  @Test
  public void testDemoteLeader() throws Throwable {
    final List<RaftServer> servers = createServers(3);

    final RaftServer leader =
        servers.stream()
            .filter(s -> s.cluster().getMember().equals(s.cluster().getLeader()))
            .findFirst()
            .get();

    final RaftServer follower =
        servers.stream()
            .filter(s -> !s.cluster().getMember().equals(s.cluster().getLeader()))
            .findFirst()
            .get();

    follower
        .cluster()
        .getMember(leader.cluster().getMember().memberId())
        .addTypeChangeListener(
            t -> {
              threadAssertEquals(t, RaftMember.Type.PASSIVE);
              resume();
            });
    leader.cluster().getMember().demote(RaftMember.Type.PASSIVE).thenRun(this::resume);
    await(15000, 2);
  }

  /** Tests submitting a command. */
  @Test
  public void testOneNodeSubmitCommand() throws Throwable {
    testSubmitCommand(1);
  }

  /** Tests submitting a command with a configured consistency level. */
  private void testSubmitCommand(final int nodes) throws Throwable {
    createServers(nodes);

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    primitive.write("Hello world!").thenRun(this::resume);

    await(30000);
  }

  /** Tests submitting a command. */
  @Test
  public void testTwoNodeSubmitCommand() throws Throwable {
    testSubmitCommand(2);
  }

  /** Tests submitting a command. */
  @Test
  public void testThreeNodeSubmitCommand() throws Throwable {
    testSubmitCommand(3);
  }

  /** Tests submitting a command. */
  @Test
  public void testFourNodeSubmitCommand() throws Throwable {
    testSubmitCommand(4);
  }

  /** Tests submitting a command. */
  @Test
  public void testFiveNodeSubmitCommand() throws Throwable {
    testSubmitCommand(5);
  }

  /** Tests submitting a command. */
  @Test
  public void testTwoOfThreeNodeSubmitCommand() throws Throwable {
    testSubmitCommand(2, 3);
  }

  /** Tests submitting a command to a partial cluster. */
  private void testSubmitCommand(final int live, final int total) throws Throwable {
    createServers(live, total);

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    primitive.write("Hello world!").thenRun(this::resume);

    await(30000);
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
      } else {
        server
            .listen(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
            .thenRun(this::resume);
      }
      servers.add(server);
    }

    await(30000 * live, live);

    return servers;
  }

  @Test
  public void testNodeCatchUpAfterCompaction() throws Throwable {
    // given
    createServers(3);

    servers.get(0).shutdown();
    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);

    final int entries = 10;
    final int entrySize = 1024;
    final String entry = RandomStringUtils.random(entrySize);
    for (int i = 0; i < entries; i++) {
      primitive.write(entry).whenComplete((v, t) -> resume());
    }
    await(10_000, entries);

    // when
    CompletableFuture.allOf(servers.get(1).compact(), servers.get(2).compact())
        .get(15_000, TimeUnit.MILLISECONDS);

    // then
    final RaftServer server = createServer(members.get(0).memberId());
    final List<MemberId> members =
        this.members.stream().map(RaftMember::memberId).collect(Collectors.toList());

    server.join(members).get(15_000, TimeUnit.MILLISECONDS);
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

  /** Tests submitting a query. */
  @Test
  public void testOneNodeSubmitQueryWithSequentialConsistency() throws Throwable {
    testSubmitQuery(1, ReadConsistency.SEQUENTIAL);
  }

  /** Tests submitting a query with a configured consistency level. */
  private void testSubmitQuery(final int nodes, final ReadConsistency consistency)
      throws Throwable {
    createServers(nodes);

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client, consistency);
    primitive.read().thenRun(this::resume);

    await(30000);
  }

  /** Tests submitting a query. */
  @Test
  public void testOneNodeSubmitQueryWithBoundedLinearizableConsistency() throws Throwable {
    testSubmitQuery(1, ReadConsistency.LINEARIZABLE_LEASE);
  }

  /** Tests submitting a query. */
  @Test
  public void testOneNodeSubmitQueryWithLinearizableConsistency() throws Throwable {
    testSubmitQuery(1, ReadConsistency.LINEARIZABLE);
  }

  /** Tests submitting a query. */
  @Test
  public void testTwoNodeSubmitQueryWithSequentialConsistency() throws Throwable {
    testSubmitQuery(2, ReadConsistency.SEQUENTIAL);
  }

  /** Tests submitting a query. */
  @Test
  public void testTwoNodeSubmitQueryWithBoundedLinearizableConsistency() throws Throwable {
    testSubmitQuery(2, ReadConsistency.LINEARIZABLE_LEASE);
  }

  /** Tests submitting a query. */
  @Test
  public void testTwoNodeSubmitQueryWithLinearizableConsistency() throws Throwable {
    testSubmitQuery(2, ReadConsistency.LINEARIZABLE);
  }

  /** Tests submitting a query. */
  @Test
  public void testThreeNodeSubmitQueryWithSequentialConsistency() throws Throwable {
    testSubmitQuery(3, ReadConsistency.SEQUENTIAL);
  }

  /** Tests submitting a query. */
  @Test
  public void testThreeNodeSubmitQueryWithBoundedLinearizableConsistency() throws Throwable {
    testSubmitQuery(3, ReadConsistency.LINEARIZABLE_LEASE);
  }

  /** Tests submitting a query. */
  @Test
  public void testThreeNodeSubmitQueryWithLinearizableConsistency() throws Throwable {
    testSubmitQuery(3, ReadConsistency.LINEARIZABLE);
  }

  /** Tests submitting a query. */
  @Test
  public void testFourNodeSubmitQueryWithSequentialConsistency() throws Throwable {
    testSubmitQuery(4, ReadConsistency.SEQUENTIAL);
  }

  /** Tests submitting a query. */
  @Test
  public void testFourNodeSubmitQueryWithBoundedLinearizableConsistency() throws Throwable {
    testSubmitQuery(4, ReadConsistency.LINEARIZABLE_LEASE);
  }

  /** Tests submitting a query. */
  @Test
  public void testFourNodeSubmitQueryWithLinearizableConsistency() throws Throwable {
    testSubmitQuery(4, ReadConsistency.LINEARIZABLE);
  }

  /** Tests submitting a query. */
  @Test
  public void testFiveNodeSubmitQueryWithSequentialConsistency() throws Throwable {
    testSubmitQuery(5, ReadConsistency.SEQUENTIAL);
  }

  /** Tests submitting a query. */
  @Test
  public void testFiveNodeSubmitQueryWithBoundedLinearizableConsistency() throws Throwable {
    testSubmitQuery(5, ReadConsistency.LINEARIZABLE_LEASE);
  }

  /** Tests submitting a query. */
  @Test
  public void testFiveNodeSubmitQueryWithLinearizableConsistency() throws Throwable {
    testSubmitQuery(5, ReadConsistency.LINEARIZABLE);
  }

  /** Tests submitting a sequential event. */
  @Test
  public void testOneNodeSequentialEvent() throws Throwable {
    testSequentialEvent(1);
  }

  /** Tests submitting a sequential event. */
  private void testSequentialEvent(final int nodes) throws Throwable {
    createServers(nodes);

    final AtomicLong count = new AtomicLong();
    final AtomicLong index = new AtomicLong();

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    primitive
        .onEvent(
            event -> {
              threadAssertEquals(count.incrementAndGet(), 2L);
              threadAssertEquals(index.get(), event);
              resume();
            })
        .join();

    primitive
        .sendEvent(true)
        .thenAccept(
            result -> {
              threadAssertNotNull(result);
              threadAssertEquals(count.incrementAndGet(), 1L);
              index.set(result);
              resume();
            });

    await(30000, 2);
  }

  /** Tests submitting a sequential event. */
  @Test
  public void testTwoNodeSequentialEvent() throws Throwable {
    testSequentialEvent(2);
  }

  /** Tests submitting a sequential event. */
  @Test
  public void testThreeNodeSequentialEvent() throws Throwable {
    testSequentialEvent(3);
  }

  /** Tests submitting a sequential event. */
  @Test
  public void testFourNodeSequentialEvent() throws Throwable {
    testSequentialEvent(4);
  }

  /** Tests submitting a sequential event. */
  @Test
  public void testFiveNodeSequentialEvent() throws Throwable {
    testSequentialEvent(5);
  }

  /** Tests submitting sequential events. */
  @Test
  public void testOneNodeEvents() throws Throwable {
    testEvents(1);
  }

  /** Tests submitting sequential events to all sessions. */
  private void testEvents(final int nodes) throws Throwable {
    createServers(nodes);

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    primitive
        .onEvent(
            event -> {
              threadAssertNotNull(event);
              resume();
            })
        .join();
    createPrimitive(createClient())
        .onEvent(
            event -> {
              threadAssertNotNull(event);
              resume();
            })
        .join();
    createPrimitive(createClient())
        .onEvent(
            event -> {
              threadAssertNotNull(event);
              resume();
            })
        .join();

    primitive.sendEvent(false).thenRun(this::resume);

    await(30000, 4);
  }

  /** Tests submitting sequential events. */
  @Test
  public void testTwoNodeEvents() throws Throwable {
    testEvents(2);
  }

  /** Tests submitting sequential events. */
  @Test
  public void testThreeNodeEvents() throws Throwable {
    testEvents(3);
  }

  /** Tests submitting sequential events. */
  @Test
  public void testFourNodeEvents() throws Throwable {
    testEvents(4);
  }

  /** Tests submitting sequential events. */
  @Test
  public void testFiveNodeEvents() throws Throwable {
    testEvents(5);
  }

  /** Tests that operations are properly sequenced on the client. */
  @Test
  public void testSequenceLinearizableOperations() throws Throwable {
    testSequenceOperations(5, ReadConsistency.LINEARIZABLE);
  }

  /** Tests submitting a linearizable event that publishes to all sessions. */
  private void testSequenceOperations(final int nodes, final ReadConsistency consistency)
      throws Throwable {
    createServers(nodes);

    final AtomicInteger counter = new AtomicInteger();
    final AtomicLong index = new AtomicLong();

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client, consistency);
    primitive.onEvent(
        value -> {
          threadAssertEquals(counter.incrementAndGet(), 3);
          threadAssertTrue(value >= index.get());
          index.set(value);
          resume();
        });

    primitive
        .write("Hello world!")
        .thenAccept(
            result -> {
              threadAssertNotNull(result);
              threadAssertEquals(counter.incrementAndGet(), 1);
              threadAssertTrue(index.compareAndSet(0, result));
              resume();
            });

    primitive
        .sendEvent(true)
        .thenAccept(
            result -> {
              threadAssertNotNull(result);
              threadAssertEquals(counter.incrementAndGet(), 2);
              threadAssertTrue(result > index.get());
              index.set(result);
              resume();
            });

    primitive
        .read()
        .thenAccept(
            result -> {
              threadAssertNotNull(result);
              threadAssertEquals(counter.incrementAndGet(), 4);
              final long i = index.get();
              threadAssertTrue(result >= i);
              resume();
            });

    await(30000, 4);
  }

  /** Tests that operations are properly sequenced on the client. */
  @Test
  public void testSequenceBoundedLinearizableOperations() throws Throwable {
    testSequenceOperations(5, ReadConsistency.LINEARIZABLE_LEASE);
  }

  /** Tests that operations are properly sequenced on the client. */
  @Test
  public void testSequenceSequentialOperations() throws Throwable {
    testSequenceOperations(5, ReadConsistency.SEQUENTIAL);
  }

  /** Tests blocking within an event thread. */
  @Test
  public void testBlockOnEvent() throws Throwable {
    createServers(3);

    final AtomicLong index = new AtomicLong();

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);

    primitive.onEvent(
        event -> {
          threadAssertEquals(index.get(), event);
          try {
            threadAssertTrue(index.get() <= primitive.read().get(10, TimeUnit.SECONDS));
          } catch (final InterruptedException | TimeoutException | ExecutionException e) {
            threadFail(e);
          }
          resume();
        });

    primitive
        .sendEvent(true)
        .thenAccept(
            result -> {
              threadAssertNotNull(result);
              index.compareAndSet(0, result);
              resume();
            });

    await(10000, 2);
  }

  /** Tests submitting linearizable events. */
  @Test
  public void testFiveNodeManyEvents() throws Throwable {
    testManyEvents(5);
  }

  /** Tests submitting a linearizable event that publishes to all sessions. */
  private void testManyEvents(final int nodes) throws Throwable {
    createServers(nodes);

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    primitive.onEvent(
        message -> {
          threadAssertNotNull(message);
          resume();
        });

    for (int i = 0; i < 10; i++) {
      primitive.sendEvent(true).thenRun(this::resume);
      await(30000, 2);
    }
  }

  /** Tests submitting linearizable events. */
  @Test
  public void testThreeNodesManyEventsAfterLeaderShutdown() throws Throwable {
    testManyEventsAfterLeaderShutdown(3);
  }

  /** Tests submitting a linearizable event that publishes to all sessions. */
  private void testManyEventsAfterLeaderShutdown(final int nodes) throws Throwable {
    final List<RaftServer> servers = createServers(nodes);

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    primitive.onEvent(
        event -> {
          threadAssertNotNull(event);
          resume();
        });

    for (int i = 0; i < 10; i++) {
      primitive.sendEvent(true).thenRun(this::resume);
      await(30000, 2);
    }

    final RaftServer leader =
        servers.stream().filter(s -> s.getRole() == RaftServer.Role.LEADER).findFirst().get();
    leader.shutdown().get(10, TimeUnit.SECONDS);

    for (int i = 0; i < 10; i++) {
      primitive.sendEvent(true).thenRun(this::resume);
      await(30000, 2);
    }
  }

  /** Tests submitting linearizable events. */
  @Test
  public void testThreeNodesAndRestartFollower() throws Throwable {
    // given
    final List<RaftServer> servers = createServers(3);

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    primitive.onEvent(
        event -> {
          threadAssertNotNull(event);
          resume();
        });

    final RaftServer follower =
        servers.stream().filter(s -> s.getRole() == Role.FOLLOWER).findFirst().get();
    final MemberId memberId = new MemberId(follower.name());
    follower.shutdown().get(10, TimeUnit.SECONDS);

    for (int i = 0; i < 1_000; i++) {
      primitive.sendEvent(true).thenRun(this::resume);
    }
    await(30000, 2 * 1000);

    // when
    LoggerFactory.getLogger(RaftTest.class).error("====\nRestart!\n====");
    members.removeIf(r -> r.memberId().equals(memberId));
    createServer(memberId)
        .bootstrap(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
        .thenRun(this::resume);

    // then
    await(30000);
  }

  /** Tests submitting linearizable events. */
  @Test
  public void testThreeNodesAndRestartLeader() throws Throwable {
    // given
    final List<RaftServer> servers = createServers(3);

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    primitive.onEvent(
        event -> {
          threadAssertNotNull(event);
          resume();
        });

    final RaftServer leader =
        servers.stream().filter(s -> s.getRole() == Role.LEADER).findFirst().get();
    final MemberId memberId = new MemberId(leader.name());
    leader.shutdown().get(10, TimeUnit.SECONDS);

    for (int i = 0; i < 1_000; i++) {
      primitive.sendEvent(true).thenRun(this::resume);
    }
    await(30000, 2 * 1000);

    // when
    LoggerFactory.getLogger(RaftTest.class).error("====\nRestart!\n====");
    members.removeIf(r -> r.memberId().equals(memberId));
    createServer(memberId)
        .bootstrap(members.stream().map(RaftMember::memberId).collect(Collectors.toList()))
        .thenRun(this::resume);

    // then
    await(30000);
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

  /** Tests submitting linearizable events. */
  @Test
  public void testFiveNodesManyEventsAfterLeaderShutdown() throws Throwable {
    testManyEventsAfterLeaderShutdown(5);
  }

  /** Tests submitting sequential events. */
  @Test
  public void testThreeNodesEventsAfterFollowerKill() throws Throwable {
    testEventsAfterFollowerKill(3);
  }

  /** Tests submitting a sequential event that publishes to all sessions. */
  private void testEventsAfterFollowerKill(final int nodes) throws Throwable {
    final List<RaftServer> servers = createServers(nodes);

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    primitive.onEvent(
        event -> {
          threadAssertNotNull(event);
          resume();
        });

    for (int i = 0; i < 10; i++) {
      primitive.sendEvent(true).thenRun(this::resume);
      await(30000, 2);
    }

    primitive.sendEvent(true).thenRun(this::resume);

    final RaftServer follower =
        servers.stream().filter(s -> s.getRole() == RaftServer.Role.FOLLOWER).findFirst().get();
    follower.shutdown().get(10, TimeUnit.SECONDS);

    await(30000, 2);

    for (int i = 0; i < 10; i++) {
      primitive.sendEvent(true).thenRun(this::resume);
      await(30000, 2);
    }
  }

  /** Tests submitting sequential events. */
  @Test
  public void testFiveNodesEventsAfterFollowerKill() throws Throwable {
    testEventsAfterFollowerKill(5);
  }

  /** Tests submitting events. */
  @Test
  public void testFiveNodesEventsAfterLeaderKill() throws Throwable {
    testEventsAfterLeaderKill(5);
  }

  /** Tests submitting a linearizable event that publishes to all sessions. */
  private void testEventsAfterLeaderKill(final int nodes) throws Throwable {
    final List<RaftServer> servers = createServers(nodes);

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    primitive.onEvent(
        event -> {
          threadAssertNotNull(event);
          resume();
        });

    for (int i = 0; i < 10; i++) {
      primitive.sendEvent(true).thenRun(this::resume);
      await(30000, 2);
    }

    primitive.sendEvent(true).thenRun(this::resume);

    final RaftServer leader =
        servers.stream().filter(s -> s.getRole() == RaftServer.Role.LEADER).findFirst().get();
    leader.shutdown().get(10, TimeUnit.SECONDS);

    await(30000);

    for (int i = 0; i < 10; i++) {
      primitive.sendEvent(true).thenRun(this::resume);
      await(30000, 2);
    }
  }

  /** Tests submitting linearizable events. */
  @Test
  public void testFiveNodeManySessionsManyEvents() throws Throwable {
    testManySessionsManyEvents(5);
  }

  /** Tests submitting a linearizable event that publishes to all sessions. */
  private void testManySessionsManyEvents(final int nodes) throws Throwable {
    createServers(nodes);

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    primitive.onEvent(
        event -> {
          threadAssertNotNull(event);
          resume();
        });

    createPrimitive(createClient())
        .onEvent(
            event -> {
              threadAssertNotNull(event);
              resume();
            });

    createPrimitive(createClient())
        .onEvent(
            event -> {
              threadAssertNotNull(event);
              resume();
            });

    for (int i = 0; i < 10; i++) {
      primitive.sendEvent(false).thenRun(this::resume);
      await(10000, 4);
    }
  }

  /** Tests session expiring events. */
  @Test
  public void testOneNodeExpireEvent() throws Throwable {
    testSessionExpire(1);
  }

  /** Tests a session expiring. */
  private void testSessionExpire(final int nodes) throws Throwable {
    createServers(nodes);

    final RaftClient client1 = createClient();
    final TestPrimitive primitive1 = createPrimitive(client1);
    final RaftClient client2 = createClient();
    createSession(client2);
    primitive1.onExpire(event -> resume()).thenRun(this::resume);
    client2.close().thenRun(this::resume);
    await(Duration.ofSeconds(10).toMillis(), 3);
  }

  /** Tests session expiring events. */
  @Test
  public void testThreeNodeExpireEvent() throws Throwable {
    testSessionExpire(3);
  }

  /** Tests session expiring events. */
  @Test
  public void testFiveNodeExpireEvent() throws Throwable {
    testSessionExpire(5);
  }

  /** Tests session close events. */
  @Test
  public void testOneNodeCloseEvent() throws Throwable {
    testSessionClose(1);
  }

  /** Tests a session closing. */
  private void testSessionClose(final int nodes) throws Throwable {
    createServers(nodes);

    final RaftClient client1 = createClient();
    final TestPrimitive primitive1 = createPrimitive(client1);
    final RaftClient client2 = createClient();
    primitive1.onClose(event -> resume()).thenRun(this::resume);
    await(Duration.ofSeconds(10).toMillis(), 1);
    createSession(client2).close().thenRun(this::resume);
    await(Duration.ofSeconds(10).toMillis(), 2);
  }

  /** Tests session close events. */
  @Test
  public void testThreeNodeCloseEvent() throws Throwable {
    testSessionClose(3);
  }

  /** Tests session close events. */
  @Test
  public void testFiveNodeCloseEvent() throws Throwable {
    testSessionClose(5);
  }

  @Test
  public void testThreeNodeManyEventsDoNotMissHeartbeats() throws Throwable {
    // given
    createServers(3);

    final RaftClient client = createClient();
    final TestPrimitive primitive = createPrimitive(client);
    primitive.onEvent(
        message -> {
          threadAssertNotNull(message);
          resume();
        });

    final double startMissedHeartBeats = RaftRoleMetrics.getHeartbeatMissCount("1");

    // when
    for (int i = 0; i < 1_000; i++) {
      primitive.sendEvent(true);
    }
    await(10000, 1_000);

    // then
    final double missedHeartBeats = RaftRoleMetrics.getHeartbeatMissCount("1");
    assertThat(0.0, is(missedHeartBeats - startMissedHeartBeats));
  }

  @Test
  public void testSnapshotSentOnDataLoss() throws Throwable {
    final List<RaftMember> members =
        Lists.newArrayList(createMember(), createMember(), createMember());
    final Map<MemberId, RaftStorage> storages =
        members.stream()
            .map(RaftMember::memberId)
            .collect(Collectors.toMap(Function.identity(), this::createStorage));
    final Map<MemberId, RaftServer> servers =
        storages.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, this::createServer));

    // wait for cluster to start
    startCluster(servers);

    // fill two segments then compact so we have at least one snapshot
    final RaftClient client = createClient(members);
    final TestPrimitive primitive = createPrimitive(client);
    fillSegment(primitive);
    fillSegment(primitive);
    Futures.allOf(servers.values().stream().map(RaftServer::compact)).thenRun(this::resume);
    await(30000);

    // partition into leader/followers
    final Map<Boolean, List<RaftMember>> collect =
        members.stream()
            .collect(Collectors.partitioningBy(m -> servers.get(m.memberId()).isLeader()));
    final RaftMember leader = collect.get(true).get(0);
    final RaftStorage leaderStorage = storages.get(leader.memberId());
    final RaftMember slave = collect.get(false).get(0);

    // shutdown client + primitive
    primitive.close().thenCompose(nothing -> client.close()).thenRun(this::resume);
    await(30000);

    // shutdown other node
    final RaftMember other = collect.get(false).get(1);
    servers.get(other.memberId()).shutdown().thenRun(this::resume);
    await(30000);

    // shutdown slave and recreate from scratch
    RaftServer slaveServer =
        recreateServerWithDataLoss(
            Arrays.asList(leader.memberId(), other.memberId()),
            slave,
            servers.get(slave.memberId()));
    assertEquals(
        leaderStorage.getSnapshotStore().getCurrentSnapshotIndex(),
        slaveServer.getContext().getStorage().getSnapshotStore().getCurrentSnapshotIndex());

    // and again a second time to ensure the snapshot index of the member is reset
    slaveServer =
        recreateServerWithDataLoss(
            Arrays.asList(leader.memberId(), other.memberId()), slave, slaveServer);

    // ensure the snapshots are the same
    final Snapshot leaderSnapshot = leaderStorage.getSnapshotStore().getCurrentSnapshot();
    final Snapshot slaveSnapshot =
        slaveServer.getContext().getStorage().getSnapshotStore().getCurrentSnapshot();

    assertEquals(leaderSnapshot.index(), slaveSnapshot.index());
    assertEquals(leaderSnapshot.term(), slaveSnapshot.term());
    assertEquals(leaderSnapshot.timestamp(), slaveSnapshot.timestamp());
    assertEquals(leaderSnapshot.version(), slaveSnapshot.version());

    final ByteBuffer leaderSnapshotData = readSnapshot(leaderSnapshot);
    final ByteBuffer slaveSnapshotData = readSnapshot(slaveSnapshot);
    assertEquals(leaderSnapshotData, slaveSnapshotData);
  }

  @Test
  public void testCorrectTermInSnapshot() throws Throwable {
    final List<RaftMember> members =
        Lists.newArrayList(createMember(), createMember(), createMember());
    final List<MemberId> memberIds =
        members.stream().map(RaftMember::memberId).collect(Collectors.toList());
    final Map<MemberId, RaftServer> servers =
        memberIds.stream().collect(Collectors.toMap(Function.identity(), this::createServer));

    // wait for cluster to start
    startCluster(servers);
    servers.get(members.get(0).memberId()).shutdown().join();

    // fill two segments then compact so we have at least one snapshot
    final RaftClient client = createClient(members);
    final TestPrimitive primitive = createPrimitive(client);
    fillSegment(primitive);
    fillSegment(primitive);
    final MemberId leaderId =
        members.stream()
            .filter(m -> servers.get(m.memberId()).isLeader())
            .findFirst()
            .get()
            .memberId();
    servers.get(leaderId).compact().get(15_000, TimeUnit.MILLISECONDS);

    final Snapshot currentSnapshot =
        servers.get(leaderId).getContext().getStorage().getSnapshotStore().getCurrentSnapshot();
    final long leaderTerm = servers.get(leaderId).getTerm();

    assertEquals(currentSnapshot.term(), leaderTerm);

    final RaftServer server = createServer(members.get(0).memberId());
    server.join(memberIds).get(15_000, TimeUnit.MILLISECONDS);

    final SnapshotStore snapshotStore = server.getContext().getStorage().getSnapshotStore();

    waitUntil(() -> snapshotStore.getCurrentSnapshot() != null, 100);

    final Snapshot receivedSnapshot = snapshotStore.getCurrentSnapshot();

    assertEquals(receivedSnapshot.index(), currentSnapshot.index());
    assertEquals(receivedSnapshot.term(), leaderTerm);
  }

  @Test
  public void shouldCompactStorageUnderHighLoad() throws Throwable {
    // given
    final List<RaftMember> members =
        Lists.newArrayList(createMember(), createMember(), createMember());
    final Map<MemberId, RaftStorage> storages =
        members.stream()
            .map(RaftMember::memberId)
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    memberId ->
                        createStorage(
                            memberId,
                            storageBuilder ->
                                storageBuilder.withStorageStatistics(
                                    new FakeStatistics(
                                        new File(directory.toFile(), memberId.toString()))))));
    final Map<MemberId, RaftServer> servers =
        storages.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry ->
                        createServer(
                            entry.getKey(),
                            builder ->
                                builder
                                    .withStorage(entry.getValue())
                                    .withLoadMonitorFactory(FakeLoadMonitor::new)
                                    .withStateMachineFactory(FakeStateMachine::new))));
    // wait for cluster to start
    startCluster(servers);

    // when high load
    final RaftClient client = createClient(members);
    final TestPrimitive primitive = createPrimitive(client);

    // then we should still be able to snapshots and compactions
    final MemberId chosenOne = members.get(0).memberId();
    fillSegment(primitive);
    fillSegment(primitive);
    waitUntil(() -> storages.get(chosenOne).getSnapshotStore().getCurrentSnapshot() != null, 100);
  }

  private ByteBuffer readSnapshot(final Snapshot snapshot) {
    ByteBuffer buffer = ByteBuffer.allocate(2048);
    try (final SnapshotChunkReader reader = snapshot.newChunkReader()) {
      while (reader.hasNext()) {
        final SnapshotChunk chunk = reader.next();
        // resize buffer
        if (buffer.remaining() < chunk.data().remaining()) {
          final ByteBuffer buf = ByteBuffer.allocate(buffer.capacity() * 2);
          buffer.flip();
          buf.put(buffer);
          buffer = buf;
        }

        buffer.put(chunk.data());
      }
    }

    return buffer;
  }

  private RaftServer recreateServerWithDataLoss(
      final List<MemberId> others, final RaftMember member, final RaftServer server)
      throws TimeoutException, InterruptedException {
    server.shutdown().thenRun(this::resume);
    await(30000);
    deleteStorage(server.getContext().getStorage());

    final RaftServer newServer = createServer(member.memberId());
    newServer.bootstrap(others).thenRun(this::resume);
    await(30000);
    return newServer;
  }

  private void deleteStorage(final RaftStorage storage) {
    storage.deleteSnapshotStore();
    storage.deleteLog();
    storage.deleteMetaStore();
  }

  private void fillSegment(final TestPrimitive primitive)
      throws InterruptedException, ExecutionException, TimeoutException {
    final String entry = RandomStringUtils.randomAscii(1024);
    IntStream.range(0, 10).forEach(i -> primitive.write(entry).whenComplete((v, t) -> resume()));
    await(10_000, 10);
  }

  private RaftMember createMember() {
    final RaftMember member = nextMember(RaftMember.Type.ACTIVE);
    members.add(member);

    return member;
  }

  private void startCluster(final Map<MemberId, RaftServer> servers)
      throws TimeoutException, InterruptedException {
    final List<MemberId> members = new ArrayList<>(servers.keySet());
    for (final RaftServer s : servers.values()) {
      s.bootstrap(members).thenRun(this::resume);
    }

    await(30000 * servers.size(), servers.size());
  }

  private RaftServer createServer(final Map.Entry<MemberId, RaftStorage> entry) {
    return createServer(entry.getKey(), b -> b.withStorage(entry.getValue()));
  }

  private void waitUntil(final BooleanSupplier condition, int retries) {
    try {
      while (!condition.getAsBoolean() && retries > 0) {
        Thread.sleep(100);
        retries--;
      }
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }

    assertTrue(condition.getAsBoolean());
  }

  @Test
  public void testRoleChangeNotificationAfterInitialEntryOnLeader() throws Throwable {
    // given
    final List<RaftServer> servers = createServers(3);
    final RaftServer leader =
        servers.stream().filter(s -> s.getRole() == RaftServer.Role.LEADER).findFirst().get();
    final CountDownLatch transitionCompleted = new CountDownLatch(1);
    servers.stream()
        .forEach(
            server ->
                server.addRoleChangeListener(
                    (role, term) ->
                        assertLastReadInitialEntry(role, term, server, transitionCompleted)));
    // when
    leader.stepDown();

    // then
    transitionCompleted.await(10, TimeUnit.SECONDS);
    assertEquals(0, transitionCompleted.getCount());
  }

  private void assertLastReadInitialEntry(
      final Role role,
      final long term,
      final RaftServer server,
      final CountDownLatch transitionCompleted) {
    if (role == Role.LEADER) {
      final RaftLogReader raftLogReader = server.getContext().getLog().openReader(0, Mode.COMMITS);
      raftLogReader.reset(raftLogReader.getLastIndex());
      final RaftLogEntry entry = raftLogReader.next().entry();
      assert (entry instanceof InitializeEntry);
      assertEquals(term, entry.term());
      transitionCompleted.countDown();
    }
  }

  @Test
  public void testNotifyOnFailure() throws Throwable {
    // given
    final List<RaftServer> servers = createServers(1);
    final RaftServer server = servers.get(0);
    final CountDownLatch firstListener = new CountDownLatch(1);
    final CountDownLatch secondListener = new CountDownLatch(1);

    server.addFailureListener(() -> firstListener.countDown());
    server.addFailureListener(() -> secondListener.countDown());

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
    firstListener.await(2, TimeUnit.SECONDS);
    secondListener.await(1, TimeUnit.SECONDS);
    assertEquals(0, firstListener.getCount());
    assertEquals(0, secondListener.getCount());

    assertEquals(server.getRole(), Role.INACTIVE);
  }

  private static final class FakeStatistics extends StorageStatistics {

    FakeStatistics(final File file) {
      super(file);
    }

    @Override
    public long getFreeMemory() {
      return 1;
    }

    @Override
    public long getTotalMemory() {
      return 10;
    }
  }

  private static final class FakeLoadMonitor extends LoadMonitor {

    FakeLoadMonitor(
        final int windowSize, final int highLoadThreshold, final ThreadContext threadContext) {
      super(windowSize, highLoadThreshold, threadContext);
    }

    @Override
    public boolean isUnderHighLoad() {
      return true;
    }
  }
}
