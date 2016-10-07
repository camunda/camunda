package org.camunda.tngp.broker.clustering.gossip;

import static org.camunda.tngp.management.gossip.PeerState.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SystemEpochClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.data.PeerListIterator;
import org.camunda.tngp.broker.clustering.gossip.data.ShuffledPeerList;
import org.camunda.tngp.broker.clustering.gossip.protocol.Dissemination;
import org.camunda.tngp.broker.clustering.gossip.protocol.FailureDetection;
import org.camunda.tngp.broker.clustering.gossip.protocol.Probe;
import org.camunda.tngp.broker.clustering.gossip.protocol.Suspicion;
import org.camunda.tngp.broker.clustering.gossip.util.FileIoUtil;
import org.camunda.tngp.broker.clustering.worker.cfg.ManagementComponentCfg;
import org.camunda.tngp.list.CompactList;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public class GossipProtocol
{
    protected final EpochClock clock = new SystemEpochClock();

    protected final Transport transport;
    protected final ClientChannelManager clientChannelManager;

    protected final TransportConnectionPool connectionPool;
    protected final TransportConnection connection;

    protected final ManagementComponentCfg cfg;

    protected ShuffledPeerList shuffledPeerList;
    protected PeerList members;
    protected final Peer localPeer = new Peer();

    protected Dissemination[] disseminators;
    protected FailureDetection[] failureDetectors;
    protected Probe[] probers;
    protected Suspicion suspicion;

    protected final int disseminationInterval;
    protected final int storageInterval;
    protected final String peersStorageFile;

    public GossipProtocol(
            final ManagementComponentCfg cfg,
            final Transport transport,
            final ClientChannelManager clientChannelManager,
            final TransportConnectionPool connectionPool)
    {
        this.cfg = cfg;
        this.transport = transport;
        this.clientChannelManager = clientChannelManager;
        this.connectionPool = connectionPool;
        this.connection = connectionPool.openConnection();
        this.disseminationInterval = cfg.gossip.disseminationInterval;
        this.storageInterval = cfg.gossip.peersStorageInterval;
        this.peersStorageFile = cfg.gossip.peersStorageFile;
    }

    public void start()
    {
        initMembers();
        initLocalPeer();
        initContactPoints();

        initDisseminators();
        initFailureDetectors();
        initSuspicion();
        initProbers();
    }

    public void stop()
    {
    }

    protected void initDisseminators()
    {
        final int capacity = cfg.gossip.numDisseminators;
        final int dissmeninationTimeout = cfg.gossip.disseminationTimeout;

        disseminators = new Dissemination[capacity];

        for (int i = 0; i < capacity; i++)
        {
            disseminators[i] = new Dissemination(this, dissmeninationTimeout);
        }
    }

    protected void initFailureDetectors()
    {
        final int capacity = cfg.gossip.numFailureDetectors;
        final int failureDetectorTimeout = cfg.gossip.failureDetectorTimeout;

        failureDetectors = new FailureDetection[capacity];

        for (int i = 0; i < capacity; i++)
        {
            failureDetectors[i] = new FailureDetection(this, 4, failureDetectorTimeout);
        }
    }

    protected void initSuspicion()
    {
        final int suspicionTimeout = cfg.gossip.suspicionTimeout;
        suspicion = new Suspicion(this, suspicionTimeout);
    }

    protected void initProbers()
    {
        final int capacity = cfg.gossip.numProbers;
        final int probeTimeout = cfg.gossip.probeTimeout;

        probers = new Probe[capacity];

        for (int i = 0; i < capacity; i++)
        {
            probers[i] = new Probe(this, probeTimeout);
        }
    }

    protected void initMembers()
    {
        final File file = new File(peersStorageFile);

        if (FileIoUtil.canRead(file, FileIoUtil.getSha1Digest()))
        {
            final byte[] data = new byte[(int) file.length()];

            try (final InputStream is = new FileInputStream(file))
            {
                FileIoUtil.read(is, data);
            }
            catch (final IOException e)
            {
                // ignore
            }

            final UnsafeBuffer buffer = new UnsafeBuffer(data);
            final CompactList underlyingList = new CompactList(buffer);
            members = new PeerList(underlyingList);
        }

        if (members == null)
        {
            final int capacity = cfg.gossip.maxPeerCapacity;
            members = new PeerList(capacity);
        }

        final PeerListIterator iterator = members.iterator();
        while (iterator.hasNext())
        {
            final Peer peer = iterator.next();
            if (peer.state() == SUSPECT)
            {
                peer.state(ALIVE);
            }

            peer.locked(false);
            members.set(peer);
        }

        shuffledPeerList = new ShuffledPeerList(this);
    }

    protected void initLocalPeer()
    {
        final String hostname = cfg.host;
        final int port = cfg.port;

        getLocalPeer().endpoint()
            .host(hostname)
            .port(port);

        getLocalPeer().heartbeat()
            .generation(clock.time())
            .version(0);

        getLocalPeer()
            .state(ALIVE)
            .localPeer(true);

        final int idx = getMembers().find(localPeer);
        if (idx > -1)
        {
            getMembers().set(localPeer);
        }
        else
        {
            getMembers().insert(getLocalPeer());
        }
    }

    protected void initContactPoints()
    {
        final String[] contactPoints = cfg.gossip.initialContactPoints;

        for (int i = 0; i < contactPoints.length; i++)
        {
            final String endpoint = contactPoints[i];
            final String[] endpointParts = parseEndpoint(endpoint);

            final int port = getPort(endpointParts[1]);

            final Peer peer = new Peer();

            peer.endpoint()
                .host(endpointParts[0])
                .port(port);

            peer.heartbeat()
                .generation(0)
                .version(0);

            peer.state(ALIVE);

            getMembers().insert(peer);
        }
    }

    protected String[] parseEndpoint(final String endpoint)
    {
        final String[] parts = endpoint.split(":");

        if (parts.length != 2)
        {
            // TODO: throw exceptions?
        }

        return parts;
    }

    protected int getPort(final String port)
    {
        try
        {
            return Integer.parseInt(port);
        }
        catch (final NumberFormatException e)
        {
            throw new RuntimeException(e);
        }
    }

    public Transport getTransport()
    {
        return transport;
    }

    public ClientChannelManager getClientChannelManager()
    {
        return clientChannelManager;
    }

    public TransportConnection getConnection()
    {
        return connection;
    }

    public PeerList getMembers()
    {
        return members;
    }

    public Peer getLocalPeer()
    {
        return localPeer;
    }

    public ShuffledPeerList getShuffledPeerList()
    {
        return shuffledPeerList;
    }

    public EpochClock getClock()
    {
        return clock;
    }

    public Dissemination[] getDisseminators()
    {
        return disseminators;
    }

    public FailureDetection[] getFailureDetectors()
    {
        return failureDetectors;
    }

    public Suspicion getSuspicion()
    {
        return suspicion;
    }

    public Probe[] getProbers()
    {
        return probers;
    }

    public int getDisseminationInterval()
    {
        return disseminationInterval;
    }

    public int getStorageInterval()
    {
        return storageInterval;
    }

    public String getPeersStorageFile()
    {
        return peersStorageFile;
    }

    public void registerGossipListener(final GossipListener listener)
    {
        members.registerGossipListener(listener);
    }

}
