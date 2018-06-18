/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.broker.it.clustering;

import static io.zeebe.broker.it.clustering.ClusteringRule.*;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.snapshotStorageServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logBlockIndexWriterService;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.api.commands.Partition;
import io.zeebe.client.api.commands.Topic;
import io.zeebe.logstreams.snapshot.SerializableWrapper;
import io.zeebe.logstreams.spi.ReadableSnapshot;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.logstreams.spi.SnapshotWriter;
import io.zeebe.servicecontainer.*;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.SocketAddress;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class SnapshotReplicationTest {
  private static final String BROKER_1_TOML = "zeebe.cluster.snapshotReplication.1.cfg.toml";
  private static final String BROKER_2_TOML = "zeebe.cluster.snapshotReplication.2.cfg.toml";
  private static final String BROKER_3_TOML = "zeebe.cluster.snapshotReplication.3.cfg.toml";

  private SocketAddress[] brokerAddresses =
      new SocketAddress[] {
        BROKER_1_CLIENT_ADDRESS, BROKER_2_CLIENT_ADDRESS, BROKER_3_CLIENT_ADDRESS
      };
  private String[] brokerConfigs = new String[] {BROKER_1_TOML, BROKER_2_TOML, BROKER_3_TOML};

  public AutoCloseableRule closeables = new AutoCloseableRule();
  public Timeout testTimeout = Timeout.seconds(90);
  public ClientRule clientRule = new ClientRule();
  public ClusteringRule clusteringRule =
      new ClusteringRule(closeables, clientRule, brokerAddresses, brokerConfigs);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(closeables).around(testTimeout).around(clientRule).around(clusteringRule);

  @Test
  public void shouldReplicateSnapshotsFromLeaderForSystemTopic() throws Exception {
    final Topic topic = clusteringRule.getInternalSystemTopic();
    final SocketAddress leaderAddress =
        clusteringRule.getLeaderAddressForPartition(topic.getPartitions().get(0).getId());
    final SocketAddress followerAddress =
        clusteringRule.getFollowerAddressForPartition(topic.getPartitions().get(0).getId());

    final SnapshotStorage leaderStorage = getSnapshotStorage(topic, leaderAddress);
    final SnapshotStorage followerStorage = getSnapshotStorage(topic, followerAddress);

    final TestSnapshot snapshot = new TestSnapshot("snarf", 1L, "bar");

    // when
    snapshot.write(leaderStorage);
    waitForReplication(followerStorage, snapshot);

    // then
    assertThat(followerStorage.listSnapshots().size()).isEqualTo(1);
    assertReplicated(followerStorage, snapshot);
  }

  @Test
  public void shouldReplicateSnapshotsFromLeaderForOtherTopic() throws Exception {
    // given
    final Topic topic = clusteringRule.createTopic("super-topic", 1, 2);
    final SocketAddress leaderAddress =
        clusteringRule.getLeaderAddressForPartition(topic.getPartitions().get(0).getId());
    final SocketAddress followerAddress =
        clusteringRule.getFollowerAddressForPartition(topic.getPartitions().get(0).getId());

    final SnapshotStorage leaderStorage = getSnapshotStorage(topic, leaderAddress);
    final SnapshotStorage followerStorage = getSnapshotStorage(topic, followerAddress);

    final TestSnapshot snapshot = new TestSnapshot("snap", 1L, "foo");

    // when
    snapshot.write(leaderStorage);
    waitForReplication(followerStorage, snapshot);

    // then
    assertThat(followerStorage.listSnapshots().size()).isEqualTo(1);
    assertReplicated(followerStorage, snapshot);
  }

  @Test
  public void shouldReplicateMultipleSnapshots() throws Exception {
    // given
    final Topic topic = clusteringRule.getInternalSystemTopic();
    final SocketAddress leaderAddress =
        clusteringRule.getLeaderAddressForPartition(topic.getPartitions().get(0).getId());
    final SocketAddress followerAddress =
        clusteringRule.getFollowerAddressForPartition(topic.getPartitions().get(0).getId());

    final SnapshotStorage leaderStorage = getSnapshotStorage(topic, leaderAddress);
    final SnapshotStorage followerStorage = getSnapshotStorage(topic, followerAddress);

    final TestSnapshot[] snapshots =
        new TestSnapshot[] {
          new TestSnapshot("snap", 1L, "foo"), new TestSnapshot("snop", 1L, "bar")
        };

    // when
    snapshots[0].write(leaderStorage);
    snapshots[1].write(leaderStorage);
    waitForReplication(followerStorage, snapshots);

    // then
    assertThat(followerStorage.listSnapshots().size()).isEqualTo(snapshots.length);
    assertReplicated(followerStorage, snapshots[0]);
    assertReplicated(followerStorage, snapshots[1]);
  }

  @Test
  public void shouldReplicateNewerSnapshotAndRemoveOlderOne() throws Exception {
    // given
    final Topic topic = clusteringRule.getInternalSystemTopic();
    final SocketAddress leaderAddress =
        clusteringRule.getLeaderAddressForPartition(topic.getPartitions().get(0).getId());
    final SocketAddress followerAddress =
        clusteringRule.getFollowerAddressForPartition(topic.getPartitions().get(0).getId());

    final SnapshotStorage leaderStorage = getSnapshotStorage(topic, leaderAddress);
    final SnapshotStorage followerStorage = getSnapshotStorage(topic, followerAddress);

    final TestSnapshot snapshot = new TestSnapshot("snap", 1L, "foo");

    // when
    snapshot.write(leaderStorage);
    waitForReplication(followerStorage, snapshot);

    // then
    assertThat(followerStorage.listSnapshots().size()).isEqualTo(1);
    assertReplicated(followerStorage, snapshot);

    // when
    snapshot.position = 2L;
    snapshot.contents = "bar";
    snapshot.write(leaderStorage);
    waitForReplication(followerStorage, snapshot);

    // then
    assertThat(followerStorage.listSnapshots().size()).isEqualTo(1);
    assertReplicated(followerStorage, snapshot);
  }

  @Test
  public void shouldNotReplicateLogBlockIndex() throws Exception {
    // given
    final Topic topic = clusteringRule.getInternalSystemTopic();
    final SocketAddress leaderAddress =
        clusteringRule.getLeaderAddressForPartition(topic.getPartitions().get(0).getId());
    final SocketAddress followerAddress =
        clusteringRule.getFollowerAddressForPartition(topic.getPartitions().get(0).getId());

    final SnapshotStorage leaderStorage = getSnapshotStorage(topic, leaderAddress);
    final SnapshotStorage followerStorage = getSnapshotStorage(topic, followerAddress);

    final String logBlockIndexName = logBlockIndexWriterService(topic.getName()).getName();
    final TestSnapshot[] snapshots =
        new TestSnapshot[] {
          new TestSnapshot(logBlockIndexName, 1L, "bar"), new TestSnapshot("znap", 1L, "foo")
        };

    // when
    snapshots[0].write(leaderStorage);
    snapshots[1].write(leaderStorage);
    waitForReplication(followerStorage, snapshots[1]);

    // make sure to give it enough time that it at least tried to replicate the blockIndex
    // is it necessary? giving the other snapshot a name starting with z, and creating it after,
    // hopefully it would try to always replicate the second one after trying the first, but
    // can't guarantee that
    Thread.sleep(500);

    // then
    assertThat(followerStorage.listSnapshots().size()).isEqualTo(1);
    assertReplicated(followerStorage, snapshots[1]);
  }

  private SnapshotStorage getSnapshotStorage(final Topic topic, final SocketAddress address)
      throws Exception {
    final Broker broker = clusteringRule.brokers.get(address);
    final Partition partition = topic.getPartitions().get(0);
    final String logName = String.format("%s-%d", partition.getTopicName(), partition.getId());
    final ServiceName<Void> name =
        ServiceName.newServiceName(
            String.format("test.snapshotReplication.extractor.%s", logName), Void.class);
    final SnapshotStorageExtractor extractor = new SnapshotStorageExtractor();

    broker
        .getBrokerContext()
        .getServiceContainer()
        .createService(name, extractor)
        .dependency(snapshotStorageServiceName(logName), extractor.storageInjector)
        .install()
        .join();

    return extractor.storageInjector.getValue();
  }

  private void assertReplicated(final SnapshotStorage storage, final TestSnapshot snapshot)
      throws Exception {
    final SerializableWrapper<String> contents = new SerializableWrapper<>(null);
    final ReadableSnapshot replicated = storage.getLastSnapshot(snapshot.name);

    assertThat(replicated).isNotNull();
    assertThat(replicated.getName()).isEqualTo(snapshot.name);
    assertThat(replicated.getPosition()).isEqualTo(snapshot.position);

    // when
    contents.recoverFromSnapshot(replicated.getData());

    // then
    assertThat(contents.getObject()).isEqualTo(snapshot.contents);
  }

  private void waitForReplication(final SnapshotStorage storage, final TestSnapshot... snapshots) {
    final List<TestSnapshot> list = Arrays.asList(snapshots);
    TestUtil.waitUntil(() -> list.stream().allMatch((snapshot) -> snapshot.exists(storage)));
  }

  class TestSnapshot {
    long position;
    String name;
    String contents;

    TestSnapshot(final String name, final long position, final String contents) {
      this.name = name;
      this.position = position;
      this.contents = contents;
    }

    boolean exists(final SnapshotStorage storage) {
      return storage.snapshotExists(name, position);
    }

    void write(final SnapshotStorage storage) throws Exception {
      final SnapshotWriter writer = storage.createSnapshot(name, position);
      final SerializableWrapper<String> wrapped = new SerializableWrapper<>(contents);
      writer.writeSnapshot(wrapped);
      writer.commit();
    }
  }

  class SnapshotStorageExtractor implements Service<Void> {
    Injector<SnapshotStorage> storageInjector = new Injector<>();

    @Override
    public void start(ServiceStartContext startContext) {}

    @Override
    public void stop(ServiceStopContext stopContext) {}

    @Override
    public Void get() {
      return null;
    }
  }
}
