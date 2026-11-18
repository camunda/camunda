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
import io.atomix.raft.cluster.MembershipChange;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.MemberReport;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision;
import io.atomix.raft.partition.systempartition.VoterPolicy;
import io.atomix.raft.partition.systempartition.VoterPolicy.ZoneVoters;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.JoinRequest;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.TestRaftProtocolFactory;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.protocol.VersionedAppendRequest;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.raft.snapshot.TestSnapshotStore;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.system.Configuration;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the Raft mechanisms that the system partition needs (design revision 7, 5.8): a partition
 * replicated on every broker, in which only some members are voters ({@link Type#ACTIVE}) and the
 * others are {@link Type#PASSIVE}, whose members only the leader changes, and in which a member
 * without data starts without a configuration. Servers are created with the {@code systemPartition}
 * option (R20) unless a test says otherwise.
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

  private RaftServer createDataPartitionServer(final MemberId id, final MemberId... others) {
    return createServer(id, config -> config.setSystemPartition(false), others);
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
    partitionConfig.setSystemPartition(true);
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

  private void stop(final RaftServer server) {
    server.shutdown().join();
    servers.remove(server);
  }

  private void loseDisk(final RaftServer server, final MemberId id) throws IOException {
    stop(server);
    FileUtil.deleteFolderIfExists(tmp.resolve(id.id()));
  }

  private static Optional<RaftServer> leaderOf(final RaftServer... servers) {
    return Arrays.stream(servers).filter(RaftServer::isLeader).findAny();
  }

  private static RaftServer awaitLeader(final RaftServer... servers) {
    return Awaitility.await("a leader is elected")
        .until(() -> leaderOf(servers), Optional::isPresent)
        .orElseThrow();
  }

  private static RaftServer awaitLeaderIs(final RaftServer expected, final RaftServer... servers) {
    Awaitility.await("the expected member is the leader")
        .until(
            () -> {
              final var leader = awaitLeader(servers);
              if (leader != expected) {
                leader.stepDown().join();
                return false;
              }
              return true;
            });
    return expected;
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

  private static Configuration configurationOf(final RaftServer server) {
    return onRaftThread(server, () -> server.getContext().getCluster().getConfiguration());
  }

  private static <T> T onRaftThread(final RaftServer server, final Supplier<T> supplier) {
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
    return appendEntryAsync(leader).orTimeout(10, TimeUnit.SECONDS).join();
  }

  private static CompletableFuture<Long> appendEntryAsync(final RaftServer leader) {
    final var result = new AppendResult();
    final RaftContext context = leader.getContext();
    context
        .getThreadContext()
        .execute(
            () ->
                ((LeaderRole) context.getRaftRole())
                    .appendEntry(-1, -1, ByteBuffer.wrap(new byte[0]), result));
    return result.commit();
  }

  private static long configurationIndex(final RaftServer server) {
    return onRaftThread(server, () -> server.getContext().getCluster().getConfiguration().index());
  }

  private static RaftMember member(final MemberId id, final Type type) {
    return member(id, type, 0);
  }

  private static RaftMember member(final MemberId id, final Type type, final long incarnation) {
    return new DefaultRaftMember(id, type, Instant.now(), incarnation);
  }

  /** Retries the change on the leader until it accepts it, e.g. once a member caught up. */
  private static ReconfigureResponse awaitChanged(
      final RaftServer leader,
      final List<RaftMember> members,
      final List<FenceEntry> fenced,
      final long membershipVersion) {
    return Awaitility.await("the leader changes the members to %s".formatted(members))
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              try {
                return leader
                    .changeMembers(
                        new MembershipChange(
                            members, fenced, membershipVersion, configurationIndex(leader)))
                    .get(10, TimeUnit.SECONDS);
              } catch (final Exception e) {
                return null;
              }
            },
            response -> response != null && response.status() == Status.OK);
  }

  private static ReconfigureResponse awaitChanged(
      final RaftServer leader, final RaftMember... members) {
    return awaitChanged(leader, List.of(members), List.of(), 0);
  }

  private List<RaftServer> bootstrap(final Map<MemberId, Type> members) {
    final var ids = members.keySet().toArray(MemberId[]::new);
    final var created = new ArrayList<RaftServer>();
    for (final var id : ids) {
      created.add(
          createServer(
              id, Arrays.stream(ids).filter(other -> !other.equals(id)).toArray(MemberId[]::new)));
    }
    CompletableFuture.allOf(
            created.stream()
                .map(server -> server.bootstrap(members))
                .toArray(CompletableFuture[]::new))
        .orTimeout(30, TimeUnit.SECONDS)
        .join();
    created.sort(
        (a, b) ->
            MemberId.ID_COMPARATOR.compare(
                a.cluster().getLocalMember().memberId(), b.cluster().getLocalMember().memberId()));
    return created;
  }

  private List<RaftServer> bootstrapTwoVotersOnePassive() {
    return bootstrap(twoVotersOnePassive());
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

      // when
      final var cluster = bootstrap(members);

      // then
      final var m3 = cluster.get(2);
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      assertThat(leader).isNotSameAs(m3);
      assertThat(m3.getRole()).isEqualTo(Role.PASSIVE);
      assertThat(memberTypes(cluster.get(0))).isEqualTo(members);
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
      final var cluster = bootstrap(members);

      // when - restart the passive member with an untyped bootstrap, which would make all ACTIVE
      stop(cluster.get(2));
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

    @Test
    void shouldAppendFirstConfigurationEntryAsFirstLeader() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();

      // when
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));

      // then - a configuration index above 0 shows that the system partition exists (R14)
      Awaitility.await("the first configuration entry is committed")
          .untilAsserted(
              () ->
                  assertThat(cluster)
                      .allSatisfy(server -> assertThat(configurationIndex(server)).isPositive()));
      assertThat(configurationOf(leader).newMembers())
          .extracting(RaftMember::memberId, RaftMember::getType)
          .containsExactlyInAnyOrder(
              tuple(id1, Type.ACTIVE), tuple(id2, Type.ACTIVE), tuple(id3, Type.PASSIVE));
    }

    @Test
    void shouldNotAppendConfigurationEntryInDataPartition() {
      // given
      final var members = Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE);
      final var m1 = createDataPartitionServer(id1, id2);
      final var m2 = createDataPartitionServer(id2, id1);
      CompletableFuture.allOf(m1.bootstrap(members), m2.bootstrap(members))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();

      // when
      appendEntry(awaitLeader(m1, m2));

      // then - data partitions keep their log (R20)
      assertThat(configurationIndex(m1)).isZero();
      assertThat(configurationIndex(m2)).isZero();
    }
  }

  @Nested
  final class LeaderChangesMembers {

    @Test
    void shouldPromoteCaughtUpPassiveMember() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var m3 = cluster.get(2);
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      appendEntry(leader);

      // when
      awaitChanged(
          leader, member(id1, Type.ACTIVE), member(id2, Type.ACTIVE), member(id3, Type.ACTIVE));

      // then
      final var target = Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE);
      Awaitility.await("all members see the promotion")
          .untilAsserted(
              () -> cluster.forEach(server -> assertThat(memberTypes(server)).isEqualTo(target)));
      Awaitility.await("the promoted member is a voter")
          .untilAsserted(
              () -> assertThat(m3.getRole()).isIn(Role.FOLLOWER, Role.CANDIDATE, Role.LEADER));
    }

    @Test
    void shouldStoreMembershipVersion() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));

      // when
      awaitChanged(
          leader,
          List.of(
              member(id1, Type.ACTIVE),
              member(id2, Type.ACTIVE),
              member(id3, Type.PASSIVE),
              member(id4, Type.PASSIVE, 44)),
          List.of(),
          17);

      // then - all members store it, Raft does not interpret it (R19)
      Awaitility.await("all members have the membership version")
          .untilAsserted(
              () ->
                  assertThat(cluster)
                      .allSatisfy(
                          server ->
                              assertThat(configurationOf(server).membershipVersion())
                                  .isEqualTo(17)));
      assertThat(configurationOf(leader).newMembers())
          .filteredOn(m -> m.memberId().equals(id4))
          .extracting(RaftMember::getIncarnation)
          .containsExactly(44L);
    }

    @Test
    void shouldRejectChangeOnFollower() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      final var follower = cluster.stream().filter(s -> s != leader).findFirst().orElseThrow();

      // when - then
      assertThatThrownBy(
              () ->
                  follower
                      .changeMembers(
                          new MembershipChange(
                              List.of(member(id1, Type.ACTIVE), member(id2, Type.ACTIVE)),
                              List.of(),
                              0,
                              configurationIndex(follower)))
                      .orTimeout(10, TimeUnit.SECONDS)
                      .join())
          .hasRootCauseInstanceOf(RaftException.NoLeader.class);
    }

    @Test
    void shouldRejectTargetConfigurationOfOtherMember() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeaderIs(cluster.get(0), cluster.toArray(RaftServer[]::new));
      appendEntry(leader);

      // when - the passive member sends a target configuration that promotes it
      final var response =
          ((TestRaftServerProtocol) cluster.get(2).getContext().getProtocol())
              .reconfigure(
                  id1,
                  ReconfigureRequest.builder()
                      .withIndex(configurationIndex(leader))
                      .withTerm(leader.getTerm())
                      .withMembers(
                          List.of(
                              member(id1, Type.ACTIVE),
                              member(id2, Type.ACTIVE),
                              member(id3, Type.ACTIVE)))
                      .from(id3.id())
                      .build())
              .orTimeout(10, TimeUnit.SECONDS)
              .join();

      // then - only the leader changes the members (R3)
      assertThat(response.status()).isEqualTo(Status.ERROR);
      assertThat(response.error().message()).contains("Only the leader changes the members");
      assertThat(memberTypes(leader)).isEqualTo(twoVotersOnePassive());
    }

    @Test
    void shouldRejectJoinOfOtherMember() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeaderIs(cluster.get(0), cluster.toArray(RaftServer[]::new));
      appendEntry(leader);

      // when - a broker asks to join, as a data partition member would
      final var response =
          ((TestRaftServerProtocol) cluster.get(2).getContext().getProtocol())
              .join(
                  id1,
                  JoinRequest.builder().withJoiningMember(member(id4, Type.PASSIVE, 4)).build())
              .orTimeout(10, TimeUnit.SECONDS)
              .join();

      // then
      assertThat(response.status()).isEqualTo(Status.ERROR);
      assertThat(memberTypes(leader)).doesNotContainKey(id4);
    }

    @Test
    void shouldRejectStaleExpectedIndex() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      appendEntry(leader);
      final var before = configurationIndex(leader);
      awaitChanged(
          leader,
          member(id1, Type.ACTIVE),
          member(id2, Type.ACTIVE),
          member(id3, Type.PASSIVE),
          member(id4, Type.PASSIVE, 4));

      // when - a target calculated from the old configuration
      final var response =
          leader
              .changeMembers(
                  new MembershipChange(
                      List.of(
                          member(id1, Type.ACTIVE),
                          member(id2, Type.ACTIVE),
                          member(id3, Type.PASSIVE)),
                      List.of(),
                      0,
                      before))
              .orTimeout(10, TimeUnit.SECONDS)
              .join();

      // then
      assertThat(response.status()).isEqualTo(Status.ERROR);
      assertThat(response.error().type()).isEqualTo(RaftError.Type.CONFIGURATION_ERROR);
      assertThat(memberTypes(leader)).containsKey(id4);
    }

    @Test
    void shouldAnswerOkWhenTargetIsAlreadyTheConfiguration() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      appendEntry(leader);
      final var index = configurationIndex(leader);

      // when
      final var response =
          awaitChanged(
              leader,
              member(id1, Type.ACTIVE),
              member(id2, Type.ACTIVE),
              member(id3, Type.PASSIVE));

      // then
      assertThat(response.index()).isEqualTo(index);
      assertThat(configurationIndex(leader)).isEqualTo(index);
    }

    @Test
    void shouldRejectNewActiveMember() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      appendEntry(leader);

      // when
      final var response =
          leader
              .changeMembers(
                  new MembershipChange(
                      List.of(
                          member(id1, Type.ACTIVE),
                          member(id2, Type.ACTIVE),
                          member(id3, Type.PASSIVE),
                          member(id4, Type.ACTIVE, 4)),
                      List.of(),
                      0,
                      configurationIndex(leader)))
              .orTimeout(10, TimeUnit.SECONDS)
              .join();

      // then
      assertThat(response.status()).isEqualTo(Status.ERROR);
      assertThat(response.error().message()).contains("a new member must not be ACTIVE");
    }

    @Test
    void shouldReadLeaderConfigurationThroughPassiveMember() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      appendEntry(leader);
      awaitChanged(
          leader,
          List.of(
              member(id1, Type.ACTIVE),
              member(id2, Type.ACTIVE),
              member(id3, Type.PASSIVE),
              member(id4, Type.PASSIVE, 4)),
          List.of(),
          3);

      // when - the passive member forwards the read to the leader (R3)
      final var read =
          cluster.get(2).readLeaderConfiguration().orTimeout(10, TimeUnit.SECONDS).join();

      // then
      assertThat(read.leader()).isEqualTo(leader.cluster().getLocalMember().memberId());
      assertThat(read.isCommittedAndNotJoint()).isTrue();
      assertThat(read.configuration().index()).isEqualTo(configurationIndex(leader));
      assertThat(read.configuration().membershipVersion()).isEqualTo(3);
      assertThat(read.configuration().newMembers())
          .extracting(RaftMember::memberId)
          .containsExactlyInAnyOrder(id1, id2, id3, id4);
    }
  }

  @Nested
  final class PromotionCheck {

    /** Two voters and a passive member that caught up, then went down. */
    private RaftServer bootstrapWithDownPassiveMember(final boolean systemPartition) {
      final var members = twoVotersOnePassive();
      final var created = new ArrayList<RaftServer>();
      for (final var id : List.of(id1, id2, id3)) {
        created.add(
            createServer(
                id,
                config -> config.setSystemPartition(systemPartition),
                List.of(id1, id2, id3).stream()
                    .filter(o -> !o.equals(id))
                    .toArray(MemberId[]::new)));
      }
      CompletableFuture.allOf(
              created.stream().map(s -> s.bootstrap(members)).toArray(CompletableFuture[]::new))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();
      final var leader = awaitLeaderIs(created.get(0), created.toArray(RaftServer[]::new));
      final var index = appendEntry(leader);
      Awaitility.await("passive member caught up")
          .untilAsserted(
              () -> assertThat(commitIndex(created.get(2))).isGreaterThanOrEqualTo(index));
      stop(created.get(2));
      return leader;
    }

    /** Promotes member 3 once more than an election timeout passed since it went down. */
    private ReconfigureResponse promoteMember3Later(final RaftServer leader) {
      return Awaitility.await("leader answers after an election timeout")
          .pollDelay(Duration.ofSeconds(1))
          .until(
              () ->
                  leader
                      .changeMembers(
                          new MembershipChange(
                              List.of(
                                  member(id1, Type.ACTIVE),
                                  member(id2, Type.ACTIVE),
                                  member(id3, Type.ACTIVE)),
                              List.of(),
                              0,
                              configurationIndex(leader)))
                      .orTimeout(10, TimeUnit.SECONDS)
                      .join(),
              r -> r.error() == null || !r.error().message().contains("Another configuration"));
    }

    @Test
    void shouldNotPromoteMemberThatDidNotAnswerRecently() {
      // given
      final var leader = bootstrapWithDownPassiveMember(true);

      // when
      final var response = promoteMember3Later(leader);

      // then - member 3 is caught up, but it did not answer (R9)
      assertThat(response.status()).isEqualTo(Status.ERROR);
      assertThat(response.error().message())
          .contains("did not answer within the election timeout")
          .doesNotContain("not caught up");
    }

    @Test
    void shouldPromoteMemberThatDidNotAnswerRecentlyInDataPartition() {
      // given - a data partition keeps its behavior (R20)
      final var leader = bootstrapWithDownPassiveMember(false);

      // when
      final var response = promoteMember3Later(leader);

      // then
      assertThat(response.status()).isEqualTo(Status.OK);
    }

    @Test
    void shouldPromotePassiveMemberUnderConstantLoadWithLagThreshold() {
      // given - a passive member gets only committed entries, thus it lags a little under load
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeaderIs(cluster.get(0), cluster.toArray(RaftServer[]::new));
      appendEntry(leader);
      final var load = new AtomicBoolean(true);
      final var loadThread =
          new Thread(
              () -> {
                while (load.get()) {
                  try {
                    appendEntryAsync(leader).get(5, TimeUnit.SECONDS);
                  } catch (final Exception e) {
                    return;
                  }
                }
              });
      loadThread.start();

      try {
        // when
        final var response =
            awaitChanged(
                leader,
                member(id1, Type.ACTIVE),
                member(id2, Type.ACTIVE),
                member(id3, Type.ACTIVE));

        // then
        assertThat(response.status()).isEqualTo(Status.OK);
      } finally {
        load.set(false);
      }
    }
  }

  @Nested
  final class ForceConfigure {

    private List<RaftServer> bootstrapThreeVotersOnePassive() {
      final var cluster =
          bootstrap(
              Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE, id4, Type.PASSIVE));
      appendEntry(awaitLeader(cluster.get(0), cluster.get(1), cluster.get(2)));
      return cluster;
    }

    @Test
    void shouldNotWaitForUnreachablePassiveMember() {
      // given - voter 2 and passive member 4 are down
      final var cluster = bootstrapThreeVotersOnePassive();
      final var m1 = cluster.get(0);
      final var m3 = cluster.get(2);
      stop(cluster.get(1));
      stop(cluster.get(3));

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
      stop(cluster.get(0));
      stop(cluster.get(1));

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
      stop(cluster.get(1));

      // when - force a configuration in which the unreachable member is a voter
      final var target =
          Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.PASSIVE, id4, Type.PASSIVE);

      // then
      assertThatThrownBy(() -> m1.forceConfigure(target).orTimeout(30, TimeUnit.SECONDS).join())
          .hasRootCauseInstanceOf(RaftException.ProtocolException.class);
    }

    @Test
    void shouldKeepEntryThatIsCommittedButNoSurvivorKnowsAsCommitted() {
      // given - four voters, member 1 leads; member 4 is cut off
      final var cluster =
          bootstrap(Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE, id4, Type.ACTIVE));
      final var m1 = awaitLeaderIs(cluster.get(0), cluster.toArray(RaftServer[]::new));
      final var m2 = cluster.get(1);
      final var m3 = cluster.get(2);
      appendEntry(m1);
      protocolFactory.partition(id4);
      final var commitBefore = commitIndex(m1);
      // members 2 and 3 never learn a commit index above the one before the entry
      ((TestRaftServerProtocol) m1.getContext().getProtocol())
          .interceptDelivery(
              VersionedAppendRequest.class,
              (receiver, request) ->
                  (receiver.equals(id2) || receiver.equals(id3))
                          && request.commitIndex() > commitBefore
                      ? CompletableFuture.failedFuture(new ConnectException("blocked"))
                      : CompletableFuture.completedFuture(null));

      // when - the entry commits on 1, 2 and 3, then 1 and 4 fail
      final var entry = appendEntry(m1);
      assertThat(commitIndex(m2)).isLessThan(entry);
      assertThat(commitIndex(m3)).isLessThan(entry);
      stop(m1);
      stop(cluster.get(3));

      // and phase 0 removes them with a forced change; the survivors are a blocking set (D20)
      final var reports = List.of(report(m2), report(m3));
      final var decision =
          SystemPartitionRecovery.removeMembers(
              Set.of(id1, id4), reports, VoterPolicy.of(new ZoneVoters("", 4)), false);
      assertThat(decision).isInstanceOf(RecoveryDecision.ForcedChange.class);
      final var forced = (RecoveryDecision.ForcedChange) decision;
      assertThat(forced.completeness().complete()).isTrue();
      m2.forceConfigure(forced.target()).orTimeout(30, TimeUnit.SECONDS).join();

      // then - the new leader commits the entry: nothing committed is lost
      final var newLeader = awaitLeader(m2, m3);
      appendEntry(newLeader);
      assertThat(commitIndex(m2)).isGreaterThanOrEqualTo(entry);
      assertThat(commitIndex(m3)).isGreaterThanOrEqualTo(entry);
      assertThat(memberTypes(newLeader))
          .containsExactlyInAnyOrderEntriesOf(Map.of(id2, Type.ACTIVE, id3, Type.ACTIVE));
      assertThat(configurationOf(newLeader).fenced())
          .containsExactlyInAnyOrder(new FenceEntry(id1, 0), new FenceEntry(id4, 0));
    }

    private MemberReport report(final RaftServer server) {
      final var state = server.getReplicaState().orTimeout(5, TimeUnit.SECONDS).join();
      return new MemberReport(
          server.cluster().getLocalMember().memberId(),
          state.configuration(),
          state.incarnation(),
          state.lastLogTerm(),
          state.lastLogIndex(),
          state.commitIndex(),
          null,
          0);
    }
  }

  @Nested
  final class StartWithoutConfiguration {

    private RaftServer startWithoutData(final MemberId id, final long incarnation) {
      final var others =
          List.of(id1, id2, id3, id4).stream().filter(o -> !o.equals(id)).toArray(MemberId[]::new);
      final var server = createServer(id, others);
      server.startWithoutConfiguration(incarnation).orTimeout(30, TimeUnit.SECONDS).join();
      return server;
    }

    private ConfigureRequest configureRequest(
        final List<RaftMember> newMembers, final List<RaftMember> oldMembers) {
      return ConfigureRequest.builder()
          .withTerm(1)
          .withLeader(id1)
          .withIndex(5)
          .withTime(System.currentTimeMillis())
          .withNewMembers(new ArrayList<>(newMembers))
          .withOldMembers(new ArrayList<>(oldMembers))
          .build();
    }

    @Test
    void shouldNotVoteOrPollWithoutConfiguration() {
      // given
      final var m3 = startWithoutData(id3, 33);
      final var sender = protocolFactory.newServerProtocol(id1);

      // when
      final var poll =
          sender
              .poll(
                  id3,
                  PollRequest.builder()
                      .withTerm(5)
                      .withCandidate(id1)
                      .withLastLogIndex(10)
                      .withLastLogTerm(5)
                      .build())
              .orTimeout(10, TimeUnit.SECONDS)
              .join();
      final var vote =
          sender
              .vote(
                  id3,
                  VoteRequest.builder()
                      .withTerm(5)
                      .withCandidate(id1)
                      .withLastLogIndex(10)
                      .withLastLogTerm(5)
                      .build())
              .orTimeout(10, TimeUnit.SECONDS)
              .join();

      // then
      assertThat(m3.getRole()).isEqualTo(Role.INACTIVE);
      assertThat(poll.status() == Status.ERROR || !poll.accepted()).isTrue();
      assertThat(vote.status() == Status.ERROR || !vote.voted()).isTrue();
    }

    @Test
    void shouldRejectConfigurationInWhichItIsNotPassiveWithItsIncarnation() {
      // given
      final var m3 = startWithoutData(id3, 33);
      final var sender = protocolFactory.newServerProtocol(id1);
      final var others = List.of(member(id1, Type.ACTIVE), member(id2, Type.ACTIVE));

      // when
      final var asVoter =
          sender
              .configure(
                  id3,
                  configureRequest(
                      List.of(others.get(0), others.get(1), member(id3, Type.ACTIVE, 33)),
                      List.of()))
              .orTimeout(10, TimeUnit.SECONDS)
              .join();
      final var asOldVoter =
          sender
              .configure(
                  id3,
                  configureRequest(
                      List.of(others.get(0), others.get(1), member(id3, Type.ACTIVE, 0)),
                      List.of()))
              .orTimeout(10, TimeUnit.SECONDS)
              .join();
      final var jointReplacement =
          sender
              .configure(
                  id3,
                  configureRequest(
                      List.of(others.get(0), others.get(1), member(id3, Type.PASSIVE, 33)),
                      List.of(others.get(0), others.get(1), member(id3, Type.ACTIVE, 0))))
              .orTimeout(10, TimeUnit.SECONDS)
              .join();

      // then - each rejection carries its incarnation (R16)
      assertThat(List.of(asVoter, asOldVoter, jointReplacement))
          .allSatisfy(
              response -> {
                assertThat(response.status()).isEqualTo(Status.ERROR);
                assertThat(response.senderIncarnation()).isEqualTo(33);
              });
      assertThat(m3.getRole()).isEqualTo(Role.INACTIVE);
      assertThat(configurationOf(m3)).isNull();
    }

    @Test
    void shouldBecomePassiveWhenLeaderAddsItAndStayPassiveAfterRestart() {
      // given
      final var cluster = bootstrap(Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE));
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      final var index = appendEntry(leader);
      final var m3 = startWithoutData(id3, 33);

      // when
      awaitChanged(
          leader,
          member(id1, Type.ACTIVE),
          member(id2, Type.ACTIVE),
          member(id3, Type.PASSIVE, 33));

      // then
      Awaitility.await("member 3 catches up as a passive member")
          .untilAsserted(
              () -> {
                assertThat(m3.getRole()).isEqualTo(Role.PASSIVE);
                assertThat(commitIndex(m3)).isGreaterThanOrEqualTo(index);
              });

      // when - it restarts with its stored configuration
      stop(m3);
      final var restarted = createServer(id3, id1, id2);
      restarted.bootstrap(id1, id2, id3).orTimeout(30, TimeUnit.SECONDS).join();

      // then
      assertThat(restarted.getRole()).isEqualTo(Role.PASSIVE);
      assertThat(restarted.cluster().getLocalMember().getIncarnation()).isEqualTo(33);
    }

    @Test
    void shouldFailToStartWithoutConfigurationWithStoredConfiguration() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      awaitLeader(cluster.toArray(RaftServer[]::new));
      stop(cluster.get(2));
      final var restarted = createServer(id3, id1, id2);

      // when - then
      assertThatThrownBy(
              () -> restarted.startWithoutConfiguration(33).orTimeout(30, TimeUnit.SECONDS).join())
          .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNeverVoteAfterLosingItsDisk() throws IOException {
      // given - three voters; member 1 leads; member 3 loses its disk and starts without data
      final var cluster = bootstrap(Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE));
      final var m1 = awaitLeaderIs(cluster.get(0), cluster.toArray(RaftServer[]::new));
      final var m2 = cluster.get(1);
      appendEntry(m1);
      loseDisk(cluster.get(2), id3);
      final var m3 = startWithoutData(id3, 33);

      // when - the leader goes down; member 2 needs a vote of member 3
      stop(m1);

      // then - member 3 does not vote with its empty log (D15)
      Awaitility.await("no leader without a vote of the old incarnation")
          .during(Duration.ofSeconds(3))
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(leaderOf(m2, m3)).isEmpty());
      assertThat(m3.getRole()).isEqualTo(Role.INACTIVE);
    }
  }

  @Nested
  final class IncarnationsInTheQuorum {

    @Test
    void shouldNotCountNewIncarnationForOldVoterInJointPhaseOfReplacement() throws IOException {
      // given - voters 1, 2 and 3, passive member 4; member 1 leads
      final var cluster =
          bootstrap(
              Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE, id4, Type.PASSIVE));
      final var m1 = awaitLeaderIs(cluster.get(0), cluster.get(0), cluster.get(1), cluster.get(2));
      final var m2 = cluster.get(1);
      final var index = appendEntry(m1);
      Awaitility.await("passive member caught up")
          .untilAsserted(
              () -> assertThat(commitIndex(cluster.get(3))).isGreaterThanOrEqualTo(index));

      // and - member 3 lost its disk and runs with incarnation 33; member 2 is down
      loseDisk(cluster.get(2), id3);
      final var m3 = createServer(id3, id1, id2, id4);
      m3.startWithoutConfiguration(33).orTimeout(30, TimeUnit.SECONDS).join();
      stop(m2);

      // when - the leader replaces 3#0 by 3#33 and promotes member 4 in one change (rule 2)
      final var change =
          m1.changeMembers(
              new MembershipChange(
                  List.of(
                      member(id1, Type.ACTIVE),
                      member(id2, Type.ACTIVE),
                      member(id4, Type.ACTIVE),
                      member(id3, Type.PASSIVE, 33)),
                  List.of(),
                  0,
                  configurationIndex(m1)));

      // then - the joint configuration needs 2 of the old voters {1, 2, 3#0}; the answers of 3#33
      // must not count for 3#0, thus it does not commit while member 2 is down (R17, Q15)
      Awaitility.await("the joint configuration is appended")
          .untilAsserted(() -> assertThat(configurationOf(m1).requiresJointConsensus()).isTrue());
      final var jointIndex = configurationIndex(m1);
      Awaitility.await("the joint configuration does not commit")
          .during(Duration.ofSeconds(2))
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> assertThat(commitIndex(m1)).isLessThan(jointIndex));

      // when - member 2 comes back
      final var restarted2 = createServer(id2, id1, id3, id4);
      restarted2.bootstrap(id1, id2, id3, id4).orTimeout(30, TimeUnit.SECONDS).join();

      // then - the joint phase ends, maybe by a new leader if member 1 stepped down without a
      // quorum of the old voters, and its end fences 3#0 (R18)
      Awaitility.await("the joint phase ends")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                final var configuration = configurationOf(restarted2);
                assertThat(configuration.requiresJointConsensus()).isFalse();
                assertThat(configuration.newMembers())
                    .extracting(
                        RaftMember::memberId, RaftMember::getType, RaftMember::getIncarnation)
                    .containsExactlyInAnyOrder(
                        tuple(id1, Type.ACTIVE, 0L),
                        tuple(id2, Type.ACTIVE, 0L),
                        tuple(id4, Type.ACTIVE, 0L),
                        tuple(id3, Type.PASSIVE, 33L));
                assertThat(configuration.fenced()).containsExactly(new FenceEntry(id3, 0));
              });
      assertThat(change).isDone();
      final var next = appendEntry(awaitLeader(m1, restarted2, cluster.get(3)));
      Awaitility.await("the new incarnation catches up as a passive member")
          .untilAsserted(
              () -> {
                assertThat(m3.getRole()).isEqualTo(Role.PASSIVE);
                assertThat(commitIndex(m3)).isGreaterThanOrEqualTo(next);
              });
    }
  }

  @Nested
  final class Fencing {

    /** Demotes member 3 and fences it with incarnation 0, with a leader among 1 and 2. */
    private RaftServer demoteAndFenceMember3(final List<RaftServer> cluster) {
      final var leader = awaitLeaderIs(cluster.get(0), cluster.toArray(RaftServer[]::new));
      awaitChanged(
          leader,
          List.of(member(id1, Type.ACTIVE), member(id2, Type.ACTIVE), member(id3, Type.PASSIVE)),
          List.of(new FenceEntry(id3, 0)),
          0);
      return leader;
    }

    private List<RaftServer> bootstrapThreeVoters() {
      return bootstrap(Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE));
    }

    @Test
    void shouldNotifyFencedMemberWithLeaderNotice() {
      // given
      final var cluster = bootstrapThreeVoters();
      final var m3 = cluster.get(2);
      final var fencedIncarnation = new CompletableFuture<Long>();
      m3.addFencedListener(fencedIncarnation::complete);

      // when
      final var leader = demoteAndFenceMember3(cluster);

      // then
      assertThat(fencedIncarnation.orTimeout(30, TimeUnit.SECONDS).join()).isZero();
      Awaitility.await("fenced member stays inactive")
          .untilAsserted(() -> assertThat(m3.getRole()).isEqualTo(Role.INACTIVE));
      assertThat(configurationOf(leader).fenced()).containsExactly(new FenceEntry(id3, 0));
      appendEntry(leader);
    }

    @Test
    void shouldRejectPollFromFencedMemberWithoutTakingItsTerm() {
      // given
      final var cluster = bootstrapThreeVoters();
      final var m1 = cluster.get(0);
      final var m3 = cluster.get(2);
      final var leader = demoteAndFenceMember3(cluster);
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
    void shouldSendFencedNoticeToRemovedMemberWithOldData() {
      // given - member 3 is removed while it is down; the end of the joint phase fences it
      final var cluster = bootstrapThreeVoters();
      final var leader = awaitLeaderIs(cluster.get(0), cluster.toArray(RaftServer[]::new));
      stop(cluster.get(2));
      awaitChanged(leader, member(id1, Type.ACTIVE), member(id2, Type.ACTIVE));
      assertThat(configurationOf(leader).fenced()).containsExactly(new FenceEntry(id3, 0));

      // and - it starts with its old data, as a passive member would, and sends nothing
      final var restarted = createServer(id3, id1, id2);
      final var fenced = new CompletableFuture<Long>();
      restarted.addFencedListener(fenced::complete);
      restarted.bootstrap(id1, id2, id3);

      // when - the leader sees its fenced incarnation and sends the notice (5.6.6)
      Awaitility.await("the removed member is fenced")
          .atMost(Duration.ofSeconds(30))
          .until(
              () -> {
                leader.sendFencedNotice(id3).join();
                return fenced.isDone();
              });

      // then
      assertThat(fenced.join()).isZero();
    }

    @Test
    void shouldAddFencedMemberAgainWithNewIncarnation() throws IOException {
      // given
      final var cluster = bootstrapThreeVoters();
      final var m3 = cluster.get(2);
      final var fenced = new CompletableFuture<Long>();
      m3.addFencedListener(fenced::complete);
      final var leader = demoteAndFenceMember3(cluster);
      fenced.orTimeout(30, TimeUnit.SECONDS).join();

      // when - member 3 deletes its data, starts without data with a new incarnation, and the
      // leader replaces its incarnation (rule 2)
      loseDisk(m3, id3);
      final var restarted = createServer(id3, id1, id2);
      restarted.startWithoutConfiguration(77).orTimeout(30, TimeUnit.SECONDS).join();
      awaitChanged(
          leader,
          member(id1, Type.ACTIVE),
          member(id2, Type.ACTIVE),
          member(id3, Type.PASSIVE, 77));

      // then - the old entry stays and does not match the new incarnation
      final var configuration = configurationOf(leader);
      assertThat(configuration.newMembers())
          .filteredOn(member -> member.memberId().equals(id3))
          .extracting(RaftMember::getType, RaftMember::getIncarnation)
          .containsExactly(tuple(Type.PASSIVE, 77L));
      assertThat(configuration.fenced()).containsExactly(new FenceEntry(id3, 0));
      final var index = appendEntry(leader);
      Awaitility.await("the new incarnation receives entries")
          .untilAsserted(() -> assertThat(commitIndex(restarted)).isGreaterThanOrEqualTo(index));
    }

    @Test
    void shouldNotifyFencedMemberThatRestartsWithOldData() {
      // given - member 3 is down while it is demoted and fenced
      final var cluster = bootstrapThreeVoters();
      final var leader = awaitLeaderIs(cluster.get(0), cluster.toArray(RaftServer[]::new));
      stop(cluster.get(2));
      awaitChanged(
          leader,
          List.of(member(id1, Type.ACTIVE), member(id2, Type.ACTIVE), member(id3, Type.PASSIVE)),
          List.of(new FenceEntry(id3, 0)),
          0);

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
  final class EndOfJointPhase {

    @Test
    void shouldFenceRemovedVoterWhileAnotherVoterIsDown() {
      // given - voters 1, 2 and 3, passive member 4 has caught up, member 1 leads
      final var cluster =
          bootstrap(
              Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE, id4, Type.PASSIVE));
      final var leader =
          awaitLeaderIs(cluster.get(0), cluster.get(0), cluster.get(1), cluster.get(2));
      final var index = appendEntry(leader);
      Awaitility.await("passive member caught up")
          .untilAsserted(
              () -> assertThat(commitIndex(cluster.get(3))).isGreaterThanOrEqualTo(index));
      stop(cluster.get(1));

      // when - member 3 leaves and member 4 replaces it in one change; the old voters need member
      // 3 for their quorum, thus it is fenced only when the joint phase ends
      awaitChanged(
          leader, member(id1, Type.ACTIVE), member(id2, Type.ACTIVE), member(id4, Type.ACTIVE));

      // then
      final var configuration = leader.getConfiguration().orTimeout(5, TimeUnit.SECONDS).join();
      assertThat(configuration.requiresJointConsensus()).isFalse();
      assertThat(configuration.newMembers())
          .extracting(RaftMember::memberId, RaftMember::getType)
          .containsExactlyInAnyOrder(
              tuple(id1, Type.ACTIVE), tuple(id2, Type.ACTIVE), tuple(id4, Type.ACTIVE));
      assertThat(configuration.fenced()).containsExactly(new FenceEntry(id3, 0));
      appendEntry(leader);
    }

    @Test
    void shouldCommitRemovalOfLeaderWithoutElection() {
      // given - voters 1, 2 and 3, member 3 leads
      final var cluster = bootstrap(Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE));
      final var leader = awaitLeaderIs(cluster.get(2), cluster.toArray(RaftServer[]::new));
      appendEntry(leader);
      final var termBefore = leader.getTerm();

      // when - the leader removes itself
      final var response =
          leader
              .changeMembers(
                  new MembershipChange(
                      List.of(member(id1, Type.ACTIVE), member(id2, Type.ACTIVE)),
                      List.of(),
                      0,
                      configurationIndex(leader)))
              .orTimeout(30, TimeUnit.SECONDS)
              .join();
      // the leader steps down when the configuration that fences it commits, thus it may answer
      // with an error although the change is done; the membership component transfers the
      // leadership before (5.5, rule 1)
      assertThat(response).isNotNull();

      // then - the leader itself appended the configuration that ends the joint phase
      final var newLeader = awaitLeader(cluster.get(0), cluster.get(1));
      appendEntry(newLeader);
      final var configuration = newLeader.getConfiguration().orTimeout(5, TimeUnit.SECONDS).join();
      assertThat(configuration.requiresJointConsensus()).isFalse();
      assertThat(configuration.newMembers())
          .extracting(RaftMember::memberId)
          .containsOnly(id1, id2);
      assertThat(configuration.fenced()).containsExactly(new FenceEntry(id3, 0));
      assertThat(configuration.term()).isEqualTo(termBefore);
    }

    @Test
    void shouldFenceRemovedMemberWhenNewLeaderEndsJointPhase() {
      // given - voters 1 to 4, member 1 leads
      final var cluster =
          bootstrap(Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE, id4, Type.ACTIVE));
      final var m1 = awaitLeaderIs(cluster.get(0), cluster.toArray(RaftServer[]::new));
      final var m2 = cluster.get(1);
      appendEntry(m1);
      // and the leader never hears back, thus it cannot commit what it appends
      ((TestRaftServerProtocol) m1.getContext().getProtocol())
          .interceptResponse(
              AppendResponse.class,
              response -> CompletableFuture.failedFuture(new ConnectException("dropped")));

      // when - it appends the joint configuration that removes member 4, then fails
      m1.changeMembers(
          new MembershipChange(
              List.of(member(id1, Type.ACTIVE), member(id2, Type.ACTIVE), member(id3, Type.ACTIVE)),
              List.of(),
              0,
              configurationIndex(m1)));
      Awaitility.await("the followers have the joint configuration")
          .untilAsserted(() -> assertThat(configurationOf(m2).requiresJointConsensus()).isTrue());
      assertThat(configurationOf(m1).fenced()).isEmpty();
      stop(m1);

      // then - a new leader ends the joint phase and fences member 4 (R18, Q13)
      final var newLeader = awaitLeader(cluster.get(1), cluster.get(2), cluster.get(3));
      Awaitility.await("the joint phase is ended")
          .untilAsserted(
              () -> {
                final var configuration = configurationOf(m2);
                assertThat(configuration.requiresJointConsensus()).isFalse();
                assertThat(configuration.newMembers())
                    .extracting(RaftMember::memberId)
                    .containsOnly(id1, id2, id3);
                assertThat(configuration.fenced()).containsExactly(new FenceEntry(id4, 0));
              });
      assertThat(newLeader).isNotNull();
    }

    @Test
    void shouldNotFenceRemovedMemberInDataPartition() {
      // given
      final var members = Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE, id3, Type.ACTIVE);
      final var m1 = createDataPartitionServer(id1, id2, id3);
      final var m2 = createDataPartitionServer(id2, id1, id3);
      final var m3 = createDataPartitionServer(id3, id1, id2);
      CompletableFuture.allOf(m1.bootstrap(members), m2.bootstrap(members), m3.bootstrap(members))
          .orTimeout(30, TimeUnit.SECONDS)
          .join();
      final var leader = awaitLeaderIs(m1, m1, m2, m3);
      appendEntry(leader);

      // when
      awaitChanged(leader, member(id1, Type.ACTIVE), member(id2, Type.ACTIVE));

      // then - data partitions keep their behavior (R20)
      assertThat(configurationOf(leader).fenced()).isEmpty();
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
    void shouldNotifyConfigurationListenerAndReadTypedConfiguration() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      final var m3 = cluster.get(2);
      final var committed = new ConcurrentLinkedQueue<Configuration>();
      m3.addConfigurationListener(
          (configuration, isCommitted) -> {
            if (isCommitted) {
              committed.add(configuration);
            }
          });

      // when
      awaitChanged(
          leader,
          member(id1, Type.ACTIVE),
          member(id2, Type.ACTIVE),
          member(id3, Type.PASSIVE),
          member(id4, Type.PASSIVE, 4));

      // then
      Awaitility.await("passive member is notified of the committed configuration")
          .untilAsserted(
              () ->
                  assertThat(committed)
                      .anySatisfy(
                          configuration -> assertThat(configuration.newMembers()).hasSize(4)));
      final var view = leader.getConfiguration().orTimeout(5, TimeUnit.SECONDS).join();
      assertThat(view.index()).isEqualTo(configurationIndex(leader));
      assertThat(view.requiresJointConsensus()).isFalse();
      assertThat(view.newMembers())
          .extracting(RaftMember::memberId, RaftMember::getType)
          .containsExactlyInAnyOrder(
              tuple(id1, Type.ACTIVE),
              tuple(id2, Type.ACTIVE),
              tuple(id3, Type.PASSIVE),
              tuple(id4, Type.PASSIVE));
    }

    @Test
    void shouldExposeReplicationStatusOnLeaderOnly() {
      // given
      final var cluster = bootstrapTwoVotersOnePassive();
      final var leader = awaitLeader(cluster.toArray(RaftServer[]::new));
      final var index = appendEntry(leader);
      final var follower = cluster.stream().filter(s -> s != leader).findFirst().orElseThrow();

      // when
      final var status = leader.getReplicationStatus().orTimeout(5, TimeUnit.SECONDS).join();

      // then
      assertThat(follower.getReplicationStatus().orTimeout(5, TimeUnit.SECONDS).join()).isEmpty();
      assertThat(status).isPresent();
      Awaitility.await("the passive member is replicated")
          .untilAsserted(
              () ->
                  assertThat(
                          leader
                              .getReplicationStatus()
                              .join()
                              .orElseThrow()
                              .member(id3)
                              .orElseThrow()
                              .matchIndex())
                      .isGreaterThanOrEqualTo(index));
      assertThat(status.orElseThrow().commitIndex()).isGreaterThanOrEqualTo(index);
    }
  }
}
