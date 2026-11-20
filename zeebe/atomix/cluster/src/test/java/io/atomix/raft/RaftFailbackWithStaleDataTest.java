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
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
    // given - zone A force-removed zone B, kept writing and compacted its log past zone B's data
    final var zoneA = Set.of(M0, M1);
    final var alive = Set.of(M0, M1, M2);
    appendAndReplicate(Set.of(M0, M1, M2, M3), 20);
    forceRemoveZoneB(zoneA);
    appendAndReplicate(zoneA, 800);
    compactPast(zoneA, staleLastIndex());

    // when - zone B comes back with the old data and is re-added. Its snapshot install is slow, as
    // for a large snapshot: it only completes after zone A has taken a newer snapshot, which is
    // past the configuration that the member received in the meantime.
    final var join = joinWithSlowSnapshotInstall(M2, zoneA, alive);
    final var promote = promoteWithRetries(M2, alive);

    // then
    assertThat(join).describedAs("join of member 2").isCompleted();
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

  private void forceRemoveZoneB(final Set<MemberId> zoneA) {
    final var forced =
        new ReconfigurationHelper(raft.getRaftContext(M0))
            .forceConfigure(Map.of(M0, ACTIVE, M1, ACTIVE));
    raft.runWhile(zoneA, 50, () -> !forced.isDone());
    assertThat(forced).isCompleted();
  }

  /** The last index that zone B has, as it did not receive anything after it was removed. */
  private long staleLastIndex() {
    return Math.max(
        raft.getRaftContext(M2).getLog().getLastIndex(),
        raft.getRaftContext(M3).getLog().getLastIndex());
  }

  /** Snapshots and compacts the log of the members, until it starts after the given index. */
  private void compactPast(final Set<MemberId> members, final long index) {
    takeSnapshots(members);
    raft.runWhile(
        members,
        50,
        () ->
            members.stream()
                .anyMatch(member -> raft.getRaftContext(member).getLog().getFirstIndex() <= index));
    for (final var member : members) {
      assertThat(raft.getRaftContext(member).getLog().getFirstIndex())
          .describedAs("log of member %s is compacted past index %d", member, index)
          .isGreaterThan(index);
    }
  }

  /**
   * Re-adds the member as PROMOTABLE. The leader sends it its configuration, then takes a newer
   * snapshot, and only then the member receives the snapshot, which is held back until then.
   */
  private CompletableFuture<Void> joinWithSlowSnapshotInstall(
      final MemberId member, final Set<MemberId> leaderZone, final Set<MemberId> alive) {
    final var join = raft.join(member, PROMOTABLE, alive);
    final var context = raft.getRaftContext(member);
    raft.getServerProtocol(member).holdInstallRequests();
    raft.runWhile(alive, 60, () -> context.getCurrentConfigurationIndex() <= 0);
    final long configurationIndex = context.getCurrentConfigurationIndex();
    assertThat(configurationIndex)
        .describedAs("member %s received its configuration", member)
        .isPositive();
    assertThat(context.getCurrentSnapshotIndex()).isZero();

    appendAndReplicate(alive, 3);
    takeSnapshots(leaderZone);
    raft.runWhile(alive, 5, () -> true);
    final long leaderZoneSnapshotIndex = raft.getRaftContext(M0).getCurrentSnapshotIndex();
    assertThat(leaderZoneSnapshotIndex)
        .describedAs("snapshot past the configuration of member %s", member)
        .isGreaterThan(configurationIndex);

    raft.getServerProtocol(member).releaseInstallRequests();
    raft.runWhile(alive, 500, () -> !join.isDone());
    assertThat(join).describedAs("join of member %s", member).isCompleted();
    assertThat(context.getCurrentSnapshotIndex())
        .describedAs("member %s installed the snapshot before promotion", member)
        .isEqualTo(leaderZoneSnapshotIndex);
    return join;
  }

  /**
   * Retries the promotion like its caller does: a promotion built from a stale configuration view
   * fails, and is expected to succeed once the leader has sent the member its configuration.
   */
  private CompletableFuture<Void> promoteWithRetries(
      final MemberId member, final Set<MemberId> alive) {
    for (int attempt = 1; ; attempt++) {
      final var promote = raft.promote(member);
      raft.runWhile(alive, 100, () -> !promote.isDone());
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
      raft.runWhile(alive, 5, () -> true);
    }
    raft.runWhile(alive, 20, () -> true);
  }
}
