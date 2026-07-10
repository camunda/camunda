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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.atomix.cluster.MemberId;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.snapshot.impl.SnapshotChunkImpl;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class RaftSnapshotReplicationLagTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);
  private RaftServer leader;
  private RaftServer follower;
  private TestRaftServerProtocol leaderProtocol;

  @Before
  public void setup() {
    leader = raftRule.getLeader().orElseThrow();
    leaderProtocol = (TestRaftServerProtocol) leader.getContext().getProtocol();
  }

  @Test
  public void shouldSeedThenDrainSnapshotReplicationLagWhileInstalling() throws Throwable {
    // given
    final int numberOfChunks = 10;
    final var snapshot = disconnectFollowerAndTakeSnapshot(numberOfChunks);
    final long totalSnapshotSize = snapshot.getTotalSizeInBytes().orElseThrow();
    final var followerId = MemberId.from(follower.name());

    final List<Long> observedLag = new CopyOnWriteArrayList<>();
    leaderProtocol.interceptRequest(
        InstallRequest.class,
        request -> {
          final var member = leader.getContext().getCluster().getMemberContext(followerId);
          if (member != null) {
            observedLag.add(member.getSnapshotReplicationLag());
          }
        });

    // when
    reconnectFollowerAndAwaitSnapshot();

    // then
    assertThat(observedLag).isNotEmpty();
    assertThat(observedLag.get(0))
        .describedAs("lag is seeded with the full snapshot size before the first chunk is sent")
        .isEqualTo(totalSnapshotSize);
    assertThat(observedLag)
        .describedAs("lag only ever decreases while the install progresses")
        .isSortedAccordingTo(Comparator.reverseOrder());
    assertThat(observedLag.stream().distinct().count())
        .describedAs("lag drains incrementally, one acknowledged chunk at a time")
        .isGreaterThan(2);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(snapshotReplicationLag(followerId))
                    .describedAs("lag is zeroed once the install completes")
                    .isZero());
  }

  @Test
  public void shouldExcludeMetadataChunkFromLag() throws Throwable {
    // given
    final int numberOfChunks = 10;
    final var snapshot = disconnectFollowerAndTakeSnapshot(numberOfChunks);
    final long totalSnapshotSize = snapshot.getTotalSizeInBytes().orElseThrow();

    final Map<String, Integer> nonMetadataChunkSizes = new ConcurrentHashMap<>();
    final AtomicBoolean metadataTransferred = new AtomicBoolean();
    leaderProtocol.interceptRequest(
        InstallRequest.class,
        request -> {
          final var chunk = new SnapshotChunkImpl();
          if (!chunk.tryWrap(new UnsafeBuffer(request.data()))) {
            return;
          }
          if (FileBasedSnapshotStoreImpl.METADATA_FILE_NAME.equals(chunk.getChunkName())) {
            metadataTransferred.set(true);
          } else {
            nonMetadataChunkSizes.put(
                chunk.getChunkName() + "__" + chunk.getFileBlockPosition(),
                chunk.getContent().length);
          }
        });

    // when
    reconnectFollowerAndAwaitSnapshot();

    // then
    assertThat(metadataTransferred)
        .describedAs("the metadata chunk is transferred to the follower")
        .isTrue();
    final long nonMetadataBytes =
        nonMetadataChunkSizes.values().stream().mapToLong(Integer::longValue).sum();
    assertThat(nonMetadataBytes)
        .describedAs("the seeded lag accounts for every chunk except the metadata chunk")
        .isEqualTo(totalSnapshotSize);
  }

  @Test
  public void shouldPublishReplicationLagThroughMeterRegistry() throws Throwable {
    // given
    final int numberOfChunks = 10;
    final var snapshot = disconnectFollowerAndTakeSnapshot(numberOfChunks);
    final long totalSnapshotSize = snapshot.getTotalSizeInBytes().orElseThrow();
    final var followerId = MemberId.from(follower.name());
    final var registry = leader.getContext().getMeterRegistry();

    final List<Double> observedGauge = new CopyOnWriteArrayList<>();
    leaderProtocol.interceptRequest(
        InstallRequest.class,
        request -> {
          final var gauge = replicationLagGauge(registry, followerId);
          if (gauge != null) {
            observedGauge.add(gauge.value());
          }
        });

    // when
    reconnectFollowerAndAwaitSnapshot();

    // then
    assertThat(observedGauge).isNotEmpty();
    assertThat(observedGauge.get(0))
        .describedAs("the gauge is seeded with the full snapshot size before the first chunk")
        .isEqualTo((double) totalSnapshotSize);
    assertThat(observedGauge)
        .describedAs("the published gauge only ever decreases while the install progresses")
        .isSortedAccordingTo(Comparator.reverseOrder());
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var gauge = replicationLagGauge(registry, followerId);
              assertThat(gauge).describedAs("the gauge remains published").isNotNull();
              assertThat(gauge.value())
                  .describedAs("the gauge is zeroed once the install completes")
                  .isZero();
            });
  }

  @Test
  public void shouldRemoveReplicationLagMetricOnLeadershipLoss() throws Throwable {
    // given
    final int numberOfChunks = 10;
    disconnectFollowerAndTakeSnapshot(numberOfChunks);
    final var followerId = MemberId.from(follower.name());
    final var registry = leader.getContext().getMeterRegistry();
    reconnectFollowerAndAwaitSnapshot();
    assertThat(replicationLagGauge(registry, followerId))
        .describedAs("the gauge is published while the leader tracks the follower")
        .isNotNull();

    // when
    raftRule.shutdownServer(leader);

    // then
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(replicationLagGauge(registry, followerId))
                    .describedAs("the gauge is removed when the leader steps down")
                    .isNull());
  }

  private Gauge replicationLagGauge(final MeterRegistry registry, final MemberId followerId) {
    return registry
        .find("zeebe.raft.replication.lag.bytes")
        .tag("follower", followerId.id())
        .gauge();
  }

  private long snapshotReplicationLag(final MemberId followerId) {
    return leader
        .getContext()
        .getCluster()
        .getMemberContext(followerId)
        .getSnapshotReplicationLag();
  }

  private PersistedSnapshot disconnectFollowerAndTakeSnapshot(final int numberOfChunks)
      throws Exception {
    follower = raftRule.getFollower().orElseThrow();
    raftRule.partition(follower);

    leader.getContext().setPreferSnapshotReplicationThreshold(1);
    final var commitIndex = raftRule.appendEntries(2);

    final var snapshot =
        raftRule.takeSnapshot(leader, commitIndex, numberOfChunks, true).orElseThrow();
    raftRule.appendEntry();
    return snapshot;
  }

  private void reconnectFollowerAndAwaitSnapshot() throws InterruptedException {
    final var snapshotReceived = new CountDownLatch(1);
    raftRule
        .getPersistedSnapshotStore(follower.name())
        .addSnapshotListener(s -> snapshotReceived.countDown());
    raftRule.reconnect(follower);

    assertThat(snapshotReceived.await(30, TimeUnit.SECONDS)).isTrue();
  }
}
