package org.camunda.tngp.broker.clustering.raft.handler;

import static org.camunda.tngp.transport.protocol.Protocols.*;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.message.ConfigureRequest;
import org.camunda.tngp.broker.clustering.raft.message.ConfigureResponse;
import org.camunda.tngp.broker.clustering.raft.message.JoinRequest;
import org.camunda.tngp.broker.clustering.raft.message.LeaveRequest;
import org.camunda.tngp.broker.clustering.raft.message.PollRequest;
import org.camunda.tngp.broker.clustering.raft.message.PollResponse;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.util.MessageWriter;
import org.camunda.tngp.clustering.raft.AppendRequestDecoder;
import org.camunda.tngp.clustering.raft.AppendResponseDecoder;
import org.camunda.tngp.clustering.raft.ConfigureRequestDecoder;
import org.camunda.tngp.clustering.raft.JoinRequestDecoder;
import org.camunda.tngp.clustering.raft.LeaveRequestDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.PollRequestDecoder;
import org.camunda.tngp.clustering.raft.VoteRequestDecoder;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.transport.singlemessage.SingleMessageHeaderDescriptor;

public class RaftMessageHandler implements FragmentHandler
{
    private final TransportHeaderDescriptor requestTransportHeaderDescriptor = new TransportHeaderDescriptor();
    private final RequestResponseProtocolHeaderDescriptor requestResponseProtocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    private final Raft raft;
    private final Subscription subscription;

    private final MessageWriter messageWriter;

    private final AppendRequest appendRequest;
    private final AppendResponse appendResponse;
    private final VoteRequest voteRequest;
    private final PollRequest pollRequest;
    private final JoinRequest joinRequest;
    private final LeaveRequest leaveRequest;
    private final ConfigureRequest configureRequest;

    public RaftMessageHandler(final RaftContext context, final Subscription subscription)
    {
        this.raft = context.getRaft();
        this.subscription = subscription;
        this.messageWriter = new MessageWriter(context.getSendBuffer());

        this.appendRequest = new AppendRequest();
        this.appendResponse = new AppendResponse();
        this.voteRequest = new VoteRequest();
        this.pollRequest = new PollRequest();
        this.joinRequest = new JoinRequest();
        this.leaveRequest = new LeaveRequest();
        this.configureRequest = new ConfigureRequest();
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

        final int protocol = requestTransportHeaderDescriptor.protocolId();
        switch (protocol)
        {
            case REQUEST_RESPONSE:
            {
                requestResponseProtocolHeaderDescriptor.wrap(buffer, messageOffset);

                final long connectionId = requestResponseProtocolHeaderDescriptor.connectionId();
                final long requestId = requestResponseProtocolHeaderDescriptor.requestId();

                messageOffset += RequestResponseProtocolHeaderDescriptor.headerLength();
                messageLength -= RequestResponseProtocolHeaderDescriptor.headerLength();
                result = handleRequestResponseMessage(buffer, messageOffset, messageLength, streamId, connectionId, requestId);
                break;
            }
            case FULL_DUPLEX_SINGLE_MESSAGE:
            {
                messageOffset += SingleMessageHeaderDescriptor.HEADER_LENGTH;
                messageLength -= SingleMessageHeaderDescriptor.HEADER_LENGTH;
                result = handleSingleMessage(buffer, messageOffset, messageLength, streamId);
                break;
            }
            default:
            {
                // TODO: respond with an error
                result = CONSUME_FRAGMENT_RESULT;
            }
        }

        return result;
    }

    private int handleSingleMessage(final DirectBuffer buffer, final int offset, final int length, final int channeldId)
    {
        final int result = CONSUME_FRAGMENT_RESULT;

        headerDecoder.wrap(buffer, offset);

        final int schemaId = headerDecoder.schemaId();

        if (AppendRequestDecoder.SCHEMA_ID == schemaId)
        {
            final int id = buffer.getShort(offset + headerDecoder.encodedLength());

            if (raft.id() == id)
            {
                raft.lastContact(System.currentTimeMillis());

                final int templateId = headerDecoder.templateId();

                switch (templateId)
                {
                    case AppendRequestDecoder.TEMPLATE_ID:
                    {
                        appendRequest.reset();
                        appendRequest.wrap(buffer, offset, length);
                        final AppendResponse appendResponse = raft.handleAppendRequest(appendRequest);

                        messageWriter.protocol(FULL_DUPLEX_SINGLE_MESSAGE)
                            .channelId(channeldId)
                            .message(appendResponse)
                            .tryWriteMessage();
                        break;
                    }

                    case AppendResponseDecoder.TEMPLATE_ID:
                    {
                        appendResponse.reset();
                        appendResponse.wrap(buffer, offset, length);
                        raft.handleAppendResponse(appendResponse);
                        break;
                    }
                    default:
                    {
                        // TODO: respond with an error
                    }
                }

            }
        }

        return result;
    }

    private int handleRequestResponseMessage(final DirectBuffer buffer, final int offset, final int length, final int channeldId, final long connectionId, final long requestId)
    {
        final int result = CONSUME_FRAGMENT_RESULT;

        headerDecoder.wrap(buffer, offset);

        final int schemaId = headerDecoder.schemaId();

        if (AppendRequestDecoder.SCHEMA_ID == schemaId)
        {
            final int id = buffer.getShort(offset + headerDecoder.encodedLength());

            if (raft.id() == id)
            {
                raft.lastContact(System.currentTimeMillis());

                final int templateId = headerDecoder.templateId();

                switch (templateId)
                {
                    case VoteRequestDecoder.TEMPLATE_ID:
                    {
                        voteRequest.reset();
                        voteRequest.wrap(buffer, offset, length);
                        final VoteResponse voteResponse = raft.handleVoteRequest(voteRequest);

                        messageWriter.protocol(REQUEST_RESPONSE)
                            .channelId(channeldId)
                            .connectionId(connectionId)
                            .requestId(requestId)
                            .message(voteResponse)
                            .tryWriteMessage();
                        break;
                    }

                    case PollRequestDecoder.TEMPLATE_ID:
                    {
                        pollRequest.reset();
                        pollRequest.wrap(buffer, offset, length);
                        final PollResponse pollResponse = raft.handlePollRequest(pollRequest);

                        messageWriter.protocol(REQUEST_RESPONSE)
                            .channelId(channeldId)
                            .connectionId(connectionId)
                            .requestId(requestId)
                            .message(pollResponse)
                            .tryWriteMessage();
                        break;
                    }

                    case JoinRequestDecoder.TEMPLATE_ID:
                    {
                        joinRequest.reset();
                        joinRequest.wrap(buffer, offset, length);
                        raft.handleJoinRequest(joinRequest)
                            .thenAccept((response) ->
                            {
                                messageWriter.protocol(REQUEST_RESPONSE)
                                    .channelId(channeldId)
                                    .connectionId(connectionId)
                                    .requestId(requestId)
                                    .message(response)
                                    .tryWriteMessage();
                            });
                        break;
                    }

                    case LeaveRequestDecoder.TEMPLATE_ID:
                    {
                        leaveRequest.reset();
                        leaveRequest.wrap(buffer, offset, length);
                        raft.handleLeaveRequest(leaveRequest)
                            .thenAccept((response) ->
                            {
                                messageWriter.protocol(REQUEST_RESPONSE)
                                    .channelId(channeldId)
                                    .connectionId(connectionId)
                                    .requestId(requestId)
                                    .message(response)
                                    .tryWriteMessage();
                            });
                        break;
                    }

                    case ConfigureRequestDecoder.TEMPLATE_ID:
                    {
                        configureRequest.reset();
                        configureRequest.wrap(buffer, offset, length);
                        final ConfigureResponse response = raft.handleConfigureRequest(configureRequest);
                        messageWriter.protocol(REQUEST_RESPONSE)
                            .channelId(channeldId)
                            .connectionId(connectionId)
                            .requestId(requestId)
                            .message(response)
                            .tryWriteMessage();
                        break;
                    }
                    default:
                    {
                        // TODO: respond with an error
                    }
                }
            }
        }

        return result;
    }

}
