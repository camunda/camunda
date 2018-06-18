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
package io.zeebe.broker.clustering.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import io.zeebe.broker.clustering.api.*;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfigurationManager;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.transport.clientapi.BufferingServerOutput;
import io.zeebe.clustering.management.ErrorResponseCode;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorage;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorageConfiguration;
import io.zeebe.logstreams.snapshot.SerializableWrapper;
import io.zeebe.logstreams.spi.ReadableSnapshot;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.logstreams.spi.SnapshotWriter;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class ManagementApiRequestHandlerTest {
  private Map<Integer, Partition> trackedSnapshotPartitions;
  private TestActor actor = new TestActor();
  private BufferingServerOutput output = new BufferingServerOutput();
  private ManagementApiRequestHandler handler = createHandler();

  private TemporaryFolder tempFolder = new TemporaryFolder();
  private ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();
  private ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorSchedulerRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(tempFolder).around(actorSchedulerRule).around(serviceContainerRule);

  @Before
  public void setup() {
    trackedSnapshotPartitions = new ConcurrentHashMap<>();
    actorSchedulerRule.submitActor(actor);
    output = new BufferingServerOutput();
    handler = createHandler();
  }

  @Test
  public void shouldHandleListSnapshotsRequests() {
    // given
    final int partitionId = 1;
    final Partition partition = createAndTrackPartition(partitionId);
    final ReadableSnapshot[] expectedSnapshots =
        new ReadableSnapshot[] {
          createSnapshot(partition, "first", 1L, new SerializableWrapper<>("foo")),
          createSnapshot(partition, "second", 2L, new SerializableWrapper<>("bar"))
        };

    // given
    final ListSnapshotsResponse response = new ListSnapshotsResponse();
    final ListSnapshotsRequest request = new ListSnapshotsRequest().setPartitionId(1);

    // when
    sendRequest(request);

    // then
    assertThat(output.getSentResponses().size()).isEqualTo(1);

    // when
    output.wrapResponse(0, response);

    // then
    assertThat(response.getSnapshots().size()).isEqualTo(2);
    for (final ReadableSnapshot expectedSnapshot : expectedSnapshots) {
      final Optional<ListSnapshotsResponse.SnapshotMetadata> found =
          response
              .getSnapshots()
              .stream()
              .filter((s) -> s.getName().equals(expectedSnapshot.getName()))
              .findFirst();

      assertThat(found.isPresent()).isTrue();
      if (found.isPresent()) {
        final ListSnapshotsResponse.SnapshotMetadata snapshot = found.get();

        assertThat(snapshot.getName()).isEqualTo(expectedSnapshot.getName());
        assertThat(snapshot.getLength()).isEqualTo(expectedSnapshot.getSize());
        assertThat(snapshot.getChecksum()).isEqualTo(expectedSnapshot.getChecksum());
        assertThat(snapshot.getLogPosition()).isEqualTo(expectedSnapshot.getPosition());
      }
    }
  }

  @Test
  public void shouldListSnapshotsAndReceiveErrorWhenRequestingNotTrackedPartition() {
    // given
    final int partitionId = 1;
    createThrowawayPartitionWithSnapshot(partitionId);

    // given
    final ListSnapshotsRequest request = new ListSnapshotsRequest().setPartitionId(partitionId + 1);

    // when
    sendRequest(request);

    // then
    assertError(
        output,
        ErrorResponseCode.PARTITION_NOT_FOUND,
        SnapshotReplicationRequestHandler.PARTITION_NOT_FOUND_MESSAGE);
  }

  @Test
  public void shouldFetchSnapshotChunkAndReceiveErrorWhenRequestingNotTrackedPartition() {
    // given
    final int partitionId = 3;
    createThrowawayPartitionWithSnapshot(partitionId);

    // given
    final FetchSnapshotChunkRequest request =
        new FetchSnapshotChunkRequest().setPartitionId(partitionId + 1);

    // when
    sendRequest(request);

    // then
    assertError(
        output,
        ErrorResponseCode.PARTITION_NOT_FOUND,
        SnapshotReplicationRequestHandler.PARTITION_NOT_FOUND_MESSAGE);
  }

  @Test
  public void shouldFetchSnapshotChunkAndReceiveErrorWhenChunkOffsetIsNegative() {
    // given
    final int partitionId = 5;
    final ReadableSnapshot snapshot = createAndTrackPartitionWithSnapshot(partitionId);
    final FetchSnapshotChunkRequest request =
        getFetchSnapshotChunkRequest(partitionId, snapshot).setChunkOffset(-1);

    // when
    sendRequest(request);

    // then
    assertError(
        output,
        ErrorResponseCode.INVALID_PARAMETERS,
        SnapshotReplicationRequestHandler.INVALID_CHUNK_OFFSET_MESSAGE);
  }

  @Test
  public void shouldFetchSnapshotChunkAndReceiveErrorWhenChunkLengthIsNotPositive() {
    // given
    final int partitionId = 2;
    final ReadableSnapshot snapshot = createAndTrackPartitionWithSnapshot(partitionId);
    final FetchSnapshotChunkRequest request =
        getFetchSnapshotChunkRequest(partitionId, snapshot).setChunkLength(0);

    // when
    sendRequest(request);

    // then
    assertError(
        output,
        ErrorResponseCode.INVALID_PARAMETERS,
        SnapshotReplicationRequestHandler.INVALID_CHUNK_LENGTH_MESSAGE);
  }

  @Test
  public void shouldFetchSnapshotChunkAndReceiveErrorOnSkipError() throws Exception {
    // given
    final int partitionId = 3;
    final ReadableSnapshot mockSnapshot = createAndTrackPartitionWithMockSnapshot(partitionId);
    final FetchSnapshotChunkRequest request =
        getFetchSnapshotChunkRequest(partitionId, mockSnapshot).setChunkOffset(1);
    final InputStream mockInputStream = spy(mockSnapshot.getData());

    // when
    doAnswer((i) -> mockInputStream).when(mockSnapshot).getData();
    doAnswer((i) -> -1L).when(mockInputStream).skip(anyLong());
    sendRequest(request);

    // then
    assertError(
        output, ErrorResponseCode.READ_ERROR, SnapshotReplicationRequestHandler.SEEK_ERROR_MESSAGE);
  }

  @Test
  public void shouldFetchSnapshotChunkAndReceiveErrorOnNoBytesRead() throws Exception {
    // given
    final int partitionId = 3;
    final ReadableSnapshot mockSnapshot = createAndTrackPartitionWithMockSnapshot(partitionId);
    final FetchSnapshotChunkRequest request =
        getFetchSnapshotChunkRequest(partitionId, mockSnapshot).setChunkOffset(1);
    final InputStream mockInputStream = spy(mockSnapshot.getData());

    // when
    doAnswer((i) -> mockInputStream).when(mockSnapshot).getData();
    doAnswer((i) -> -1).when(mockInputStream).read(any(), anyInt(), anyInt());
    sendRequest(request);

    // then
    assertError(
        output,
        ErrorResponseCode.READ_ERROR,
        SnapshotReplicationRequestHandler.INVALID_READ_ERROR_MESSAGE);
  }

  @Test
  public void shouldFetchSnapshotChunkAndReceiveErrorOnReadError() throws Exception {
    // given
    final int partitionId = 3;
    final ReadableSnapshot mockSnapshot = createAndTrackPartitionWithMockSnapshot(partitionId);
    final FetchSnapshotChunkRequest request =
        getFetchSnapshotChunkRequest(partitionId, mockSnapshot).setChunkOffset(1);
    final InputStream mockInputStream = spy(mockSnapshot.getData());

    // when
    doAnswer((i) -> mockInputStream).when(mockSnapshot).getData();
    doThrow(new IOException()).when(mockInputStream).read(any(), anyInt(), anyInt());
    sendRequest(request);

    // then
    assertError(
        output, ErrorResponseCode.READ_ERROR, SnapshotReplicationRequestHandler.READ_ERROR_MESSAGE);
  }

  @Test
  public void shouldFetchSnapshotChunkAndReceiveErrorOnOpenSnapshotError() throws Exception {
    // given
    final int partitionId = 3;
    final SnapshotStorage storage = spy(createSnapshotStorage());
    final Partition partition = createAndTrackPartition(partitionId, storage);
    final ReadableSnapshot snapshot = createSnapshot(partition);
    final FetchSnapshotChunkRequest request = getFetchSnapshotChunkRequest(partitionId, snapshot);

    // when
    doThrow(new RuntimeException()).when(storage).getLastSnapshot(any());
    sendRequest(request);

    // then
    assertError(
        output,
        ErrorResponseCode.READ_ERROR,
        SnapshotReplicationRequestHandler.GET_SNAPSHOT_ERROR_MESSAGE);
  }

  @Test
  public void shouldFetchSnapshotChunkAndReceiveErrorOnSnapshotNotFound() throws Exception {
    // given
    final int partitionId = 3;

    // given
    final FetchSnapshotChunkRequest request =
        new FetchSnapshotChunkRequest()
            .setPartitionId(partitionId)
            .setChunkLength(32)
            .setChunkOffset(1)
            .setName("something")
            .setLogPosition(1L);

    // when
    createAndTrackPartition(partitionId);
    sendRequest(request);

    // then
    assertError(
        output,
        ErrorResponseCode.INVALID_PARAMETERS,
        SnapshotReplicationRequestHandler.NO_SNAPSHOT_ERROR_MESSAGE);
  }

  @Test
  public void shouldFetchSnapshotChunkWithEnoughDataForChunk() throws IOException {
    // given
    final int partitionId = 2;
    final SerializableWrapper<String> contents = new SerializableWrapper<>("foo");
    final ReadableSnapshot snapshot =
        createAndTrackPartitionWithSnapshotContents(partitionId, contents);
    final byte[] snapshotBytes = new byte[(int) snapshot.getSize()];
    final FetchSnapshotChunkRequest request =
        getFetchSnapshotChunkRequest(partitionId, snapshot).setChunkLength(1);
    final FetchSnapshotChunkResponse response = new FetchSnapshotChunkResponse();

    // when
    snapshot.getData().read(snapshotBytes);
    sendRequest(request);

    // then
    assertThat(output.getSentResponses().size()).isEqualTo(1);

    // when
    output.wrapResponse(0, response);

    // then
    assertThat(response.getData().capacity()).isEqualTo(1);
    assertThat(response.getData().getByte(0)).isEqualTo(snapshotBytes[0]);
  }

  @Test
  public void shouldFetchSnapshotChunkRecursively() throws Exception {
    // given
    final int partitionId = 2;
    final SerializableWrapper<String> contents = new SerializableWrapper<>("foo");
    final ReadableSnapshot snapshot =
        createAndTrackPartitionWithSnapshotContents(partitionId, contents);
    final byte[] snapshotBytes = new byte[(int) snapshot.getSize()];
    final FetchSnapshotChunkRequest request =
        getFetchSnapshotChunkRequest(partitionId, snapshot).setChunkLength(1);
    final FetchSnapshotChunkResponse response = new FetchSnapshotChunkResponse();

    final byte[] bufferedChunk = new byte[(int) snapshot.getSize()];
    snapshot.getData().read(snapshotBytes);
    for (int i = 0; i < snapshotBytes.length; i++) {
      // when
      sendRequest(request.setChunkOffset(i));

      // then
      assertThat(output.getSentResponses().size()).isEqualTo(i + 1);

      // when
      output.wrapResponse(i, response);

      // then
      assertThat(response.getData().capacity()).isEqualTo(1);
      assertThat(response.getData().getByte(0)).isEqualTo(snapshotBytes[i]);

      bufferedChunk[i] = response.getData().getByte(0);
    }

    // when
    final SerializableWrapper<String> received = new SerializableWrapper<>("");
    received.recoverFromSnapshot(new ByteArrayInputStream(bufferedChunk));

    // then
    assertThat(received.getObject()).isEqualTo(contents.getObject());
  }

  @Test
  public void shouldHandleConcurrentFetchSnapshotChunkRequests() throws Exception {
    // given
    final FetchSnapshotChunkResponse response = new FetchSnapshotChunkResponse();
    final int partitionId = 2;
    final SerializableWrapper<String> contents = new SerializableWrapper<>("foo");
    final ReadableSnapshot snapshot =
        createAndTrackPartitionWithSnapshotContents(partitionId, contents);
    final byte[] snapshotBytes = new byte[(int) snapshot.getSize()];
    final FetchSnapshotChunkRequest firstRequest =
        getFetchSnapshotChunkRequest(partitionId, snapshot).setChunkOffset(1).setChunkLength(1);
    final FetchSnapshotChunkRequest secondRequest =
        getFetchSnapshotChunkRequest(partitionId, snapshot).setChunkOffset(2).setChunkLength(1);
    final ActorControl controller = actor.getActorControl();

    // when
    snapshot.getData().read(snapshotBytes, 0, (int) snapshot.getSize());

    // ugly hack to make sure ALL requests are processed before responses are scheduled to be sent
    // change behaviour of runUntilDone to append a job instead of prepending it
    actor.run(
        () -> {
          doAnswer(
                  i -> {
                    controller.submit(i.getArgument(0));
                    return null;
                  })
              .when(controller)
              .runUntilDone(any());
          doNothing().when(controller).done();
        });
    actorSchedulerRule.workUntilDone();

    // when
    sendRequestAsync(firstRequest);

    sendRequestAsync(secondRequest);

    // then
    assertThat(output.getSentResponses()).isEmpty();

    // when
    actorSchedulerRule.workUntilDone();

    // then
    assertThat(output.getSentResponses().size()).isEqualTo(2);

    output.wrapResponse(0, response);
    assertThat(response.getData().getByte(0)).isEqualTo(snapshotBytes[1]);

    output.wrapResponse(1, response);
    assertThat(response.getData().getByte(0)).isEqualTo(snapshotBytes[2]);
  }

  private void sendRequestAsync(final BufferWriter request) {
    final RemoteAddress address = new RemoteAddressImpl(1, new SocketAddress("0.0.0.0", 8080));
    final MutableDirectBuffer requestBuffer = new ExpandableDirectByteBuffer();
    request.write(requestBuffer, 0);
    actor.run(
        () -> {
          handler.onRequest(output, address, requestBuffer, 0, request.getLength(), 1);
        });
  }

  private void sendRequest(final BufferWriter request) {
    sendRequestAsync(request);
    actorSchedulerRule.workUntilDone();
  }

  private ManagementApiRequestHandler createHandler() {
    final BrokerCfg brokerConfiguration = new BrokerCfg();
    final RaftPersistentConfigurationManager raftConfigurationManager =
        new RaftPersistentConfigurationManager(brokerConfiguration.getData());

    return new ManagementApiRequestHandler(
        raftConfigurationManager,
        actor.getActorControl(),
        null, // fragile
        brokerConfiguration,
        trackedSnapshotPartitions);
  }

  private Partition createAndTrackPartition(final int id, final SnapshotStorage storage) {
    final PartitionInfo info = new PartitionInfo(BufferUtil.wrapString("test"), id, 1);
    final Partition partition =
        new Partition(info, RaftState.LEADER) {
          @Override
          public SnapshotStorage getSnapshotStorage() {
            return storage;
          }
        };

    trackedSnapshotPartitions.put(id, partition);
    return partition;
  }

  private Partition createAndTrackPartition(final int id) {
    final SnapshotStorage storage = createSnapshotStorage();
    return createAndTrackPartition(id, storage);
  }

  private ReadableSnapshot createSnapshot(
      final Partition partition,
      final String name,
      final long logPosition,
      final SnapshotSupport contents) {
    final SnapshotStorage storage = partition.getSnapshotStorage();
    final SnapshotWriter writer;
    final ReadableSnapshot createdSnapshot;

    try {
      writer = storage.createSnapshot(name, logPosition);
      writer.writeSnapshot(contents);
      writer.commit();
      createdSnapshot = storage.getLastSnapshot(name);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }

    return createdSnapshot;
  }

  private FsSnapshotStorage createSnapshotStorage() {
    final String snapshotRootPath = tempFolder.getRoot().getAbsolutePath();
    final FsSnapshotStorageConfiguration config = new FsSnapshotStorageConfiguration();
    config.setRootPath(snapshotRootPath);

    return new FsSnapshotStorage(config);
  }

  private void createThrowawayPartitionWithSnapshot(final int id) {
    createAndTrackPartitionWithSnapshot(id);
  }

  private ReadableSnapshot createAndTrackPartitionWithSnapshot(final int id) {
    final Partition partition = createAndTrackPartition(id);
    return createSnapshot(partition);
  }

  private ReadableSnapshot createAndTrackPartitionWithMockSnapshot(final int id) throws Exception {
    final SnapshotStorage storage = spy(createSnapshotStorage());
    final Partition partition = createAndTrackPartition(id, storage);
    final ReadableSnapshot snapshot = createSnapshot(partition);
    final ReadableSnapshot mockSnapshot = spy(snapshot);

    doAnswer((i) -> mockSnapshot).when(storage).getLastSnapshot(snapshot.getName());

    return mockSnapshot;
  }

  private ReadableSnapshot createSnapshot(final Partition partition) {
    final SerializableWrapper<String> contents = new SerializableWrapper<>("snapshot contents");
    return createSnapshot(partition, "something", 10L, contents);
  }

  private ReadableSnapshot createAndTrackPartitionWithSnapshotContents(
      final int id, final SnapshotSupport contents) {
    final Partition partition = createAndTrackPartition(id);
    return createSnapshot(partition, "something", 30L, contents);
  }

  private FetchSnapshotChunkRequest getFetchSnapshotChunkRequest(
      final int partitionId, final ReadableSnapshot snapshot) {
    return new FetchSnapshotChunkRequest()
        .setPartitionId(partitionId)
        .setName(snapshot.getName())
        .setLogPosition(snapshot.getPosition())
        .setChunkOffset(0)
        .setChunkLength((int) snapshot.getSize());
  }

  private void assertError(
      final BufferingServerOutput output, final ErrorResponseCode code, final String message) {
    final ErrorResponse response = new ErrorResponse();

    assertThat(output.getSentResponses().size()).isEqualTo(1);

    output.wrapResponse(0, response);
    assertThat(response.getCode()).isEqualTo(code);
    assertThat(response.getMessage()).isEqualTo(message);
  }

  private class TestActor extends Actor {
    ActorControl mocked = spy(actor);

    ActorControl getActorControl() {
      return mocked;
    }

    void run(Runnable r) {
      mocked.run(r);
    }
  }
}
