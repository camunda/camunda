package org.camunda.tngp.broker.clustering.gossip.protocol;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.GossipProtocol;
import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.data.ShuffledPeerList;
import org.camunda.tngp.broker.clustering.gossip.message.GossipReader;
import org.camunda.tngp.broker.clustering.gossip.message.ProbeWriter;
import org.camunda.tngp.broker.clustering.gossip.util.Requestor;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;

public class FailureDetection
{
    protected static final int STATE_CLOSED = 0;
    protected static final int STATE_SELECTING = 1;
    protected static final int STATE_OPENING = 2;
    protected static final int STATE_OPEN = 3;
    protected static final int STATE_ACKNOWLEDGED = 4;
    protected static final int STATE_FAILED = 5;

    protected int state = STATE_CLOSED;

    protected final GossipProtocol gossipProtocol;
    protected final PeerList members;
    protected ShuffledPeerList shuffledMembers;

    protected final Peer suspiciousMember = new Peer();

    protected final Requestor[] requestors;
    protected final int probeCapacity;
    protected final Peer[] probers;
    protected int proberLength = -1;
    protected Requestor acknowledgedRequestor;

    protected final ProbeWriter writer = new ProbeWriter();
    protected final GossipReader reader = new GossipReader();

    public FailureDetection(final GossipProtocol gossipProtocol, final int proberCapacity, final int failureDetectionTimeout)
    {
        this.gossipProtocol = gossipProtocol;
        this.members = gossipProtocol.getMembers();
        this.shuffledMembers = gossipProtocol.getShuffledPeerList();

        this.probeCapacity = proberCapacity;
        this.requestors = new Requestor[proberCapacity];
        this.probers = new Peer[proberCapacity];

        final ClientChannelManager channelManager = gossipProtocol.getClientChannelManager();
        final TransportConnection connection = gossipProtocol.getConnection();

        for (int i = 0; i < proberCapacity; i++)
        {
            probers[i] = new Peer();
            requestors[i] = new Requestor(channelManager, connection, failureDetectionTimeout);
        }
    }

    public void begin(final Peer member)
    {
        if (state == STATE_CLOSED)
        {
            suspiciousMember.wrap(member);
            state = STATE_SELECTING;
        }
        else
        {
            throw new IllegalStateException("Cannot open failure detector, has not been closed.");
        }
    }

    public int execute()
    {
        int workcount = 0;

        switch (state)
        {
            case STATE_SELECTING:
            {
                workcount += 1;
                workcount += executeSelecting();
                break;
            }

            case STATE_OPENING:
            {
                workcount += 1;
                workcount += executeOpening();
                break;
            }

            case STATE_OPEN:
            {
                workcount += 1;
                workcount += pollResponse();
                break;
            }
        }

        return workcount;
    }

    protected int executeSelecting()
    {
        proberLength = shuffledMembers.next(probeCapacity, probers, suspiciousMember);
        state = STATE_OPENING;
        return 1;
    }

    protected int executeOpening()
    {
        int workcount = 0;

        writer.member(suspiciousMember);

        for (int i = 0; i < proberLength; i++)
        {
            workcount += 1;
            requestors[i].begin(probers[i].endpoint(), writer);
        }

//        System.out.println("[FAILURE DETECTOR OPENING] now: " + System.currentTimeMillis() + ", endpoint: " + suspiciousMember.endpoint());
        state = STATE_OPEN;
        return workcount;
    }

    protected int pollResponse()
    {
        int workcount = 0;

        boolean isResponseAvailable = false;
        int numFailedProbe = 0;

        for (int i = 0; i < proberLength; i++)
        {
            final Requestor requestor = requestors[i];
            workcount += requestor.execute();

            if (requestor.isResponseAvailable())
            {
                acknowledgedRequestor = requestor;
                isResponseAvailable = true;
                break;
            }
            else if (requestor.isFailed())
            {
                numFailedProbe += 1;
            }
        }

        if (isResponseAvailable)
        {
//            System.out.println("[FAILURE DETECTOR ACK] now: " + System.currentTimeMillis() + ", endpoint: " + suspiciousMember.endpoint());
            workcount += 1;
            workcount += executeResponse();
        }
        else if (numFailedProbe == proberLength)
        {
//            System.out.println("[FAILURE DETECTOR FAILED] now: " + System.currentTimeMillis() + ", endpoint: " + suspiciousMember.endpoint());
            workcount += 1;
            workcount += processFailed();
        }

        return workcount;
    }
    protected int executeResponse()
    {
        final DirectBuffer buffer = acknowledgedRequestor.getResponseBuffer();
        final int length = acknowledgedRequestor.getResponseLength();

        reader.wrap(buffer, 0, length);
        members.merge(reader);

        state = STATE_ACKNOWLEDGED;
        return 1;
    }

    protected int processFailed()
    {
        updateMember();
        members.markPeerAsSuspected(suspiciousMember);

        state = STATE_FAILED;
        return 1;
    }

    protected void updateMember()
    {
        final int idx = members.find(suspiciousMember);
        if (idx > -1)
        {
            members.get(idx, suspiciousMember);
        }
    }

    protected void releaseMember()
    {
        suspiciousMember.locked(false);
        members.set(suspiciousMember);
    }

    public void close()
    {
        try
        {
            for (int i = 0; i < proberLength; i++)
            {
                requestors[i].close();
            }
            this.proberLength = -1;
        }
        finally
        {
            updateMember();
            releaseMember();

            acknowledgedRequestor = null;
            state = STATE_CLOSED;
        }
    }

    public boolean isOpen()
    {
        return state == STATE_OPEN;
    }

    public boolean isClosed()
    {
        return state == STATE_CLOSED;
    }

    public boolean isAcknowledged()
    {
        return state == STATE_ACKNOWLEDGED;
    }

    public boolean isFailed()
    {
        return state == STATE_FAILED;
    }
}
