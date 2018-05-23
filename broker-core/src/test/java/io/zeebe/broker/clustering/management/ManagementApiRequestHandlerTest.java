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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.*;

import io.zeebe.broker.clustering.api.*;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfigurationManager;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.transport.clientapi.BufferingServerOutput;
import io.zeebe.clustering.management.NotLeaderResponseDecoder;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorage;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorageConfiguration;
import io.zeebe.logstreams.snapshot.SerializableWrapper;
import io.zeebe.logstreams.spi.*;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.transport.*;
import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class ManagementApiRequestHandlerTest
{
    private final Map<Integer, Partition> trackedSnapshotPartitions = new HashMap<>();
    private final TestActor actor = new TestActor();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();

    @Rule
    public ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorSchedulerRule);

    @Before
    public void setup()
    {
        actorSchedulerRule.submitActor(actor);
    }

    @Test
    public void shouldHandleListSnapshotsRequests()
    {
        // given
        final int partitionId = 1;
        final Partition partition = createAndTrackPartition(partitionId);
        final ReadableSnapshot[] expectedSnapshots = new ReadableSnapshot[] {
                createSnapshot(partition, "first", 1L, "something".getBytes()),
                createSnapshot(partition, "second", 2L, "other stuff".getBytes())
        };

        // given
        final ListSnapshotsResponse response = new ListSnapshotsResponse();
        final ListSnapshotsRequest request = new ListSnapshotsRequest().setPartitionId(1);
        final BufferingServerOutput output = new BufferingServerOutput();
        final ManagementApiRequestHandler handler = createHandler();

        // when
        sendRequest(request, handler, output);

        // then
        assertThat(output.getSentResponses().size()).isEqualTo(1);

        // when
        output.wrapResponse(0, response);

        // then
        assertThat(response.getSnapshots().size()).isEqualTo(2);
        for (final ReadableSnapshot expectedSnapshot : expectedSnapshots)
        {
            final Optional<ListSnapshotsResponse.Snapshot> found = response.getSnapshots().stream()
                    .filter((s) -> s.getName().equals(expectedSnapshot.getName()))
                    .findFirst();

            assertThat(found.isPresent()).isTrue();
            if (found.isPresent())
            {
                final ListSnapshotsResponse.Snapshot snapshot = found.get();

                assertThat(snapshot.getName()).isEqualTo(expectedSnapshot.getName());
                assertThat(snapshot.getLength()).isEqualTo(expectedSnapshot.getLength());
                assertThat(snapshot.getChecksum()).isEqualTo(expectedSnapshot.getChecksum());
                assertThat(snapshot.getLogPosition()).isEqualTo(expectedSnapshot.getPosition());
            }
        }
    }

    @Test
    public void shouldReturnNotLeaderResponseWhenListingForNotTrackedPartition()
    {
        // given
        final int partitionId = 2;
        final Partition partition = createAndTrackPartition(partitionId);
        createSnapshot(partition, "test", 1L, "something".getBytes());

        // given
        final ListSnapshotsRequest request = new ListSnapshotsRequest().setPartitionId(partitionId + 1);
        final BufferingServerOutput output = new BufferingServerOutput();
        final ManagementApiRequestHandler handler = createHandler();

        // when
        sendRequest(request, handler, output);

        // then
        assertThat(output.getSentResponses().size()).isEqualTo(1);
        assertThat(output.getTemplateId(0)).isEqualTo(NotLeaderResponseDecoder.TEMPLATE_ID);
    }

    private void sendRequest(final BufferWriter request, final ServerRequestHandler handler, final ServerOutput output)
    {
        final RemoteAddress address = new RemoteAddressImpl(1, new SocketAddress("0.0.0.0", 8080));
        final MutableDirectBuffer requestBuffer = new ExpandableDirectByteBuffer();
        request.write(requestBuffer, 0);
        actor.run(() ->
        {
            handler.onRequest(output, address, requestBuffer, 0, request.getLength(), 1);
        });
    }

    private ManagementApiRequestHandler createHandler()
    {
        final BrokerCfg brokerConfiguration = new BrokerCfg();
        final RaftPersistentConfigurationManager raftConfigurationManager = new RaftPersistentConfigurationManager(brokerConfiguration.getData());

        return new ManagementApiRequestHandler(
            raftConfigurationManager,
            actor.getActorControl(),
            null, // fragile
            brokerConfiguration,
            trackedSnapshotPartitions
        );
    }

    private Partition createAndTrackPartition(final int id)
    {
        final SnapshotStorage storage = createSnapshotStorage();
        final PartitionInfo info = new PartitionInfo(BufferUtil.wrapString("test"), id, 1);
        final Partition partition = new Partition(info, RaftState.LEADER)
        {
            @Override
            public SnapshotStorage getSnapshotStorage()
            {
                return storage;
            }
        };

        trackedSnapshotPartitions.put(id, partition);
        return partition;
    }

    private ReadableSnapshot createSnapshot(final Partition partition, final String name, final long logPosition, final byte[] contents)
    {
        final SnapshotStorage storage = partition.getSnapshotStorage();
        final SerializableWrapper<byte[]> value = new SerializableWrapper<>(contents);
        final SnapshotWriter writer;
        final ReadableSnapshot createdSnapshot;

        try
        {
            writer = storage.createSnapshot(name, logPosition);
            writer.writeSnapshot(value);
            writer.commit();
            createdSnapshot = storage.getLastSnapshot(name);
        }
        catch (final Exception e)
        {
            throw new RuntimeException(e);
        }

        return createdSnapshot;
    }

    private FsSnapshotStorage createSnapshotStorage()
    {
        final String snapshotRootPath = tempFolder.getRoot().getAbsolutePath();
        final FsSnapshotStorageConfiguration config = new FsSnapshotStorageConfiguration();
        config.setRootPath(snapshotRootPath);

        return new FsSnapshotStorage(config);
    }

    private class TestActor extends Actor
    {
        ActorControl getActorControl()
        {
            return actor;
        }

        void run(Runnable r)
        {
            actor.run(r);
            actorSchedulerRule.workUntilDone();
        }
    }
}
