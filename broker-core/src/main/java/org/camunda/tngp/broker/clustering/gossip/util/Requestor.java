package org.camunda.tngp.broker.clustering.gossip.util;

import java.util.concurrent.TimeUnit;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SystemEpochClock;
import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.gossip.channel.EndpointChannel;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.requestresponse.client.PooledTransportRequest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.util.buffer.BufferWriter;

public class Requestor
{
    protected static final int STATE_CLOSED = 0;
    protected static final int STATE_CONNECTING = 1;
    protected static final int STATE_SENDING = 2;
    protected static final int STATE_OPEN = 3;
    protected static final int STATE_FAILED = 4;
    protected static final int STATE_RESPONSE_AVAILABLE = 5;

    protected int state = STATE_CLOSED;

    protected final EpochClock clock = new SystemEpochClock();
    protected int requestTimeout;

    protected final ClientChannelManager clientChannelManager;
    protected final TransportConnection connection;
    protected BufferWriter writer;

    protected EndpointChannel endpointChannel;
    protected ClientChannel channel;
    protected PooledTransportRequest request;

    protected final Endpoint recipient = new Endpoint();

    public Requestor(
            final ClientChannelManager clientChannelManager,
            final TransportConnection connection)
    {
        this(clientChannelManager, connection, -1);
    }

    public Requestor(
            final ClientChannelManager clientChannelManager,
            final TransportConnection connection,
            final int requestTimeout)
    {
        this.clientChannelManager = clientChannelManager;
        this.connection = connection;
        this.requestTimeout = requestTimeout;
    }

    public void begin(final Endpoint recipient, final BufferWriter writer)
    {
        if (state == STATE_CLOSED)
        {
            this.writer = writer;
            this.recipient.wrap(recipient);
            state = STATE_CONNECTING;
        }
        else
        {
            throw new IllegalStateException("Cannot open requestor, has not been closed.");
        }
    }

    public int execute()
    {
        int workcount = 0;

        switch (state)
        {
            case STATE_CONNECTING:
            {
                workcount += processConnecting();
                break;
            }

            case STATE_SENDING:
            {
                workcount += processSending();
                break;
            }

            case STATE_OPEN:
            {
                workcount += pollResponse();
                break;
            }

        }

        return workcount;
    }

    public void close()
    {
        try
        {
            clientChannelManager.reclaim(endpointChannel);

            if (request != null)
            {
                request.close();
            }
        }
        finally
        {
            this.endpointChannel = null;
            this.channel = null;
            this.request = null;
            this.writer = null;
            this.state = STATE_CLOSED;
        }
    }

    protected int processConnecting()
    {
        int workcount = 0;

        if (endpointChannel == null || endpointChannel.isClosed())
        {
            endpointChannel = clientChannelManager.claim(recipient);
        }

        try
        {
            channel = endpointChannel.getClientChannel();

            if (channel != null)
            {
                state = STATE_SENDING;
                workcount += 1;
                workcount += processSending();
            }

        }
        catch (final Exception e)
        {
            workcount += 1;
            state = STATE_FAILED;
        }

        return workcount;
    }

    protected int processSending()
    {
        int workcount = 0;

        final int channelId = channel.getId();
        final int length = writer.getLength();

        request = connection.openRequest(channelId, length, TimeUnit.SECONDS.toMillis(requestTimeout));

        if (request != null)
        {
            final int claimedOffset = request.getClaimedOffset();
            final MutableDirectBuffer claimedRequestBuffer = request.getClaimedRequestBuffer();

            writer.write(claimedRequestBuffer, claimedOffset);
            request.commit();

            workcount += 1;
            state = STATE_OPEN;
        }

        return workcount;
    }

    protected int pollResponse()
    {
        int workcount = 0;

        boolean isResponseAvailable = false;

        try
        {
            isResponseAvailable = request.pollResponse();
        }
        catch (final Exception e)
        {
            state = STATE_FAILED;
        }

        if (isResponseAvailable)
        {
            workcount += 1;
            state = STATE_RESPONSE_AVAILABLE;
        }

        return workcount;
    }

    public Endpoint getRecipient()
    {
        return recipient;
    }

    public DirectBuffer getResponseBuffer()
    {
        return request.getResponseBuffer();
    }

    public int getResponseLength()
    {
        return request.getResponseLength();
    }

    public boolean isFailed()
    {
        return state == STATE_FAILED;
    }

    public boolean isResponseAvailable()
    {
        return state == STATE_RESPONSE_AVAILABLE;
    }

}
