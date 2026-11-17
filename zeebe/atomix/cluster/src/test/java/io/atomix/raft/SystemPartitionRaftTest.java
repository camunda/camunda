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
}
