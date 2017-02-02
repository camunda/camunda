package org.camunda.tngp.broker.clustering.management;

import java.util.function.Supplier;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.broker.clustering.gossip.GossipProtocol;
import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.raft.RaftProtocol;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.singlemessage.DataFramePool;

public class ClusterManager
{
    protected final ClientChannelManager clientChannelManager;
    protected final TransportConnectionPool connectionPool;
    protected final TransportConnection connection;
    protected final DataFramePool dataFramePool;

    protected final GossipProtocol gossipProtocol;
    protected final RaftProtocol raftProtocol;

    protected final Long2ObjectHashMap<LogStream> logStreamsById = new Long2ObjectHashMap<>();

    protected final ManyToOneConcurrentArrayQueue<Runnable> logCmdQueue = new ManyToOneConcurrentArrayQueue<>(100);
    protected final ManyToOneConcurrentArrayQueue<Supplier<Peer>> peerCmdQueue = new ManyToOneConcurrentArrayQueue<>(100);

    public ClusterManager(
            final GossipProtocol gossipProtocol,
            final RaftProtocol raftProtocol,
            final ClientChannelManager clientChannelManager,
            final TransportConnectionPool connectionPool,
            final DataFramePool dataFramePool)
    {
        this.gossipProtocol = gossipProtocol;
        this.raftProtocol = raftProtocol;
        this.clientChannelManager = clientChannelManager;
        this.connectionPool = connectionPool;
        this.connection = connectionPool.openConnection();
        this.dataFramePool = dataFramePool;
    }

    public void start()
    {
        gossipProtocol.registerGossipListener((p) ->
        {
            final Peer copy = new Peer();
            copy.wrap(p);
            peerCmdQueue.add(() ->
            {
                return copy;
            });
        });

    }

    public GossipProtocol getGossipProtocol()
    {
        return gossipProtocol;
    }

    public RaftProtocol getRaftProtocol()
    {
        return raftProtocol;
    }

    public ClientChannelManager getClientChannelManager()
    {
        return clientChannelManager;
    }

    public TransportConnectionPool getConnectionPool()
    {
        return connectionPool;
    }

    public TransportConnection getConnection()
    {
        return connection;
    }

    public DataFramePool getDataFramePool()
    {
        return dataFramePool;
    }

    public void addStream(LogStream logStream)
    {
        logCmdQueue.add(() ->
        {
            createRaft(logStream);
            logStreamsById.put(logStream.getId(), logStream);
        });
    }

    public void removeStream(LogStream logStream)
    {
        logCmdQueue.add(() ->
        {
            logStreamsById.remove(logStream.getId());
        });
    }

    protected void createRaft(LogStream stream)
    {
        final int size = gossipProtocol.getMembers().size();
        if (size == 1)
        {
            final Peer localPeer = gossipProtocol.getLocalPeer();
            final Endpoint localEndpoint = localPeer.endpoint();

            if (stream != null)
            {
//                writeLogEvents(stream);

                final Raft raft = Raft.builder(new Endpoint().host(localEndpoint.host()).port(localEndpoint.port()))
                        .withStream(stream)
                        .withClientChannelManager(clientChannelManager)
                        .withConnection(connection)
                        .withDataFramePool(dataFramePool)
                        .build()
                        .bootstrap();
                raftProtocol.getRafts().add(raft);
            }
        }
    }


//    public void writeLogEvents(final LogStream log)
//    {
//        final LogStreamWriter writer = new LogStreamWriter(log);
//        final BrokerEventMetadata eventMetadata = new BrokerEventMetadata();
//
//        eventMetadata.raftTermId(1);
//
//        for (int i = 0; i < 100_000; i++)
//        {
//            final String value = "foo" + i;
//            final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocate(value.getBytes().length));
//            msg.putBytes(0, value.getBytes());
//
//            writer
//                .positionAsKey()
//                .metadataWriter(eventMetadata)
//                .value(msg)
//                .tryWrite();
//        }
//    }
}
