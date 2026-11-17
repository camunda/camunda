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
import static org.assertj.core.api.Assertions.tuple;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.FenceEntry;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.VoterPolicy;
import io.atomix.raft.cluster.VoterPolicy.ZoneVoters;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.TestRaftProtocolFactory;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.raft.snapshot.TestSnapshotStore;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.system.Configuration;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
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

  @Nested
  final class Fencing {

    private Configuration configurationOf(final RaftServer server) {
      return onRaftThread(server, () -> server.getContext().getCluster().getConfiguration());
    }

    private List<RaftServer> bootstrapThreeVoters() {
      final var members = Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE);
      final var m1 = createServer(id1, id2, id3);
      final var m2 = createServer(id2, id1, id3);
      final var m3 = createServer(id3, id1, id2);
      CompletableFuture.allOf(m1.bootstrap(members), m2.bootstrap(members), m3.bootstrap(members))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();
      return List.of(m1, m2, m3);
    }

    /** Demotes and fences member 3 with incarnation 0, with a leader among 1 and 2. */
    private RaftServer fenceMember3(final List<RaftServer> cluster) {
      final var m1 = cluster.get(0);
      final var m2 = cluster.get(1);
      final var m3 = cluster.get(2);
      Awaitility.await("member 3 is not the leader")
          .until(
              () -> {
                final var leader = awaitLeader(m1, m2, m3);
                if (leader == m3) {
                  m3.stepDown().join();
                  return false;
                }
                return true;
              });
      final var leader = awaitLeader(m1, m2);
      Awaitility.await("member 3 is demoted and fenced")
          .until(
              () -> {
                final var current = configurationId(leader);
                return leader
                    .reconfigure(
                        Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.PASSIVE),
                        List.of(new FenceEntry(id3, 0)),
                        null,
                        current.index(),
                        current.term())
                    .orTimeout(10, TimeUnit.SECONDS)
                    .join();
              },
              response -> response.status() == Status.OK);
      return leader;
    }

    @Test
    void shouldNotifyFencedMemberWithLeaderNotice() {
      // given
      final var cluster = bootstrapThreeVoters();
      final var m3 = cluster.get(2);
      final var fencedIncarnation = new CompletableFuture<Long>();
      m3.addFencedListener(fencedIncarnation::complete);

      // when
      final var leader = fenceMember3(cluster);

      // then
      assertThat(fencedIncarnation.orTimeout(30, TimeUnit.SECONDS).join()).isZero();
      Awaitility.await("fenced member stays inactive")
          .untilAsserted(() -> assertThat(m3.getRole()).isEqualTo(Role.INACTIVE));
      assertThat(configurationOf(leader).fenced()).containsExactly(new FenceEntry(id3, 0));
      // the cluster continues without the fenced member
      appendEntry(leader);
    }

    @Test
    void shouldRejectPollFromFencedMemberWithoutTakingItsTerm() {
      // given
      final var cluster = bootstrapThreeVoters();
      final var m1 = cluster.get(0);
      final var m3 = cluster.get(2);
      final var leader = fenceMember3(cluster);
      appendEntry(leader);
      final var termBefore = m1.getTerm();

      // when - the fenced member asks for a vote with a much higher term
      final var protocol = (TestRaftServerProtocol) m3.getContext().getProtocol();
      final var response =
          protocol
              .poll(
                  id1,
                  PollRequest.builder()
                      .withTerm(termBefore + 100)
                      .withCandidate(id3)
                      .withLastLogIndex(Long.MAX_VALUE / 2)
                      .withLastLogTerm(termBefore + 100)
                      .build())
              .orTimeout(10, TimeUnit.SECONDS)
              .join();

      // then
      assertThat(response.status()).isEqualTo(Status.ERROR);
      assertThat(response.error().type()).isEqualTo(RaftError.Type.FENCED);
      assertThat(m1.getTerm()).isEqualTo(termBefore);
    }

    @Test
    void shouldAdmitFencedMemberThatJoinsWithNewIncarnation() throws IOException {
      // given
      final var cluster = bootstrapThreeVoters();
      final var m3 = cluster.get(2);
      final var leader = fenceMember3(cluster);
      final var fenced = new CompletableFuture<Long>();
      m3.addFencedListener(fenced::complete);
      fenced.orTimeout(30, TimeUnit.SECONDS).join();

      // when - member 3 deletes its data and joins with a new incarnation
      m3.shutdown().join();
      servers.remove(m3);
      FileUtil.deleteFolderIfExists(tmp.resolve(id3.id()));
      final var rejoined = createServer(id3, id1, id2);
      rejoined
          .join(Type.PASSIVE, List.of(id1, id2), JoinOptions.withIncarnation(77L))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();

      // then
      final var configuration = configurationOf(leader);
      assertThat(configuration.newMembers())
          .filteredOn(member -> member.memberId().equals(id3))
          .extracting(RaftMember::getType, RaftMember::getIncarnation)
          .containsExactly(tuple(Type.PASSIVE, 77L));
      assertThat(configuration.fenced()).containsExactly(new FenceEntry(id3, 0));
      final var index = appendEntry(leader);
      Awaitility.await("rejoined member receives entries")
          .untilAsserted(() -> assertThat(commitIndex(rejoined)).isGreaterThanOrEqualTo(index));
    }

    @Test
    void shouldRejectJoinWithFencedIncarnation() throws IOException {
      // given
      final var cluster = bootstrapThreeVoters();
      final var m3 = cluster.get(2);
      fenceMember3(cluster);
      m3.shutdown().join();
      servers.remove(m3);
      FileUtil.deleteFolderIfExists(tmp.resolve(id3.id()));
      final var rejoined = createServer(id3, id1, id2);
      final var fenced = new CompletableFuture<Long>();
      rejoined.addFencedListener(fenced::complete);

      // when - it joins again with the fenced incarnation
      final var join =
          rejoined.join(Type.PASSIVE, List.of(id1, id2), JoinOptions.withIncarnation(0L));

      // then
      assertThat(fenced.orTimeout(30, TimeUnit.SECONDS).join()).isZero();
      assertThatThrownBy(() -> join.orTimeout(30, TimeUnit.SECONDS).join())
          .hasMessageContaining("fenced");
    }

    @Test
    void shouldNotifyFencedMemberThatRestartsWithOldData() {
      // given - member 3 is down while it is fenced
      final var cluster = bootstrapThreeVoters();
      final var m3 = cluster.get(2);
      Awaitility.await("member 3 is not the leader")
          .until(
              () -> {
                final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
                if (leader == m3) {
                  m3.stepDown().join();
                  return false;
                }
                return true;
              });
      m3.shutdown().join();
      servers.remove(m3);
      final var leader = awaitLeader(cluster.get(0), cluster.get(1));
      final var current = configurationId(leader);
      assertThat(
              leader
                  .reconfigure(
                      Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.PASSIVE),
                      List.of(new FenceEntry(id3, 0)),
                      null,
                      current.index(),
                      current.term())
                  .orTimeout(10, TimeUnit.SECONDS)
                  .join()
                  .status())
          .isEqualTo(Status.OK);

      // when - it restarts with its old data, in which it is a voter
      final var restarted = createServer(id3, id1, id2);
      final var fenced = new CompletableFuture<Long>();
      restarted.addFencedListener(fenced::complete);
      restarted.bootstrap(id1, id2, id3);

      // then
      assertThat(fenced.orTimeout(30, TimeUnit.SECONDS).join()).isZero();
      assertThat(restarted.getRole()).isEqualTo(Role.INACTIVE);
      appendEntry(awaitLeader(cluster.get(0), cluster.get(1)));
    }
  }

  @Nested
  final class JoinAdmissionHook {

    private List<RaftServer> bootstrapTwoVoters(final JoinAdmission admission) {
      final var members = Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE);
      final var m1 = createServer(id1, id2, id3, id4);
      final var m2 = createServer(id2, id1, id3, id4);
      m1.setJoinAdmission(admission);
      m2.setJoinAdmission(admission);
      CompletableFuture.allOf(m1.bootstrap(members), m2.bootstrap(members))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();
      return List.of(m1, m2);
    }

    @Test
    void shouldRetryRefusedJoinUntilAdmitted() {
      // given
      final var decision = new AtomicReference<>(JoinAdmission.Decision.NOT_A_MEMBER);
      final var checks = new java.util.concurrent.ConcurrentLinkedQueue<Long>();
      final var cluster =
          bootstrapTwoVoters(
              (member, version) -> {
                checks.add(version);
                return decision.get();
              });
      final var m3 = createServer(id3, id1, id2);

      // when
      final var join =
          m3.join(
              Type.PASSIVE,
              List.of(id1, id2),
              JoinOptions.withIncarnation(5).withConfigurationVersion(42));
      Awaitility.await("the join was refused").until(() -> !checks.isEmpty());
      assertThat(join).isNotDone();
      decision.set(JoinAdmission.Decision.ADMIT);

      // then
      join.orTimeout(30, TimeUnit.SECONDS).join();
      assertThat(checks).contains(42L);
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      assertThat(memberTypes(leader)).containsEntry(id3, Type.PASSIVE);
    }

    @Test
    void shouldFailJoinThatIsNeverAdmitted() {
      // given
      bootstrapTwoVoters((member, version) -> JoinAdmission.Decision.NOT_READY);
      final var m3 =
          createServer(
              id3, config -> config.setConfigurationChangeTimeout(Duration.ofSeconds(3)), id1, id2);

      // when - then
      assertThatThrownBy(
              () ->
                  m3.join(Type.PASSIVE, List.of(id1, id2), JoinOptions.withIncarnation(5))
                      .orTimeout(30, TimeUnit.SECONDS)
                      .join())
          .hasMessageContaining("Not ready");
    }

    @Test
    void shouldRefuseNewMemberOfTargetConfiguration() {
      // given
      final var cluster =
          bootstrapTwoVoters(
              (member, version) ->
                  member.equals(id4)
                      ? JoinAdmission.Decision.NOT_A_MEMBER
                      : JoinAdmission.Decision.ADMIT);
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      final var current = configurationId(leader);

      // when
      final var response =
          Awaitility.await("leader answers with a final response")
              .until(
                  () ->
                      leader
                          .reconfigure(
                              Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id4, Type.PASSIVE),
                              current.index(),
                              current.term())
                          .orTimeout(10, TimeUnit.SECONDS)
                          .join(),
                  r -> r.error() != null && r.error().type() != RaftError.Type.CONFIGURATION_ERROR);

      // then
      assertThat(response.error().type()).isEqualTo(RaftError.Type.NOT_A_MEMBER);
      assertThat(response.index()).isEqualTo(current.index());
      assertThat(configurationId(leader)).isEqualTo(current);
    }
  }

  @Nested
  final class VoterPolicyInConfiguration {
    private final MemberId a0 = MemberId.from("a_0");
    private final MemberId a1 = MemberId.from("a_1");
    private final MemberId b0 = MemberId.from("b_0");
    private final VoterPolicy oneVoterPerZone =
        VoterPolicy.of(new ZoneVoters("a", 1), new ZoneVoters("b", 1));

    private List<RaftServer> bootstrapWithPolicy() {
      final var members = Map.of(a0, Type.ACTIVE, b0, Type.ACTIVE, a1, Type.PASSIVE);
      final var servers =
          List.of(createServer(a0, b0, a1), createServer(b0, a0, a1), createServer(a1, a0, b0));
      CompletableFuture.allOf(
              servers.stream()
                  .map(server -> server.bootstrap(members, oneVoterPerZone))
                  .toArray(CompletableFuture[]::new))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();
      return servers;
    }

    private Configuration configurationOf(final RaftServer server) {
      return onRaftThread(server, () -> server.getContext().getCluster().getConfiguration());
    }

    @Test
    void shouldAppendInitialConfigurationWithPolicyAsFirstLeader() {
      // when
      final var cluster = bootstrapWithPolicy();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));

      // then - every member learns a configuration with an index above 0 that has the policy
      Awaitility.await("all members have the first configuration entry")
          .untilAsserted(
              () ->
                  cluster.forEach(
                      server -> {
                        final var configuration = configurationOf(server);
                        assertThat(configuration.index()).isPositive();
                        assertThat(configuration.voterPolicy()).isEqualTo(oneVoterPerZone);
                      }));
      assertThat(memberTypes(leader))
          .isEqualTo(Map.of(a0, Type.ACTIVE, b0, Type.ACTIVE, a1, Type.PASSIVE));
    }

    @Test
    void shouldNotAppendConfigurationEntryWithoutPolicy() {
      // when
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      appendEntry(leader);

      // then
      assertThat(configurationOf(leader).index()).isZero();
    }

    @Test
    void shouldRejectPromotionBeyondPolicy() {
      // given
      final var cluster = bootstrapWithPolicy();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      appendEntry(leader);

      // when
      final var response =
          Awaitility.await("leader answers with a final response")
              .until(
                  () -> {
                    final var current = configurationId(leader);
                    return leader
                        .reconfigure(
                            Map.of(a0, Type.ACTIVE, b0, Type.ACTIVE, a1, Type.ACTIVE),
                            current.index(),
                            current.term())
                        .orTimeout(10, TimeUnit.SECONDS)
                        .join();
                  },
                  r -> r.error() == null || r.error().message().contains("voter policy"));

      // then
      assertThat(response.status()).isEqualTo(Status.ERROR);
      assertThat(memberTypes(leader)).containsEntry(a1, Type.PASSIVE);
    }

    @Test
    void shouldPromoteAfterPolicyUpdate() {
      // given
      final var cluster = bootstrapWithPolicy();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      appendEntry(leader);
      final var twoVotersInA = VoterPolicy.of(new ZoneVoters("a", 2), new ZoneVoters("b", 1));

      // when - the policy update is a configuration change of its own
      Awaitility.await("policy is updated")
          .until(
              () -> {
                final var current = configurationId(leader);
                return leader
                    .reconfigure(
                        Map.of(a0, Type.ACTIVE, b0, Type.ACTIVE, a1, Type.PASSIVE),
                        List.of(),
                        twoVotersInA,
                        current.index(),
                        current.term())
                    .orTimeout(10, TimeUnit.SECONDS)
                    .join();
              },
              r -> r.status() == Status.OK);
      awaitReconfigured(leader, leader, Map.of(a0, Type.ACTIVE, b0, Type.ACTIVE, a1, Type.ACTIVE));

      // then
      assertThat(configurationOf(leader).voterPolicy()).isEqualTo(twoVotersInA);
      assertThat(memberTypes(leader)).containsEntry(a1, Type.ACTIVE);
    }
  }

  @Nested
  final class EmptyStartGuard {

    private static boolean guarded(final RaftServer server) {
      return server.isEmptyStartGuardActive().orTimeout(5, TimeUnit.SECONDS).join();
    }

    private List<RaftServer> bootstrapTwoVoters() {
      final var members = Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE);
      final var m1 = createServer(id1, id2, id3);
      final var m2 = createServer(id2, id1, id3);
      CompletableFuture.allOf(m1.bootstrap(members), m2.bootstrap(members))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();
      return List.of(m1, m2);
    }

    @Test
    void shouldReleaseGuardOnceCaughtUpWithCurrentLeader() {
      // given
      final var cluster = bootstrapTwoVoters();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      final var index = appendEntry(leader);
      final var m3 = createServer(id3, id1, id2);

      // when
      m3.join(
              Type.PASSIVE,
              List.of(id1, id2),
              JoinOptions.withIncarnation(3).withEmptyStartGuard(leader.getTerm()))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();

      // then
      Awaitility.await("guard is released").untilAsserted(() -> assertThat(guarded(m3)).isFalse());
      assertThat(commitIndex(m3)).isGreaterThanOrEqualTo(index);
    }

    @Test
    void shouldNotGrantPollsWhileGuarded() {
      // given - the probe saw a higher term than any current leader has
      final var cluster = bootstrapTwoVoters();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      final var m3 = createServer(id3, id1, id2);
      m3.join(
              Type.PASSIVE,
              List.of(id1, id2),
              JoinOptions.withIncarnation(3).withEmptyStartGuard(leader.getTerm() + 100))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();
      appendEntry(leader);

      // when - a candidate with an up-to-date log asks for a vote
      final var protocol = (TestRaftServerProtocol) leader.getContext().getProtocol();
      final var response =
          protocol
              .poll(
                  id3,
                  PollRequest.builder()
                      .withTerm(leader.getTerm())
                      .withCandidate(leader.getContext().getCluster().getLocalMember().memberId())
                      .withLastLogIndex(Long.MAX_VALUE / 2)
                      .withLastLogTerm(leader.getTerm())
                      .build())
              .orTimeout(10, TimeUnit.SECONDS)
              .join();

      // then
      assertThat(guarded(m3)).isTrue();
      assertThat(response.accepted()).isFalse();
    }

    @Test
    void shouldNotHelpElectLeaderWhileGuarded() throws IOException {
      // given - three voters, member 3 lost its disk and joins again with the guard
      final var members = Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE);
      final var m1 = createServer(id1, id2, id3);
      final var m2 = createServer(id2, id1, id3);
      final var m3 = createServer(id3, id1, id2);
      CompletableFuture.allOf(m1.bootstrap(members), m2.bootstrap(members), m3.bootstrap(members))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();
      Awaitility.await("member 1 is the leader")
          .until(
              () -> {
                final var leader = awaitLeader(m1, m2, m3);
                if (leader != m1) {
                  leader.stepDown().join();
                  return false;
                }
                return true;
              });
      appendEntry(m1);
      m3.shutdown().join();
      servers.remove(m3);
      FileUtil.deleteFolderIfExists(tmp.resolve(id3.id()));
      final var rejoined = createServer(id3, id1, id2);
      rejoined
          .join(
              Type.PASSIVE,
              List.of(id1, id2),
              JoinOptions.withIncarnation(33).withEmptyStartGuard(m1.getTerm() + 100))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();
      Awaitility.await("rejoined member stays a voter")
          .untilAsserted(() -> assertThat(rejoined.getRole()).isEqualTo(Role.FOLLOWER));

      // when - the leader goes down; member 2 needs the vote of member 3
      m1.shutdown().join();

      // then - no leader is elected while member 3 is guarded
      Awaitility.await("no leader while guarded")
          .during(Duration.ofSeconds(3))
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(leaderOf(m2, rejoined)).isEmpty());
    }
  }

  @Nested
  final class VotersAndConfigurationView {

    @Test
    void shouldNotAnointPassiveMember() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      awaitLeader(cluster.toArray(RaftServer[]::new));
      final var m3 = cluster.get(2);

      // when - then
      assertThatThrownBy(() -> m3.anoint().orTimeout(10, TimeUnit.SECONDS).join())
          .hasMessageContaining("not ACTIVE");
      assertThat(m3.getRole()).isEqualTo(Role.PASSIVE);
    }

    @Test
    void shouldNotPromoteMemberThatDidNotAnswerRecently() {
      // given - the passive member caught up, then went down
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      final var m3 = cluster.get(2);
      final var index = appendEntry(leader);
      Awaitility.await("passive member caught up")
          .untilAsserted(() -> assertThat(commitIndex(m3)).isGreaterThanOrEqualTo(index));
      m3.shutdown().join();

      // when - promoted after more than an election timeout without an answer
      final var response =
          Awaitility.await("leader answers after an election timeout")
              .pollDelay(Duration.ofSeconds(1))
              .until(
                  () -> {
                    final var current = configurationId(leader);
                    return leader
                        .reconfigure(
                            Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE),
                            current.index(),
                            current.term())
                        .orTimeout(10, TimeUnit.SECONDS)
                        .join();
                  },
                  r -> r.error() == null || r.error().message().contains("not caught up"));

      // then
      assertThat(response.status()).isEqualTo(Status.ERROR);
      assertThat(response.error().message()).contains("did not answer recently");
    }

    @Test
    void shouldNotifyConfigurationListenerAndReadTypedConfiguration() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      final var m3 = cluster.get(2);
      final var committed = new java.util.concurrent.ConcurrentLinkedQueue<Configuration>();
      m3.addConfigurationListener(
          (configuration, isCommitted) -> {
            if (isCommitted) {
              committed.add(configuration);
            }
          });
      final var target =
          Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.PASSIVE, id4, Type.PASSIVE);

      // when
      awaitReconfigured(leader, leader, target);

      // then
      Awaitility.await("passive member is notified of the committed configuration")
          .untilAsserted(
              () ->
                  assertThat(committed)
                      .anySatisfy(
                          configuration ->
                              assertThat(configuration.newMembers()).hasSize(target.size())));
      final var view = leader.getConfiguration().orTimeout(5, TimeUnit.SECONDS).join();
      assertThat(view.index()).isEqualTo(configurationId(leader).index());
      assertThat(view.term()).isEqualTo(configurationId(leader).term());
      assertThat(view.requiresJointConsensus()).isFalse();
      assertThat(view.newMembers())
          .extracting(RaftMember::memberId, RaftMember::getType)
          .containsExactlyInAnyOrder(
              tuple(id1, Type.ACTIVE),
              tuple(id2, Type.ACTIVE),
              tuple(id3, Type.PASSIVE),
              tuple(id4, Type.PASSIVE));
    }
  }
}
