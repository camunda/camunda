package org.camunda.tngp.broker.clustering.gossip.protocol;

import static org.camunda.tngp.management.gossip.PeerState.ALIVE;
import static org.camunda.tngp.management.gossip.PeerState.DEAD;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.GossipProtocol;
import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.data.ShuffledPeerList;
import org.camunda.tngp.broker.clustering.gossip.message.GossipReader;
import org.camunda.tngp.broker.clustering.gossip.message.GossipWriter;
import org.camunda.tngp.broker.clustering.gossip.util.Requestor;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;

public class Dissemination
{
    protected static final int STATE_CLOSED = 0;
    protected static final int STATE_SELECTING = 1;
    protected static final int STATE_SELECTION_FAILED = 2;
    protected static final int STATE_LOCKING = 3;
    protected static final int STATE_OPENING = 4;
    protected static final int STATE_OPEN = 5;
    protected static final int STATE_ACKNOWLEDGED = 6;
    protected static final int STATE_FAILED = 7;

    protected int state = STATE_CLOSED;

    protected final GossipProtocol gossipProtocol;
    protected final Requestor requestor;
    protected final PeerList members;
    protected final ShuffledPeerList shuffledMembers;
    protected GossipWriter writer;

    protected final GossipReader reader = new GossipReader();

    protected final Peer target = new Peer();

    public Dissemination(final GossipProtocol gossipProtocol, final int disseminationTimeout)
    {
        this.gossipProtocol = gossipProtocol;
        this.members = gossipProtocol.getMembers();
        this.shuffledMembers = gossipProtocol.getShuffledPeerList();
        this.writer = new GossipWriter(members);

        final ClientChannelManager channelManager = gossipProtocol.getClientChannelManager();
        final TransportConnection connection = gossipProtocol.getConnection();

        this.requestor = new Requestor(channelManager, connection, disseminationTimeout);
    }

    public void begin()
    {
        if (state == STATE_CLOSED)
        {
            state = STATE_SELECTING;
        }
        else
        {
            throw new IllegalStateException("Cannot open disseminator, has not been closed.");
        }
    }

    public void close()
    {
        try
        {
            requestor.close();
        }
        finally
        {
            updateMember();
            if (canReleaseMember())
            {
                releaseMember();
            }

            state = STATE_CLOSED;
        }
    }

    protected boolean canReleaseMember()
    {
        if (target.state() == ALIVE && state == STATE_FAILED)
        {
            return false;
        }

        if (state == STATE_SELECTION_FAILED)
        {
            return false;
        }

        return true;
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

            case STATE_LOCKING:
            {
                workcount += 1;
                workcount += executeLocking();
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
        int workcount = 0;

        boolean found = false;
        final int size = members.size();

        for (int i = 0; i < size && !found; i++)
        {
            shuffledMembers.next(target);
            found = !target.localPeer() && target.state() != DEAD && !target.locked();
        }

        if (found)
        {
            workcount += 1;
            state = STATE_LOCKING;
        }
        else
        {
            workcount += 1;
            state = STATE_SELECTION_FAILED;
        }

        return workcount;
    }

    protected int executeLocking()
    {
        target.locked(true);
        members.set(target);

        state = STATE_OPENING;
        return 1;
    }

    protected int executeOpening()
    {
        requestor.begin(target.endpoint(), writer);

//        System.out.println("[DISSEMINATION OPENING] now: " + System.currentTimeMillis() + ", endpoint: " + target.endpoint());

        state = STATE_OPEN;
        return 1;
    }

    protected int pollResponse()
    {
        int workcount = 0;

        workcount += requestor.execute();

        if (requestor.isResponseAvailable())
        {
//            System.out.println("[DISSEMINATION ACK] now: " + System.currentTimeMillis() + ", endpoint: " + target.endpoint());
            workcount += 1;
            workcount += executeResponse();
        }
        else if (requestor.isFailed())
        {
//            System.out.println("[DISSEMINATION FAILED] now: " + System.currentTimeMillis() + ", endpoint: " + target.endpoint());
            workcount += 1;
            workcount += executeFailed();
        }

        return workcount;
    }

    protected int executeResponse()
    {
        final DirectBuffer buffer = requestor.getResponseBuffer();
        final int length = requestor.getResponseLength();

        reader.wrap(buffer, 0, length);
        members.merge(reader);

        state = STATE_ACKNOWLEDGED;
        return 1;
    }

    protected int executeFailed()
    {
        int workcount = 0;

        updateMember();

        if (target.state() == ALIVE)
        {
            final FailureDetection[] failureDetectors = gossipProtocol.getFailureDetectors();
            for (int i = 0; i < failureDetectors.length; i++)
            {
                if (failureDetectors[i].isClosed())
                {
                    workcount += 1;
                    failureDetectors[i].begin(target);
                    break;
                }
            }
        }

        state = STATE_FAILED;
        return workcount;
    }

    protected void updateMember()
    {
        final int idx = members.find(target);
        if (idx > -1)
        {
            members.get(idx, target);
        }
    }

    protected void releaseMember()
    {
        target.locked(false);
        members.set(target);
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

    public boolean isSelectionFailed()
    {
        return state == STATE_SELECTION_FAILED;
    }

    public boolean isFailed()
    {
        return state == STATE_FAILED;
    }
}
