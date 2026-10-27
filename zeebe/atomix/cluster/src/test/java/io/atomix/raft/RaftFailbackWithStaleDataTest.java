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

import static io.atomix.raft.cluster.RaftMember.Type.ACTIVE;
import static io.atomix.raft.cluster.RaftMember.Type.PROMOTABLE;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.impl.ReconfigurationHelper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reproduces the failback of a dual-region deployment. Zone B (members 2 and 3) fails, zone A
 * (members 0 and 1) force-removes it and keeps going, compacting its log. Zone B then comes back
 * with its old data directory and is re-added: join as PROMOTABLE, then promote to ACTIVE.
 */
final class RaftFailbackWithStaleDataTest {

  private static final MemberId M0 = MemberId.from("0");
  private static final MemberId M1 = MemberId.from("1");
  private static final MemberId M2 = MemberId.from("2");
  private static final MemberId M3 = MemberId.from("3");

  @TempDir Path dataDirectory;
  private ControllableRaftContexts raft;

  @BeforeEach
  void setUp() throws Exception {
    raft =
        new ControllableRaftContexts(4, config -> config.setPreferSnapshotReplicationThreshold(0));
    raft.setup(dataDirectory, new Random(1));
  }

  @AfterEach
  void tearDown() throws Exception {
    raft.shutdown();
  }

  @Test
  void shouldReAddZoneWithStaleDataAfterForceRemoval() {
    // given - all four members replicated some entries, then zone B fails
    final var all = Set.of(M0, M1, M2, M3);
    appendAndReplicate(all, 20);
    final var zoneA = Set.of(M0, M1);

    // when - zone A force-removes zone B, keeps writing and compacts its log past what zone B has
    final var forced =
        new ReconfigurationHelper(raft.getRaftContext(M0))
            .forceConfigure(Map.of(M0, ACTIVE, M1, ACTIVE));
    run(zoneA, 50, () -> !forced.isDone());
    assertThat(forced).isCompleted();
    appendAndReplicate(zoneA, 800);
    final long staleLastIndex =
        Math.max(
            raft.getRaftContext(M2).getLog().getLastIndex(),
            raft.getRaftContext(M3).getLog().getLastIndex());
    takeSnapshots(zoneA);
    run(
        zoneA,
        50,
        () ->
            zoneA.stream()
                .anyMatch(
                    member ->
                        raft.getRaftContext(member).getLog().getFirstIndex() <= staleLastIndex));
    for (final var member : zoneA) {
      assertThat(raft.getRaftContext(member).getLog().getFirstIndex())
          .describedAs("zone A member %s compacted past zone B's last entry", member)
          .isGreaterThan(staleLastIndex);
    }
    assertThat(raft.getRaftContext(M0).getCurrentSnapshotIndex()).isGreaterThan(staleLastIndex);

    // when - zone B comes back with the old data and is re-added. Its snapshot install is slow, as
    // for a large snapshot: it only completes after zone A has taken a newer snapshot, which is
    // past the configuration that the member received in the meantime.
    final var alive = Set.of(M0, M1, M2);
    final var join = raft.join(M2, PROMOTABLE, alive);
    final var member2 = raft.getRaftContext(M2);
    raft.getServerProtocol(M2).holdInstallRequests();
    run(alive, 60, () -> member2.getCurrentConfigurationIndex() <= 0);
    final long configurationIndex = member2.getCurrentConfigurationIndex();
    assertThat(configurationIndex).describedAs("member 2 received its configuration").isPositive();
    assertThat(member2.getCurrentSnapshotIndex()).isZero();
    appendAndReplicate(alive, 3);
    takeSnapshots(zoneA);
    run(alive, 5, () -> true);
    final long zoneASnapshotIndex = raft.getRaftContext(M0).getCurrentSnapshotIndex();
    assertThat(zoneASnapshotIndex)
        .describedAs("zone A snapshotted past the configuration of member 2")
        .isGreaterThan(configurationIndex);
    raft.getServerProtocol(M2).releaseInstallRequests();
    run(alive, 500, () -> !join.isDone());
    assertThat(join).describedAs("join of member 2").isCompleted();
    assertThat(raft.getRaftContext(M2).getCurrentSnapshotIndex())
        .describedAs("member 2 installed zone A's snapshot before promotion")
        .isEqualTo(zoneASnapshotIndex);

    final var promote = promoteWithRetries(M2, alive);

    // then
    assertThat(promote).describedAs("promotion of member 2 to ACTIVE").isCompleted();
    final var leaderConfig =
        raft.getRaftServers().values().stream()
            .filter(RaftContext::isLeader)
            .findFirst()
            .orElseThrow()
            .getCluster()
            .getConfiguration();
    assertThat(leaderConfig.newMembers())
        .filteredOn(m -> m.memberId().equals(M2))
        .extracting(RaftMember::getType)
        .containsExactly(ACTIVE);
  }

  /**
   * Retries the promotion like its caller does: a promotion built from a stale configuration view
   * fails, and is expected to succeed once the leader has sent the member its configuration.
   */
  private CompletableFuture<Void> promoteWithRetries(
      final MemberId member, final Set<MemberId> alive) {
    for (int attempt = 1; ; attempt++) {
      final var promote = raft.promote(member);
      run(alive, 100, () -> !promote.isDone());
      if (!promote.isCompletedExceptionally() || attempt == 5) {
        return promote;
      }
    }
  }

  private void takeSnapshots(final Set<MemberId> members) {
    for (final var member : members) {
      raft.takeSnapshot(member, raft.getRaftContext(member).getCommitIndex() - 1);
    }
  }

  private void appendAndReplicate(final Set<MemberId> alive, final int entries) {
    for (int i = 0; i < entries; i++) {
      raft.clientAppendOnLeader();
      run(alive, 5, () -> true);
    }
    run(alive, 20, () -> true);
  }

  /** Steps the alive members; the others are down and drop everything sent to them. */
  private void run(final Set<MemberId> alive, final int maxSteps, final BooleanSupplier cond) {
    for (int i = 0; i < maxSteps && cond.getAsBoolean(); i++) {
      raft.assertAtMostOneLeader();
      for (final var member : List.of(M0, M1, M2, M3)) {
        if (alive.contains(member)) {
          raft.getServerProtocol(member).receiveAll();
          raft.runUntilDone(member);
          raft.tickHeartbeatTimeout(member);
        } else {
          for (int d = 0; d < 20; d++) {
            raft.getServerProtocol(member).dropNextMessage();
          }
        }
      }
    }
  }
}
