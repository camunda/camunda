package org.camunda.tngp.broker.clustering.raft.handler;

import static org.camunda.tngp.transport.protocol.Protocols.*;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.clustering.raft.AppendRequestDecoder;
import org.camunda.tngp.clustering.raft.AppendResponseDecoder;
import org.camunda.tngp.clustering.raft.ConfigureRequestDecoder;
import org.camunda.tngp.clustering.raft.JoinRequestDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.VoteRequestDecoder;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.transport.singlemessage.SingleMessageHeaderDescriptor;

public class RaftFragmentHandler implements FragmentHandler
{
    private final TransportHeaderDescriptor requestTransportHeaderDescriptor = new TransportHeaderDescriptor();
    private final RequestResponseProtocolHeaderDescriptor requestResponseProtocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    private final Raft raft;
    private final Subscription subscription;

    public RaftFragmentHandler(final Raft raft, final Subscription subscription)
    {
        this.raft = raft;
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

        headerDecoder.wrap(buffer, messageOffset);

        final int schemaId = headerDecoder.schemaId();

        if (AppendRequestDecoder.SCHEMA_ID == schemaId)
        {
            result = CONSUME_FRAGMENT_RESULT;

            final int id = buffer.getShort(messageOffset + headerDecoder.encodedLength());

            if (raft.id() == id)
            {
                raft.lastContact(System.currentTimeMillis());

                final int templateId = headerDecoder.templateId();
                switch (templateId)
                {
                    case AppendRequestDecoder.TEMPLATE_ID:
                    {

                        result = raft.onAppendRequest(buffer, messageOffset, messageLength, streamId);
                        break;
                    }
                    case AppendResponseDecoder.TEMPLATE_ID:
                    {

                        result = raft.onAppendResponse(buffer, messageOffset, messageLength);
                        break;
                    }
                    case VoteRequestDecoder.TEMPLATE_ID:
                    {
                        result = raft.onVoteRequest(buffer, messageOffset, messageLength, streamId, connectionId, requestId);
                        break;
                    }
                    case JoinRequestDecoder.TEMPLATE_ID:
                    {
                        result = raft.onJoinRequest(buffer, messageOffset, messageLength, streamId, connectionId, requestId);
                        break;
                    }
                    case ConfigureRequestDecoder.TEMPLATE_ID:
                    {
                        result = raft.onConfigureRequest(buffer, messageOffset, messageLength, streamId, connectionId, requestId);
                        break;
                    }
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
