package org.camunda.tngp.broker.clustering.raft;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SystemEpochClock;
import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.singlemessage.DataFramePool;

public class RaftProtocol
{
    protected final EpochClock clock = new SystemEpochClock();

    protected final Transport transport;
    protected final ClientChannelManager clientChannelManager;

    protected final TransportConnectionPool connectionPool;
    protected final TransportConnection connection;

    protected final DataFramePool dataFramePool;

    protected final ConfigurationManager cfg;

    protected PeerList members;
    protected final Endpoint localEndpoint = new Endpoint();

    protected List<Raft> rafts = new CopyOnWriteArrayList<>();

    public RaftProtocol(
            final ConfigurationManager cfg,
            final Transport transport,
            final ClientChannelManager clientChannelManager,
            final TransportConnectionPool connectionPool,
            final DataFramePool dataFramePool)
    {
        this.cfg = cfg;
        this.transport = transport;
        this.clientChannelManager = clientChannelManager;
        this.connectionPool = connectionPool;
        this.connection = connectionPool.openConnection();
        this.dataFramePool = dataFramePool;
    }

    public void start()
    {
    }

    public void stop()
    {
    }

    protected void initLocalEndpoint()
    {
    }

    protected void initRafts()
    {
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

    public Endpoint getLocalEndpoint()
    {
        return localEndpoint;
    }

    public List<Raft> getRafts()
    {
        return rafts;
    }

}
