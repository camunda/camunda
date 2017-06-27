package io.zeebe.broker.clustering.gossip.handler;

import org.agrona.DirectBuffer;

import io.zeebe.broker.clustering.gossip.protocol.GossipController;
import io.zeebe.clustering.gossip.GossipDecoder;
import io.zeebe.clustering.gossip.MessageHeaderDecoder;
import io.zeebe.clustering.gossip.ProbeDecoder;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;

public class GossipFragmentHandler implements ServerMessageHandler, ServerRequestHandler
{
    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final GossipController gossipController;

    public GossipFragmentHandler(final GossipController gossipController)
    {
        this.gossipController = gossipController;
    }

//    @Override
//    public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed)
//    {
//        int result = POSTPONE_FRAGMENT_RESULT;
//
//        int messageOffset = offset + TransportHeaderDescriptor.headerLength();
//        int messageLength = length - TransportHeaderDescriptor.headerLength();
//
//        requestTransportHeaderDescriptor.wrap(buffer, offset);
//
//        long connectionId = -1L;
//        long requestId = -1L;
//
//        final int protocol = requestTransportHeaderDescriptor.protocolId();
//        switch (protocol)
//        {
//            case REQUEST_RESPONSE:
//            {
//                requestResponseProtocolHeaderDescriptor.wrap(buffer, messageOffset);
//
//                connectionId = requestResponseProtocolHeaderDescriptor.connectionId();
//                requestId = requestResponseProtocolHeaderDescriptor.requestId();
//
//                messageOffset += RequestResponseProtocolHeaderDescriptor.headerLength();
//                messageLength -= RequestResponseProtocolHeaderDescriptor.headerLength();
//                break;
//            }
//            case FULL_DUPLEX_SINGLE_MESSAGE:
//            {
//                messageOffset += SingleMessageHeaderDescriptor.HEADER_LENGTH;
//                messageLength -= SingleMessageHeaderDescriptor.HEADER_LENGTH;
//                break;
//            }
//            default:
//            {
//                // TODO: respond with an error
//                result = CONSUME_FRAGMENT_RESULT;
//                return result;
//            }
//        }
//
//        messageHeaderDecoder.wrap(buffer, messageOffset);
//
//        final int schemaId = messageHeaderDecoder.schemaId();
//
//        if (GossipDecoder.SCHEMA_ID == schemaId)
//        {
//            final int templateId = messageHeaderDecoder.templateId();
//            switch (templateId)
//            {
//                case GossipDecoder.TEMPLATE_ID:
//                {
//
//                    result = gossipController.onGossipRequest(buffer, messageOffset, messageLength, streamId, connectionId, requestId);
//                    break;
//                }
//                case ProbeDecoder.TEMPLATE_ID:
//                {
//                    result = gossipController.onProbeRequest(buffer, messageOffset, messageLength, streamId, connectionId, requestId);
//                    break;
//                }
//                default:
//                {
//                    // TODO: send error response
//                    result = CONSUME_FRAGMENT_RESULT;
//                    break;
//                }
//            }
//        }
//        else
//        {
//            result = CONSUME_FRAGMENT_RESULT;
//        }
//
//        return result;
//    }

    @Override
    public boolean onRequest(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset,
            int length, long requestId)
    {
        messageHeaderDecoder.wrap(buffer, offset);

        final int schemaId = messageHeaderDecoder.schemaId();

        if (GossipDecoder.SCHEMA_ID == schemaId)
        {
            final int templateId = messageHeaderDecoder.templateId();
            switch (templateId)
            {
                case GossipDecoder.TEMPLATE_ID:
                {
                    return gossipController.onGossipRequest(buffer, offset, length, output, remoteAddress, requestId);
                }
                case ProbeDecoder.TEMPLATE_ID:
                {
                    return gossipController.onProbeRequest(buffer, offset, length, output, remoteAddress, requestId);
                }
                default:
                {
                    // TODO: send error response
                    return true;
                }
            }
        }
        else
        {
            return true;
        }
    }

    @Override
    public boolean onMessage(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset,
            int length)
    {
        // ignore; does not do single message interactions
        return true;
    }

}
