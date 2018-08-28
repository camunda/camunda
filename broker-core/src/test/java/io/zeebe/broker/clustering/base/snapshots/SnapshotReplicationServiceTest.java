/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.base.snapshots;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.snapshotReplicationServiceName;
import static io.zeebe.util.StringUtil.getBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.zeebe.broker.clustering.api.ErrorResponse;
import io.zeebe.broker.clustering.api.FetchSnapshotChunkRequest;
import io.zeebe.broker.clustering.api.FetchSnapshotChunkResponse;
import io.zeebe.broker.clustering.api.ListSnapshotsRequest;
import io.zeebe.broker.clustering.api.ListSnapshotsResponse;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.broker.util.BufferingClientOutput;
import io.zeebe.broker.util.BufferingClientOutput.Request;
import io.zeebe.broker.util.ControlledTopologyManager;
import io.zeebe.clustering.management.ErrorResponseCode;
import io.zeebe.clustering.management.FetchSnapshotChunkRequestEncoder;
import io.zeebe.clustering.management.ListSnapshotsRequestEncoder;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotMetadata;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorage;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorageConfiguration;
import io.zeebe.logstreams.spi.ReadableSnapshot;
import io.zeebe.logstreams.spi.SnapshotMetadata;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.security.MessageDigest;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class SnapshotReplicationServiceTest {
  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private TemporaryFolder tempFolder = new TemporaryFolder();
  private ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();
  private ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorSchedulerRule);
  private final Duration snapshotPollInterval = Duration.ofSeconds(1);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(tempFolder).around(actorSchedulerRule).around(serviceContainerRule);

  private final SnapshotReplicationService service =
      new SnapshotReplicationService(snapshotPollInterval);
  private final ControlledTopologyManager topologyManager = spy(new ControlledTopologyManager());
  private final BufferingClientOutput output = new BufferingClientOutput(DEFAULT_REQUEST_TIMEOUT);
  private final ClientTransport transport = createTransport();
  private final FsSnapshotStorageConfiguration config = new FsSnapshotStorageConfiguration();
  private FsSnapshotStorage storage;
  private Partition partition;
  private final NodeInfo leaderNodeInfo = createLeaderNodeInfo(0);

  @Before
  public void setUp() throws Exception {
    tempFolder.create();
    storage = createSnapshotStorage();
    partition = createPartition();

    service.getTopologyManagerInjector().inject(topologyManager);
    service.getManagementClientApiInjector().inject(transport);
    service.getPartitionInjector().inject(partition);

    topologyManager.setPartitionLeader(partition, leaderNodeInfo);
  }

  @Test
  public void shouldRetryIfLeaderNotInTopologyYet() throws Exception {
    // given
    topologyManager.getTopology().removeMember(leaderNodeInfo);

    // when
    installService();

    // then
    assertThat(output.getSentRequests()).isEmpty();

    // when
    topologyManager.setPartitionLeader(partition, leaderNodeInfo);
    actorSchedulerRule.waitForTimer(SnapshotReplicationService.ERROR_RETRY_INTERVAL);

    // then
    assertThat(output.getSentRequests().size()).isEqualTo(1);
    assertThat(output.getLastRequest().getRequest()).isInstanceOf(ListSnapshotsRequest.class);

    // when
    final ListSnapshotsRequest sentRequest =
        (ListSnapshotsRequest) output.getLastRequest().getRequest();

    // then
    assertThat(sentRequest.getPartitionId()).isEqualTo(partition.getInfo().getPartitionId());
  }

  @Test
  public void shouldRetryOnTopologyError() {
    // when
    topologyManager.setQueryError(new RuntimeException("fail"));
    installService();

    // then
    assertThat(output.getSentRequests()).isEmpty();

    // when
    topologyManager.setQueryError(null);
    actorSchedulerRule.waitForTimer(SnapshotReplicationService.ERROR_RETRY_INTERVAL);

    // then
    assertThat(output.getSentRequests().size()).isEqualTo(1);
    assertThat(output.getLastRequest().getRequest()).isInstanceOf(ListSnapshotsRequest.class);

    // when
    final ListSnapshotsRequest sentRequest =
        (ListSnapshotsRequest) output.getLastRequest().getRequest();

    // then
    assertThat(sentRequest.getPartitionId()).isEqualTo(partition.getInfo().getPartitionId());
  }

  @Test
  public void shouldUpdateTopologyOnTransportError() {
    // when
    installService();

    // then
    assertThat(output.getSentRequests().size()).isEqualTo(1);
    assertThat(output.getLastRequest().getTemplateId())
        .isEqualTo(ListSnapshotsRequestEncoder.TEMPLATE_ID);

    // when
    final NodeInfo newLeader = createNodeInfo(1, "0.0.0.0", 5);
    topologyManager.setPartitionLeader(partition, newLeader);
    output.getLastRequest().respondWith(new RuntimeException("network error"));
    actorSchedulerRule.workUntilDone();
    actorSchedulerRule.waitForTimer(SnapshotReplicationService.ERROR_RETRY_INTERVAL);

    // then
    assertThat(output.getSentRequests().size()).isEqualTo(2);
    assertThat(output.getLastRequest().getTemplateId())
        .isEqualTo(ListSnapshotsRequestEncoder.TEMPLATE_ID);
  }

  @Test
  public void shouldRetryIfListSnapshotsFails() throws Exception {
    // given
    final ErrorResponse response =
        new ErrorResponse().setCode(ErrorResponseCode.PARTITION_NOT_FOUND).setData("fail");

    // when
    installService();

    // then
    assertThat(output.getSentRequests().size()).isEqualTo(1);
    assertThat(output.getLastRequest().getTemplateId())
        .isEqualTo(ListSnapshotsRequestEncoder.TEMPLATE_ID);

    // when
    output.getLastRequest().respondWith(response);
    actorSchedulerRule.workUntilDone();
    actorSchedulerRule.waitForTimer(SnapshotReplicationService.ERROR_RETRY_INTERVAL);

    // then
    assertThat(output.getSentRequests().size()).isEqualTo(2);
    assertThat(output.getLastRequest().getTemplateId())
        .isEqualTo(ListSnapshotsRequestEncoder.TEMPLATE_ID);
  }

  @Test
  public void shouldRefreshLeaderAddressOnListSnapshotsError() throws Exception {
    // given
    final ErrorResponse response =
        new ErrorResponse().setCode(ErrorResponseCode.PARTITION_NOT_FOUND).setData("fail");

    // when
    installService();

    // then
    assertThat(output.getSentRequests().size()).isEqualTo(1);
    assertThat(output.getLastRequest().getTemplateId())
        .isEqualTo(ListSnapshotsRequestEncoder.TEMPLATE_ID);

    // when
    final NodeInfo newLeader = createNodeInfo(1, "0.0.0.0", 12345);
    topologyManager.setPartitionLeader(partition, newLeader);
    output.getLastRequest().respondWith(response);
    actorSchedulerRule.workUntilDone();
    actorSchedulerRule.waitForTimer(SnapshotReplicationService.ERROR_RETRY_INTERVAL);

    // then
    assertThat(output.getSentRequests().size()).isEqualTo(2);
    final Request lastRequest = output.getLastRequest();
    assertThat(lastRequest.getTemplateId()).isEqualTo(ListSnapshotsRequestEncoder.TEMPLATE_ID);
  }

  @Test
  public void shouldAbortCurrentSnapshotIfOneChunkFailsAndMoveToNextOne() throws Exception {
    // given
    final ErrorResponse errorResponse =
        new ErrorResponse().setCode(ErrorResponseCode.READ_ERROR).setData("could not read");
    final SnapshotMetadata[] snapshots =
        new SnapshotMetadata[] {createSnapshot("foo", 3L, "foo"), createSnapshot("bar", 6L, "bar")};

    // when
    installService();

    // then
    assertThat(output.getSentRequests().size()).isEqualTo(1);
    assertThat(output.getLastRequest().getTemplateId())
        .isEqualTo(ListSnapshotsRequestEncoder.TEMPLATE_ID);

    // when
    output.getLastRequest().respondWith(generateListSnapshotsResponse(snapshots));
    actorSchedulerRule.workUntilDone();

    // then
    assertThat(output.getLastRequest().getTemplateId())
        .isEqualTo(FetchSnapshotChunkRequestEncoder.TEMPLATE_ID);
    assertFetchingSnapshot(snapshots[0]);

    // when
    output.getLastRequest().respondWith(errorResponse);
    actorSchedulerRule.workUntilDone();

    // then
    assertThat(storage.listSnapshots()).isEmpty();

    // when
    actorSchedulerRule.waitForTimer(SnapshotReplicationService.ERROR_RETRY_INTERVAL);

    // then
    assertFetchingSnapshot(snapshots[1]);
  }

  @Test
  public void shouldReplicationSnapshot() throws Exception {
    // given
    final SnapshotMetadata[] snapshots =
        new SnapshotMetadata[] {createSnapshot("foo", 3L, "foo"), createSnapshot("bar", 6L, "bar")};

    // when
    installService();

    // then
    assertThat(output.getLastRequest().getTemplateId())
        .isEqualTo(ListSnapshotsRequestEncoder.TEMPLATE_ID);

    // when
    output.getLastRequest().respondWith(generateListSnapshotsResponse(snapshots));
    actorSchedulerRule.workUntilDone();

    // then
    assertFetchingSnapshot(snapshots[0]);

    // when
    FetchSnapshotChunkRequest request =
        (FetchSnapshotChunkRequest) output.getLastRequest().getRequest();
    output.getLastRequest().respondWith(generateFetchSnapshotChunkResponse("foo", request));
    actorSchedulerRule.workUntilDone();

    // then
    assertReplicated(snapshots[0], "foo");
    assertFetchingSnapshot(snapshots[1]);

    // when
    request = (FetchSnapshotChunkRequest) output.getLastRequest().getRequest();
    output.getLastRequest().respondWith(generateFetchSnapshotChunkResponse("bar", request));
    actorSchedulerRule.workUntilDone();

    // then
    assertReplicated(snapshots[1], "bar");

    // when
    actorSchedulerRule.waitForTimer(snapshotPollInterval);

    // then
    assertThat(output.getLastRequest().getTemplateId())
        .isEqualTo(ListSnapshotsRequestEncoder.TEMPLATE_ID);
  }

  @Test
  public void shouldPollSnapshotsOnAbortIfNoMoreToReplicate() throws Exception {
    // given
    final ErrorResponse errorResponse =
        new ErrorResponse().setCode(ErrorResponseCode.READ_ERROR).setData("could not read");
    final SnapshotMetadata[] snapshots = new SnapshotMetadata[] {createSnapshot("foo", 3L, "foo")};

    // when
    installService();
    output.getLastRequest().respondWith(generateListSnapshotsResponse(snapshots));
    actorSchedulerRule.workUntilDone();

    // then
    assertFetchingSnapshot(snapshots[0]);

    // when
    output.getLastRequest().respondWith(errorResponse);
    actorSchedulerRule.workUntilDone();
    actorSchedulerRule.waitForTimer(snapshotPollInterval);

    // then
    assertThat(output.getLastRequest().getTemplateId())
        .isEqualTo(ListSnapshotsRequestEncoder.TEMPLATE_ID);
  }

  @Test
  public void shouldNotRemoveLastReplicatedSnapshotWhenClosing() throws Exception {
    // given
    final ServiceName<SnapshotReplicationService> serviceName =
        snapshotReplicationServiceName(partition);
    final SnapshotMetadata[] snapshots = new SnapshotMetadata[] {createSnapshot("foo", 3L, "foo")};

    // when
    installService();
    output.getLastRequest().respondWith(generateListSnapshotsResponse(snapshots));
    actorSchedulerRule.workUntilDone();
    final FetchSnapshotChunkRequest request =
        (FetchSnapshotChunkRequest) output.getLastRequest().getRequest();
    output.getLastRequest().respondWith(generateFetchSnapshotChunkResponse("foo", request));
    actorSchedulerRule.workUntilDone();

    // then
    assertReplicated(snapshots[0], "foo");

    // when
    serviceContainerRule.get().removeService(serviceName);
    actorSchedulerRule.workUntilDone();

    // then
    assertThat(serviceContainerRule.get().hasService(serviceName)).isFalse();
    assertReplicated(snapshots[0], "foo");
  }

  private void installService() {
    serviceContainerRule
        .get()
        .createService(snapshotReplicationServiceName(partition), service)
        .install();
    actorSchedulerRule.workUntilDone();
  }

  private void assertReplicated(final SnapshotMetadata snapshot, final String contents)
      throws Exception {
    final ReadableSnapshot replicated = storage.getLastSnapshot(snapshot.getName());
    final byte[] contentsBytes = getBytes(contents);
    final byte[] readBuffer = new byte[contentsBytes.length];

    assertThat(replicated).isNotNull();
    replicated.getData().read(readBuffer, 0, contentsBytes.length);

    assertThat(replicated.getPosition()).isEqualTo(snapshot.getPosition());
    assertThat(replicated.getChecksum()).isEqualTo(snapshot.getChecksum());
    assertThat(replicated.getSize()).isEqualTo(snapshot.getSize());
    assertThat(readBuffer).isEqualTo(contentsBytes);
  }

  private void assertFetchingSnapshot(final SnapshotMetadata snapshot) {
    final FetchSnapshotChunkRequest request;

    assertThat(output.getLastRequest().getRequest()).isInstanceOf(FetchSnapshotChunkRequest.class);
    request = (FetchSnapshotChunkRequest) output.getLastRequest().getRequest();

    assertThat(request.getChunkOffset()).isEqualTo(0);
    assertThat(request.getPartitionId()).isEqualTo(partition.getInfo().getPartitionId());
    assertThat(BufferUtil.bufferAsString(request.getName())).isEqualTo(snapshot.getName());
    assertThat(request.getLogPosition()).isEqualTo(snapshot.getPosition());
  }

  private ClientTransport createTransport() {
    final ClientTransport transport = mock(ClientTransport.class);
    doAnswer((i) -> output).when(transport).getOutput();

    return transport;
  }

  private FetchSnapshotChunkResponse generateFetchSnapshotChunkResponse(
      final String contents, final FetchSnapshotChunkRequest request) {
    final byte[] data = getBytes(contents);
    final int length = Math.min(request.getChunkLength(), data.length);
    return new FetchSnapshotChunkResponse().setData(data, (int) request.getChunkOffset(), length);
  }

  private ListSnapshotsResponse generateListSnapshotsResponse(final SnapshotMetadata[] snapshots) {
    final ListSnapshotsResponse response = new ListSnapshotsResponse();
    for (final SnapshotMetadata snapshot : snapshots) {
      response.addSnapshot(
          snapshot.getName(), snapshot.getPosition(), snapshot.getChecksum(), snapshot.getSize());
    }

    return response;
  }

  private SnapshotMetadata createSnapshot(
      final String name, final long position, final String contents) throws Exception {
    final byte[] data = getBytes(contents);
    final byte[] checksum = MessageDigest.getInstance(config.getChecksumAlgorithm()).digest(data);
    return new FsSnapshotMetadata(name, position, data.length, true, checksum);
  }

  private NodeInfo createLeaderNodeInfo(int nodeId) {
    return createNodeInfo(nodeId, "0.0.0.0", 26501);
  }

  private NodeInfo createNodeInfo(int nodeId, final String host, final int port) {
    return new NodeInfo(
        nodeId,
        new SocketAddress(host, port),
        new SocketAddress(host, port + 1),
        new SocketAddress(host, port + 2),
        new SocketAddress(host, port + 3));
  }

  private FsSnapshotStorage createSnapshotStorage() {
    config.setRootPath(tempFolder.getRoot().getAbsolutePath());
    return new FsSnapshotStorage(config);
  }

  private Partition createPartition() {
    final PartitionInfo info = new PartitionInfo(BufferUtil.wrapString("test"), 1, 1);
    return new Partition(info, RaftState.FOLLOWER) {
      @Override
      public SnapshotStorage getSnapshotStorage() {
        return storage;
      }
    };
  }
}
