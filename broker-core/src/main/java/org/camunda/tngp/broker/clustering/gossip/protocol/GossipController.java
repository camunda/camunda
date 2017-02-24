package org.camunda.tngp.broker.clustering.gossip.protocol;

import static org.camunda.tngp.clustering.gossip.PeerState.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.GossipContext;
import org.camunda.tngp.broker.clustering.gossip.config.GossipConfiguration;
import org.camunda.tngp.broker.clustering.gossip.data.Heartbeat;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.message.GossipRequest;
import org.camunda.tngp.broker.clustering.gossip.message.GossipResponse;
import org.camunda.tngp.broker.clustering.util.MessageWriter;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.util.StreamUtil;

public class GossipController
{
    private final Peer localPeer;
    private final PeerList peers;

    private final Peer tmp;

    private final GossipContext context;

    private long lastDissemination = 0;
    private long lastStorage = -1;

    private final PeerList diff;
    private final GossipRequest gossipRequest;
    private final GossipResponse gossipResponse;
    private final MessageWriter responseWriter;

    private Dissemination[] disseminators;
    private FailureDetection[] failureDetectors;
    private Suspicion suspicion;

    private Probe[] proberHandlers;

    public GossipController(final GossipContext context)
    {
        this.localPeer = context.getLocalPeer();
        this.peers = context.getPeers();
        this.context = context;

        final GossipConfiguration config = context.getConfig();
        this.diff = new PeerList(config.peerCapacity);
        this.gossipRequest = new GossipRequest();
        this.gossipResponse = new GossipResponse();
        this.responseWriter = new MessageWriter(context.getSendBuffer());

        this.tmp = new Peer();
        this.tmp.reset();
    }

    public void open()
    {
        final GossipConfiguration config = context.getConfig();

        createFailureDetectors(config);
        createDisseminators(config);
        createSuspicion(config);

        createProbe(config);
    }

    protected void createDisseminators(final GossipConfiguration config)
    {
        disseminators = new Dissemination[config.disseminatorCapacity];
        for (int i = 0; i < disseminators.length; i++)
        {
            disseminators[i] = new Dissemination(context, failureDetectors);
        }
    }

    protected void createFailureDetectors(final GossipConfiguration config)
    {
        failureDetectors = new FailureDetection[config.failureDetectionCapacity];
        for (int i = 0; i < failureDetectors.length; i++)
        {
            failureDetectors[i] = new FailureDetection(context);
        }
    }

    protected void createProbe(final GossipConfiguration config)
    {
        proberHandlers = new Probe[config.probeCapacity];
        for (int i = 0; i < proberHandlers.length; i++)
        {
            proberHandlers[i] = new Probe(context);
        }
    }

    protected void createSuspicion(final GossipConfiguration config)
    {
        suspicion = new Suspicion(context);
    }

    public void close()
    {
        for (int i = 0; i < disseminators.length; i++)
        {
            disseminators[i].close();
        }

        for (int i = 0; i < failureDetectors.length; i++)
        {
            failureDetectors[i].close();
        }

        for (int i = 0; i < proberHandlers.length; i++)
        {
            proberHandlers[i].close();
        }

        suspicion.close();
    }

    public int doWork()
    {
        int workcount = 0;

        workcount += doDissemination();
        workcount += doFailureDetection();

        workcount += scheduleNextDissemination();

        workcount += doSuspicion();

        workcount += doProbe();
        workcount += doStore();

        return workcount;
    }

    protected int doDissemination()
    {
        int workcount = 0;

        for (int i = 0; i < disseminators.length; i++)
        {
            final Dissemination disseminator = disseminators[i];

            workcount += disseminator.doWork();

            if (disseminator.isAcknowledged() || disseminator.isFailed())
            {
                disseminator.close();
            }
        }

        return workcount;
    }

    protected int doFailureDetection()
    {
        int workcount = 0;

        for (int i = 0; i < failureDetectors.length; i++)
        {
            final FailureDetection failureDetector = failureDetectors[i];

            workcount += failureDetector.doWork();

            if (failureDetector.isAcknowledged() || failureDetector.isFailed())
            {
                failureDetector.close();
            }
        }
        return workcount;
    }

    protected int doSuspicion()
    {
        return suspicion.doWork();
    }

    protected int doProbe()
    {
        int workcount = 0;

        for (int i = 0; i < proberHandlers.length; i++)
        {
            final Probe prober = proberHandlers[i];

            workcount += prober.doWork();

            if (prober.isAcknowledged() || prober.isFailed())
            {
                prober.close();
            }
        }
        return workcount;
    }

    protected int doStore()
    {
        int workcount = 0;

        final GossipConfiguration config = context.getConfig();

        final long now = System.currentTimeMillis();
        final int interval = config.peersStorageInterval;
        final boolean elapsed = interval > 0 ? now >= TimeUnit.MINUTES.toMillis(interval) + lastStorage : false;

        if (elapsed)
        {
            workcount += 1;

            final File file = new File(config.peersStorageFile);
            final MessageDigest messageDigest = StreamUtil.getSha1Digest();

            try (final InputStream is = peers.toInputStream())
            {
                StreamUtil.write(file, is, messageDigest);
            }
            catch (final IOException e)
            {
                // ignore
            }

            lastStorage = now;
        }

        return workcount;
    }

    protected int scheduleNextDissemination()
    {
        int workcount = 0;

        final long now = System.currentTimeMillis();

        final GossipConfiguration config = context.getConfig();
        final int interval = config.disseminationInterval;

        final boolean elapsed = interval > 0 ? now >= TimeUnit.SECONDS.toMillis(interval) + lastDissemination : false;

        if (elapsed)
        {
            localPeer.alive();
            final Heartbeat heartbeat = localPeer.heartbeat();
            heartbeat.version(heartbeat.version() + 1);
            peers.update(localPeer);

            lastDissemination = now;

            final Dissemination disseminator = getClosedDisseminator();
            if (disseminator != null)
            {
                workcount += 1;
                disseminator.open();
            }
        }

        return workcount;
    }

    protected Dissemination getClosedDisseminator()
    {
        Dissemination dissemination = null;
        for (int i = 0; i < disseminators.length; i++)
        {
            if (disseminators[i].isClosed())
            {
                dissemination = disseminators[i];
                break;
            }
        }
        return dissemination;
    }

    protected Probe getClosedProbe()
    {
        Probe probe = null;
        for (int i = 0; i < proberHandlers.length; i++)
        {
            if (proberHandlers[i].isClosed())
            {
                probe = proberHandlers[i];
                break;
            }
        }
        return probe;
    }

    public int onGossipRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId, final long connectionId, final long requestId)
    {
        gossipRequest.wrap(buffer, offset, length);

        diff.clear();
        peers.merge(gossipRequest.peers(), diff);

        final int idx = peers.find(localPeer);
        if (idx > 0)
        {
            peers.get(idx, localPeer);
            if (localPeer.state() != ALIVE)
            {
                // refute
                localPeer.alive();
                localPeer.heartbeat().generation(System.currentTimeMillis());
                peers.set(idx, localPeer);

                final int pos = diff.find(localPeer);
                if (pos >= 0)
                {
                    diff.set(idx, localPeer);
                }
                else
                {
                    diff.add(~idx, localPeer);
                }
            }
        }

        gossipResponse.peers(diff);

        // try to write response only once, if it fails
        // do not retry it, since with the next request
        // we try to respond again.
        responseWriter.protocol(Protocols.REQUEST_RESPONSE)
            .channelId(channelId)
            .connectionId(connectionId)
            .requestId(requestId)
            .message(gossipResponse)
            .tryWriteMessage();

        return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }

    public int onProbeRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId, final long connectionId, final long requestId)
    {
        final Probe probeHandler = getClosedProbe();

        if (probeHandler != null)
        {
            probeHandler.open(buffer, offset, length, channelId, connectionId, requestId);
        }
        // else -> all probe handler are active! just consume that request
        // and do not respond. The client (the one sent this probe request)
        // will most likely timeout or maybe receive a response from another
        // prober (if there are more than 1 prober per failed peer).

        return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }
}
