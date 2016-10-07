package org.camunda.tngp.broker.clustering.gossip.protocol;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.GossipProtocol;
import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.message.GossipReader;
import org.camunda.tngp.broker.clustering.gossip.message.GossipWriter;
import org.camunda.tngp.broker.clustering.gossip.util.Requestor;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public class Probe
{
    protected static final int STATE_CLOSED = 0;
    protected static final int STATE_OPEN = 1;
    protected static final int STATE_ACKNOWLEDGED = 2;
    protected static final int STATE_FAILED = 3;

    protected int state = STATE_CLOSED;

    protected final GossipProtocol gossipProtocol;
    protected final Requestor requestor;
    protected final PeerList members;

    protected final GossipReader reader = new GossipReader();
    protected final GossipWriter writer;

    protected final Peer target = new Peer();

    protected DeferredResponse response;

    public Probe(final GossipProtocol gossipProtocol, final int probeTimeout)
    {
        this.gossipProtocol = gossipProtocol;
        this.members = gossipProtocol.getMembers();
        this.writer = new GossipWriter(members);

        final ClientChannelManager channelManager = gossipProtocol.getClientChannelManager();
        final TransportConnection connection = gossipProtocol.getConnection();
        this.requestor = new Requestor(channelManager, connection, probeTimeout);
    }

    public void begin(final Peer member, final DeferredResponse response)
    {
        if (state == STATE_CLOSED)
        {
            this.response = response;
            this.target.wrap(member);
//            System.out.println("[PROBE OPEN] now: " + System.currentTimeMillis() + ", endpoint: " + target.endpoint());
            this.requestor.begin(member.endpoint(), writer);

            response.defer();
            state = STATE_OPEN;
        }
        else
        {
            throw new IllegalStateException("Cannot open prober, has not been closed.");
        }
    }

    public int execute()
    {
        int workcount = 0;

        if (state == STATE_OPEN)
        {
            workcount += requestor.execute();

            if (requestor.isResponseAvailable())
            {
//                System.out.println("[PROBE ACK] now: " + System.currentTimeMillis() + ", endpoint: " + target.endpoint());
                workcount += 1;
                workcount += executeResponse();
            }
            else if (requestor.isFailed())
            {
//                System.out.println("[PROBE FAILED] now: " + System.currentTimeMillis() + ", endpoint: " + target.endpoint());
                workcount += 1;
                workcount += executeFailed();
            }
        }

        return workcount;
    }

    protected int executeResponse()
    {
        int workcount = 0;

        final DirectBuffer responseBuffer = requestor.getResponseBuffer();
        final int responseLength = requestor.getResponseLength();

        reader.wrap(responseBuffer, 0, responseLength);
        members.merge(reader);

        if (response.allocateAndWrite(writer))
        {
            workcount += 1;
            response.commit();
        }
        else
        {
            response.abort();
        }

        state = STATE_ACKNOWLEDGED;
        return workcount;
    }

    protected int executeFailed()
    {
        state = STATE_FAILED;
        return 0;
    }

    public void close()
    {
        try
        {
            requestor.close();
        }
        finally
        {
            response = null;
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
