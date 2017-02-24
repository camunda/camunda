package org.camunda.tngp.broker.clustering.management.handler;

import static org.camunda.tngp.transport.protocol.Protocols.*;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.management.ClusterManager;
import org.camunda.tngp.clustering.management.InvitationRequestEncoder;
import org.camunda.tngp.clustering.management.InvitationResponseDecoder;
import org.camunda.tngp.clustering.management.MessageHeaderDecoder;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.transport.singlemessage.SingleMessageHeaderDescriptor;

public class ClusterManagerFragmentHandler implements FragmentHandler
{
    private final TransportHeaderDescriptor requestTransportHeaderDescriptor = new TransportHeaderDescriptor();
    private final RequestResponseProtocolHeaderDescriptor requestResponseProtocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();

    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

    private final ClusterManager clusterManager;
    private final Subscription subscription;

    public ClusterManagerFragmentHandler(final ClusterManager clusterManager, final Subscription subscription)
    {
        this.clusterManager = clusterManager;
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

        if (InvitationResponseDecoder.SCHEMA_ID == schemaId)
        {
            final int templateId = messageHeaderDecoder.templateId();
            switch (templateId)
            {
                case InvitationRequestEncoder.TEMPLATE_ID:
                {
                    result = clusterManager.onInvitationRequest(buffer, messageOffset, messageLength, streamId, connectionId, requestId);
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
