/*
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.TestRaftProtocolFactory;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.raft.snapshot.TestSnapshotStore;
import io.atomix.raft.storage.RaftStorage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the Raft mechanisms that the system partition needs: a partition replicated on every
 * broker, in which only some members are voters ({@link Type#ACTIVE}) and the others are {@link
 * Type#PASSIVE}.
 */
final class SystemPartitionRaftTest {
  private final MemberId id1 = MemberId.from("1");
  private final MemberId id2 = MemberId.from("2");
  private final MemberId id3 = MemberId.from("3");
  private final MemberId id4 = MemberId.from("4");

  private final TestRaftProtocolFactory protocolFactory = new TestRaftProtocolFactory();
  private final List<RaftServer> servers = new LinkedList<>();
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  @TempDir private Path tmp;

  @AfterEach
  void cleanup() {
    for (final var server : servers) {
      server.shutdown().join();
    }
  }

  private RaftServer createServer(final MemberId id, final MemberId... others) {
    return createServer(id, config -> {}, others);
  }

  private RaftServer createServer(
      final MemberId id,
      final Consumer<RaftPartitionConfig> configCustomizer,
      final MemberId... others) {
    final ClusterMembershipService membershipService =
        StaticClusterMembershipService.of(id, others);
    final var protocol = protocolFactory.newServerProtocol(id);
    final var storage =
        RaftStorage.builder(meterRegistry)
            .withDirectory(tmp.resolve(id.id()).toFile())
            .withSnapshotStore(new TestSnapshotStore(new AtomicReference<>()))
            .withMaxSegmentSize(1024 * 10)
            .build();
    final var partitionConfig =
        new RaftPartitionConfig()
            .setElectionTimeout(Duration.ofMillis(500))
            .setHeartbeatInterval(Duration.ofMillis(100));
    configCustomizer.accept(partitionConfig);
    final var server =
        RaftServer.builder(id)
            .withMembershipService(membershipService)
            .withProtocol(protocol)
            .withStorage(storage)
            .withPartitionConfig(partitionConfig)
            .withMeterRegistry(meterRegistry)
            .build();
    servers.add(server);
    return server;
  }

  private static Optional<RaftServer> leaderOf(final RaftServer... servers) {
    return Arrays.stream(servers).filter(RaftServer::isLeader).findAny();
  }

  private static RaftServer awaitLeader(final RaftServer... servers) {
    return Awaitility.await("a leader is elected")
        .until(() -> leaderOf(servers), Optional::isPresent)
        .orElseThrow();
  }

  private static Map<MemberId, Type> memberTypes(final RaftServer server) {
    return onRaftThread(
        server,
        () ->
            server.cluster().getMembers().stream()
                .collect(Collectors.toMap(RaftMember::memberId, RaftMember::getType)));
  }

  private static long commitIndex(final RaftServer server) {
    return onRaftThread(server, () -> server.getContext().getCommitIndex());
  }

  private static <T> T onRaftThread(
      final RaftServer server, final java.util.function.Supplier<T> supplier) {
    final var result = new CompletableFuture<T>();
    server
        .getContext()
        .getThreadContext()
        .execute(
            () -> {
              try {
                result.complete(supplier.get());
              } catch (final Exception e) {
                result.completeExceptionally(e);
              }
            });
    return result.orTimeout(5, TimeUnit.SECONDS).join();
  }

  private static long appendEntry(final RaftServer leader) {
    final var result = new AppendResult();
    final RaftContext context = leader.getContext();
    context
        .getThreadContext()
        .execute(
            () ->
                ((LeaderRole) context.getRaftRole())
                    .appendEntry(-1, -1, ByteBuffer.wrap(new byte[0]), result));
    return result.commit().orTimeout(10, TimeUnit.SECONDS).join();
  }

  private record ConfigurationId(long index, long term) {}

  private static ConfigurationId configurationId(final RaftServer server) {
    return onRaftThread(
        server,
        () ->
            new ConfigurationId(
                server.getContext().getCluster().getConfiguration().index(),
                server.getContext().getCluster().getConfigurationTerm()));
  }

  private List<RaftServer> bootstrapTwoVotersOnePassive() {
    final var members = twoVotersOnePassive();
    final var m1 = createServer(id1, id2, id3);
    final var m2 = createServer(id2, id1, id3);
    final var m3 = createServer(id3, id1, id2);
    CompletableFuture.allOf(m1.bootstrap(members), m2.bootstrap(members), m3.bootstrap(members))
        .orTimeout(30, TimeUnit.SECONDS)
        .join();
    return List.of(m1, m2, m3);
  }

  /** Retries the change until the leader accepts it, e.g. once the member caught up. */
  private static ReconfigureResponse awaitReconfigured(
      final RaftServer requester, final RaftServer leader, final Map<MemberId, Type> target) {
    return Awaitility.await("reconfiguration to %s is accepted".formatted(target))
        .until(
            () -> {
              final var current = configurationId(leader);
              return requester
                  .reconfigure(target, current.index(), current.term())
                  .orTimeout(10, TimeUnit.SECONDS)
                  .join();
            },
            response -> response.status() == Status.OK);
  }

  private Map<MemberId, Type> twoVotersOnePassive() {
    return Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.PASSIVE);
  }

  @Nested
  final class TypedBootstrap {

    @Test
    void shouldBootstrapWithTypedMembers() {
      // given
      final var members = twoVotersOnePassive();
      final var m1 = createServer(id1, id2, id3);
      final var m2 = createServer(id2, id1, id3);
      final var m3 = createServer(id3, id1, id2);

      // when
      CompletableFuture.allOf(m1.bootstrap(members), m2.bootstrap(members), m3.bootstrap(members))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();

      // then
      final var leader = awaitLeader(m1, m2, m3);
      assertThat(leader).isNotSameAs(m3);
      assertThat(m3.getRole()).isEqualTo(Role.PASSIVE);
      assertThat(memberTypes(m1)).isEqualTo(members);
      assertThat(memberTypes(m3)).isEqualTo(members);

      // and the passive member receives committed entries
      final var index = appendEntry(leader);
      Awaitility.await("passive member commits the entry")
          .untilAsserted(() -> assertThat(commitIndex(m3)).isGreaterThanOrEqualTo(index));
    }

    @Test
    void shouldKeepTypesAfterRestart() {
      // given
      final var members = twoVotersOnePassive();
      final var m1 = createServer(id1, id2, id3);
      final var m2 = createServer(id2, id1, id3);
      final var m3 = createServer(id3, id1, id2);
      CompletableFuture.allOf(m1.bootstrap(members), m2.bootstrap(members), m3.bootstrap(members))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();

      // when - restart the passive member with an untyped bootstrap, which would make all ACTIVE
      m3.shutdown().join();
      servers.remove(m3);
      final var restarted = createServer(id3, id1, id2);
      restarted.bootstrap(id1, id2, id3).orTimeout(30, TimeUnit.SECONDS).join();

      // then - the stored configuration wins
      assertThat(restarted.getRole()).isEqualTo(Role.PASSIVE);
      assertThat(memberTypes(restarted)).isEqualTo(members);
    }

    @Test
    void shouldRejectBootstrapWithoutVoter() {
      // given
      final var m1 = createServer(id1, id2);

      // when - then
      assertThatThrownBy(
              () ->
                  m1.bootstrap(Map.of(id1, Type.PASSIVE, id2, Type.PASSIVE))
                      .orTimeout(5, TimeUnit.SECONDS)
                      .join())
          .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBootstrapWithoutLocalMember() {
      // given
      final var m4 = createServer(id4, id1, id2);

      // when - then
      assertThatThrownBy(
              () -> m4.bootstrap(twoVotersOnePassive()).orTimeout(5, TimeUnit.SECONDS).join())
          .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  final class ExactIndexReconfigure {

    @Test
    void shouldPromotePassiveMemberThroughForwardedRequest() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var m3 = cluster.get(2);
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      appendEntry(leader);
      final var target = Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE);

      // when - the passive member requests its own promotion, which it forwards to the leader
      awaitReconfigured(m3, leader, target);

      // then
      Awaitility.await("all members see the promotion")
          .untilAsserted(
              () -> cluster.forEach(server -> assertThat(memberTypes(server)).isEqualTo(target)));
      Awaitility.await("the promoted member is a voter")
          .untilAsserted(
              () -> assertThat(m3.getRole()).isIn(Role.FOLLOWER, Role.CANDIDATE, Role.LEADER));
    }

    @Test
    void shouldRejectStaleIndexWithCurrentConfiguration() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      final var before = configurationId(leader);
      final var withPassive4 =
          Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.PASSIVE, id4, Type.PASSIVE);
      awaitReconfigured(leader, leader, withPassive4);
      final var current = configurationId(leader);

      // when - a different target calculated from the old configuration
      final var response =
          leader
              .reconfigure(twoVotersOnePassive(), before.index(), before.term())
              .orTimeout(10, TimeUnit.SECONDS)
              .join();

      // then
      assertThat(response.status()).isEqualTo(Status.ERROR);
      assertThat(response.error().type()).isEqualTo(RaftError.Type.CONFIGURATION_ERROR);
      assertThat(response.index()).isEqualTo(current.index());
      assertThat(response.term()).isEqualTo(current.term());
      assertThat(response.members())
          .extracting(RaftMember::memberId, RaftMember::getType)
          .containsExactlyInAnyOrder(
              withPassive4.entrySet().stream()
                  .map(e -> org.assertj.core.groups.Tuple.tuple(e.getKey(), e.getValue()))
                  .toArray(org.assertj.core.groups.Tuple[]::new));
    }

    @Test
    void shouldRejectHigherIndex() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      final var current = configurationId(leader);

      // when
      final var response =
          leader
              .reconfigure(
                  Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.PASSIVE, id4, Type.PASSIVE),
                  current.index() + 1,
                  current.term())
              .orTimeout(10, TimeUnit.SECONDS)
              .join();

      // then
      assertThat(response.status()).isEqualTo(Status.ERROR);
      assertThat(response.index()).isEqualTo(current.index());
    }

    @Test
    void shouldAnswerOkWhenTargetIsAlreadyTheConfiguration() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      final var before = configurationId(leader);
      final var target =
          Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.PASSIVE, id4, Type.PASSIVE);
      awaitReconfigured(leader, leader, target);
      final var after = configurationId(leader);

      // when - a retry with the old index
      final var response =
          leader
              .reconfigure(target, before.index(), before.term())
              .orTimeout(10, TimeUnit.SECONDS)
              .join();

      // then
      assertThat(response.status()).isEqualTo(Status.OK);
      assertThat(configurationId(leader)).isEqualTo(after);
    }

    @Test
    void shouldRejectNewActiveMember() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      final var current = configurationId(leader);

      // when
      final var response =
          Awaitility.await("leader answers with a final response")
              .until(
                  () ->
                      leader
                          .reconfigure(
                              Map.of(
                                  id1,
                                  Type.ACTIVE,
                                  id2,
                                  Type.ACTIVE,
                                  id3,
                                  Type.PASSIVE,
                                  id4,
                                  Type.ACTIVE),
                              current.index(),
                              current.term())
                          .orTimeout(10, TimeUnit.SECONDS)
                          .join(),
                  r -> r.error() == null || r.error().message().contains("new member"));

      // then
      assertThat(response.status()).isEqualTo(Status.ERROR);
      assertThat(response.error().message()).contains("must not be ACTIVE");
      assertThat(configurationId(leader)).isEqualTo(current);
    }
  }

  @Nested
  final class ForceConfigure {

    private List<RaftServer> bootstrapThreeVotersOnePassive() {
      final var members =
          Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE, id4, Type.PASSIVE);
      final var m1 = createServer(id1, id2, id3, id4);
      final var m2 = createServer(id2, id1, id3, id4);
      final var m3 = createServer(id3, id1, id2, id4);
      final var m4 = createServer(id4, id1, id2, id3);
      CompletableFuture.allOf(
              m1.bootstrap(members),
              m2.bootstrap(members),
              m3.bootstrap(members),
              m4.bootstrap(members))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();
      appendEntry(awaitLeader(m1, m2, m3));
      return List.of(m1, m2, m3, m4);
    }

    @Test
    void shouldNotWaitForUnreachablePassiveMember() {
      // given - voter 2 and passive member 4 are down
      final var cluster = bootstrapThreeVotersOnePassive();
      final var m1 = cluster.get(0);
      final var m3 = cluster.get(2);
      cluster.get(1).shutdown().join();
      cluster.get(3).shutdown().join();

      // when - force a configuration that keeps 2 and 4 as passive members
      final var target =
          Map.of(id1, Type.ACTIVE, id3, Type.ACTIVE, id2, Type.PASSIVE, id4, Type.PASSIVE);
      m1.forceConfigure(target).orTimeout(30, TimeUnit.SECONDS).join();

      // then
      final var leader = awaitLeader(m1, m3);
      appendEntry(leader);
      assertThat(memberTypes(leader)).isEqualTo(target);
    }

    @Test
    void shouldForceConfigureFromPassiveMember() {
      // given - voters 1 and 2 are down, the remaining voter has no quorum
      final var cluster = bootstrapThreeVotersOnePassive();
      final var m3 = cluster.get(2);
      final var m4 = cluster.get(3);
      cluster.get(0).shutdown().join();
      cluster.get(1).shutdown().join();

      // when - the passive member forces itself and the remaining voter to be the voters
      final var target =
          Map.of(id3, Type.ACTIVE, id4, Type.ACTIVE, id1, Type.PASSIVE, id2, Type.PASSIVE);
      m4.forceConfigure(target).orTimeout(30, TimeUnit.SECONDS).join();

      // then
      final var leader = awaitLeader(m3, m4);
      appendEntry(leader);
      assertThat(memberTypes(m3)).isEqualTo(target);
      assertThat(memberTypes(m4)).isEqualTo(target);
    }

    @Test
    void shouldStillWaitForUnreachableVoter() {
      // given - voter 2 is down
      final var cluster = bootstrapThreeVotersOnePassive();
      final var m1 = cluster.get(0);
      cluster.get(1).shutdown().join();

      // when - force a configuration in which the unreachable member is a voter
      final var target =
          Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.PASSIVE, id4, Type.PASSIVE);

      // then
      assertThatThrownBy(() -> m1.forceConfigure(target).orTimeout(30, TimeUnit.SECONDS).join())
          .hasRootCauseInstanceOf(RaftException.ProtocolException.class);
    }
  }
}
