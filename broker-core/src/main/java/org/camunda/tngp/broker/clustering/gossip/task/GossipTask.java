package org.camunda.tngp.broker.clustering.gossip.task;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SystemEpochClock;
import org.camunda.tngp.broker.clustering.gossip.GossipProtocol;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.protocol.Dissemination;
import org.camunda.tngp.broker.clustering.gossip.protocol.FailureDetection;
import org.camunda.tngp.broker.clustering.gossip.protocol.Probe;
import org.camunda.tngp.broker.clustering.gossip.util.FileIoUtil;
import org.camunda.tngp.broker.clustering.worker.ManagementWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class GossipTask implements WorkerTask<ManagementWorkerContext>
{
    protected final EpochClock clock = new SystemEpochClock();
    protected long lastDissemination = 0;
    protected long lastStore = -1;

    @Override
    public int execute(final ManagementWorkerContext context)
    {
        int workcount = 0;

        final GossipProtocol gossipProtocol = context.getGossipProtocol();

        final boolean isExpired = scheduleDissemination(gossipProtocol);

        workcount += executeDissemination(gossipProtocol, isExpired);
        workcount += executeFailureDetection(gossipProtocol);
        workcount += executeSuspicion(gossipProtocol);
        workcount += executeProbe(gossipProtocol);
        workcount += executeStore(gossipProtocol);
        return workcount;
    }

    protected boolean scheduleDissemination(final GossipProtocol gossipProtocol)
    {
        final long now = clock.time();
        final int interval = gossipProtocol.getDisseminationInterval();
        final boolean elapsed = interval > 0 ? now >= TimeUnit.SECONDS.toMillis(interval) + lastDissemination : false;

        if (elapsed)
        {
            final Peer localPeer = gossipProtocol.getLocalPeer();
            gossipProtocol.getMembers().updateHeartbeat(localPeer);
            lastDissemination = now;
        }
        return elapsed;
    }

    protected int executeDissemination(final GossipProtocol gossipProtocol, boolean scheduleDissemination)
    {
        int workcount = 0;

        final Dissemination[] disseminators = gossipProtocol.getDisseminators();

        for (int i = 0; i < disseminators.length; i++)
        {
            final Dissemination disseminator = disseminators[i];

            if (scheduleDissemination && disseminator.isClosed())
            {
                scheduleDissemination = false;
                disseminator.begin();
            }

            workcount += disseminator.execute();

            if (disseminator.isAcknowledged() || disseminator.isFailed() || disseminator.isSelectionFailed())
            {
                disseminator.close();
            }
        }

        return workcount;
    }

    protected int executeFailureDetection(final GossipProtocol gossipProtocol)
    {
        int workcount = 0;

        final FailureDetection[] failureDetectors = gossipProtocol.getFailureDetectors();

        for (int i = 0; i < failureDetectors.length; i++)
        {
            final FailureDetection failureDetector = failureDetectors[i];

            workcount += failureDetector.execute();

            if (failureDetector.isAcknowledged() || failureDetector.isFailed())
            {
                failureDetector.close();
            }
        }
        return workcount;
    }

    protected int executeSuspicion(final GossipProtocol gossipProtocol)
    {
        return gossipProtocol.getSuspicion().process();
    }

    protected int executeProbe(final GossipProtocol gossipProtocol)
    {
        int workcount = 0;

        final Probe[] probers = gossipProtocol.getProbers();

        for (int i = 0; i < probers.length; i++)
        {
            final Probe prober = probers[i];

            workcount += prober.execute();

            if (prober.isAcknowledged() || prober.isFailed())
            {
                prober.close();
            }
        }
        return workcount;
    }

    protected int executeStore(final GossipProtocol gossipProtocol)
    {
        int workcount = 0;

        final long now = clock.time();
        final int interval = gossipProtocol.getStorageInterval();
        final boolean elapsed = interval > 0 ? now >= TimeUnit.MINUTES.toMillis(interval) + lastStore : false;

        if (elapsed)
        {
//            System.out.println("[GOSSIP TASK STORE] now: " + now);

            workcount += 1;

            final PeerList members = gossipProtocol.getMembers();
            final File file = new File(gossipProtocol.getPeersStorageFile());
            final MessageDigest messageDigest = FileIoUtil.getSha1Digest();

            try (final InputStream is = members.toInputStream())
            {
                FileIoUtil.write(file, is, messageDigest);
            }
            catch (final IOException e)
            {
                // ignore
            }

            lastStore = now;

        }

        return workcount;
    }
}
