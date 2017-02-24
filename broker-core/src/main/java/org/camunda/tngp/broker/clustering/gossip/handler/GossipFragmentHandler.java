package org.camunda.tngp.broker.clustering.gossip.handler;

import static org.camunda.tngp.transport.protocol.Protocols.*;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.Gossip;
import org.camunda.tngp.clustering.gossip.GossipDecoder;
import org.camunda.tngp.clustering.gossip.MessageHeaderDecoder;
import org.camunda.tngp.clustering.gossip.ProbeDecoder;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.transport.singlemessage.SingleMessageHeaderDescriptor;

public class GossipFragmentHandler implements FragmentHandler
{
    private final TransportHeaderDescriptor requestTransportHeaderDescriptor = new TransportHeaderDescriptor();
    private final RequestResponseProtocolHeaderDescriptor requestResponseProtocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();

    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

    private final Gossip gossip;
    private final Subscription subscription;

    public GossipFragmentHandler(final Gossip gossip, final Subscription subscription)
    {
        this.gossip = gossip;
        this.subscription = subscription;
    }

    public int doWork()
    {
        return subscription.poll(this, Integer.MAX_VALUE);
    }

    @Override
    public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed)
    {
        int result = POSTPONE_FRAGMENT_RESULT;

        int messageOffset = offset + TransportHeaderDescriptor.headerLength();
        int messageLength = length - TransportHeaderDescriptor.headerLength();

        requestTransportHeaderDescriptor.wrap(buffer, offset);

        long connectionId = -1L;
        long requestId = -1L;

        final int protocol = requestTransportHeaderDescriptor.protocolId();
        switch (protocol)
        {
            case REQUEST_RESPONSE:
            {
                requestResponseProtocolHeaderDescriptor.wrap(buffer, messageOffset);

                connectionId = requestResponseProtocolHeaderDescriptor.connectionId();
                requestId = requestResponseProtocolHeaderDescriptor.requestId();

                messageOffset += RequestResponseProtocolHeaderDescriptor.headerLength();
                messageLength -= RequestResponseProtocolHeaderDescriptor.headerLength();
                break;
            }
            case FULL_DUPLEX_SINGLE_MESSAGE:
            {
                messageOffset += SingleMessageHeaderDescriptor.HEADER_LENGTH;
                messageLength -= SingleMessageHeaderDescriptor.HEADER_LENGTH;
                break;
            }
            default:
            {
                // TODO: respond with an error
                result = CONSUME_FRAGMENT_RESULT;
                return result;
            }
        }

        messageHeaderDecoder.wrap(buffer, messageOffset);

        final int schemaId = messageHeaderDecoder.schemaId();

        if (GossipDecoder.SCHEMA_ID == schemaId)
        {
            final int templateId = messageHeaderDecoder.templateId();
            switch (templateId)
            {
                case GossipDecoder.TEMPLATE_ID:
                {

                    result = gossip.onGossipRequest(buffer, messageOffset, messageLength, streamId, connectionId, requestId);
                    break;
                }
                case ProbeDecoder.TEMPLATE_ID:
                {
                    result = gossip.onProbeRequest(buffer, messageOffset, messageLength, streamId, connectionId, requestId);
                    break;
                }
                default:
                {
                    // TODO: send error response
                    result = CONSUME_FRAGMENT_RESULT;
                    break;
                }
            }
        }
        else
        {
            result = CONSUME_FRAGMENT_RESULT;
        }

        return result;
    }

}
