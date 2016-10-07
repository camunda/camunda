package org.camunda.tngp.broker.clustering.gossip.channel;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.function.BiFunction;

import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SystemEpochClock;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.transport.ClientChannelBuilder;
import org.camunda.tngp.transport.Transport;

public class ClientChannelManager
{
    public static final BiFunction<Transport, InetSocketAddress, ClientChannelBuilder> DEFAULT_CHANNEL_FACTORY = (tr, addr) ->
    {
        return tr.createClientChannel(addr);
    };

    protected final Transport transport;
    protected final BiFunction<Transport, InetSocketAddress, ClientChannelBuilder> clientChannelFactory;

    protected final ArrayList<EndpointChannel> channels;
    protected final int capacity;

    protected final EpochClock clock = new SystemEpochClock();

    public ClientChannelManager(final int capacity, final Transport transport)
    {
        this(capacity, transport, DEFAULT_CHANNEL_FACTORY);
    }

    public ClientChannelManager(
            final int capacity,
            final Transport transport,
            final BiFunction<Transport, InetSocketAddress, ClientChannelBuilder> clientChannelFactory)
    {
        this.transport = transport;
        this.clientChannelFactory = clientChannelFactory;
        this.capacity = capacity;
        this.channels = new ArrayList<EndpointChannel>(capacity);
    }

    public EndpointChannel claim(final Endpoint endpoint)
    {
        final int idx = findEndpointChannel(endpoint);
        EndpointChannel endpointChannel = null;

        if (idx < 0)
        {
            ensureCapacity();

            final Endpoint copy = endpoint.copy();
            endpointChannel = new EndpointChannel(copy);
            endpointChannel.open(transport, clientChannelFactory);

            channels.add(endpointChannel);
        }
        else
        {
            endpointChannel = channels.get(idx);

            if (endpointChannel != null)
            {
                if ((endpointChannel.isConnected() && !endpointChannel.isClientChannelOpen()) || endpointChannel.isFailed())
                {
                    // recycle endpoint channel
                    endpointChannel.close();
                    final Endpoint copy = endpoint.copy();
                    endpointChannel = new EndpointChannel(copy);
                    endpointChannel.open(transport, clientChannelFactory);
                }
            }
        }

        endpointChannel.setLastUsedTime(clock.time());
        endpointChannel.incrementReferenceCount();

        return endpointChannel;
    }

    public void reclaim(final EndpointChannel endpointChannel)
    {
        if (endpointChannel != null)
        {
            endpointChannel.decrementReferenceCount();
        }
    }

    protected void ensureCapacity()
    {
        if (channels.size() == capacity)
        {
            long lastUsed = clock.time();
            int idx = -1;

            // find channel to close
            for (int i = 0; i < channels.size(); i++)
            {
                final EndpointChannel channel = channels.get(i);
                if (!channel.isReferenced() && channel.getLastUsedTime() < lastUsed)
                {
                    lastUsed = channel.lastUsedTime;
                    idx = i;
                }
            }

            final EndpointChannel endpointChannel = channels.get(idx);
            endpointChannel.close();
            channels.remove(endpointChannel);
        }
    }

    protected int findEndpointChannel(final Endpoint endpoint)
    {
        int idx = -1;
        for (int i = 0; i < channels.size(); i++)
        {
            final EndpointChannel endpointChannel = channels.get(i);
            if (endpoint.equals(endpointChannel.getEndpoint()))
            {
                idx = i;
                break;
            }
        }

        return idx;
    }

}
