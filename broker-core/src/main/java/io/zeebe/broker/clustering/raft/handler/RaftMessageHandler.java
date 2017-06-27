package io.zeebe.broker.clustering.raft.handler;

import org.agrona.DirectBuffer;

import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.broker.clustering.raft.RaftContext;
import io.zeebe.broker.clustering.raft.message.AppendRequest;
import io.zeebe.broker.clustering.raft.message.AppendResponse;
import io.zeebe.broker.clustering.raft.message.ConfigureRequest;
import io.zeebe.broker.clustering.raft.message.ConfigureResponse;
import io.zeebe.broker.clustering.raft.message.JoinRequest;
import io.zeebe.broker.clustering.raft.message.LeaveRequest;
import io.zeebe.broker.clustering.raft.message.PollRequest;
import io.zeebe.broker.clustering.raft.message.PollResponse;
import io.zeebe.broker.clustering.raft.message.VoteRequest;
import io.zeebe.broker.clustering.raft.message.VoteResponse;
import io.zeebe.clustering.raft.AppendRequestDecoder;
import io.zeebe.clustering.raft.AppendResponseDecoder;
import io.zeebe.clustering.raft.ConfigureRequestDecoder;
import io.zeebe.clustering.raft.JoinRequestDecoder;
import io.zeebe.clustering.raft.LeaveRequestDecoder;
import io.zeebe.clustering.raft.MessageHeaderDecoder;
import io.zeebe.clustering.raft.PollRequestDecoder;
import io.zeebe.clustering.raft.VoteRequestDecoder;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;
import io.zeebe.transport.ServerResponse;
import io.zeebe.transport.TransportMessage;

public class RaftMessageHandler implements ServerMessageHandler, ServerRequestHandler
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    private final Raft raft;

    private final TransportMessage message = new TransportMessage();
    private final ServerResponse response = new ServerResponse();

    private final AppendRequest appendRequest;
    private final AppendResponse appendResponse;
    private final VoteRequest voteRequest;
    private final PollRequest pollRequest;
    private final JoinRequest joinRequest;
    private final LeaveRequest leaveRequest;
    private final ConfigureRequest configureRequest;

    public RaftMessageHandler(final RaftContext context)
    {
        this.raft = context.getRaft();
        this.appendRequest = new AppendRequest();
        this.appendResponse = new AppendResponse();
        this.voteRequest = new VoteRequest();
        this.pollRequest = new PollRequest();
        this.joinRequest = new JoinRequest();
        this.leaveRequest = new LeaveRequest();
        this.configureRequest = new ConfigureRequest();
    }

    protected boolean canHandleMessage(final DirectBuffer topicName, final int partitionId)
    {
        final LogStream logStream = raft.stream();

        return logStream.getTopicName().equals(topicName) && logStream.getPartitionId() == partitionId;
    }

    @Override
    public boolean onRequest(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset,
            int length, long requestId)
    {
        headerDecoder.wrap(buffer, offset);

        final int schemaId = headerDecoder.schemaId();

        if (AppendRequestDecoder.SCHEMA_ID == schemaId)
        {
            final int templateId = headerDecoder.templateId();

            switch (templateId)
            {
                case VoteRequestDecoder.TEMPLATE_ID:
                {
                    voteRequest.reset();
                    voteRequest.wrap(buffer, offset, length);

                    if (canHandleMessage(voteRequest.topicName(), voteRequest.partitionId()))
                    {
                        raft.lastContactNow();

                        final VoteResponse voteResponse = raft.handleVoteRequest(voteRequest);

                        response.reset()
                            .remoteAddress(remoteAddress)
                            .requestId(requestId)
                            .writer(voteResponse);

                        return output.sendResponse(response);
                    }

                    break;
                }

                case PollRequestDecoder.TEMPLATE_ID:
                {
                    pollRequest.reset();
                    pollRequest.wrap(buffer, offset, length);

                    if (canHandleMessage(pollRequest.topicName(), pollRequest.partitionId()))
                    {
                        raft.lastContactNow();

                        final PollResponse pollResponse = raft.handlePollRequest(pollRequest);

                        response.reset()
                            .remoteAddress(remoteAddress)
                            .requestId(requestId)
                            .writer(pollResponse);

                        return output.sendResponse(response);
                    }

                    break;
                }

                case JoinRequestDecoder.TEMPLATE_ID:
                {
                    joinRequest.reset();
                    joinRequest.wrap(buffer, offset, length);

                    if (canHandleMessage(joinRequest.topicName(), joinRequest.partitionId()))
                    {
                        raft.lastContactNow();

                        raft.handleJoinRequest(joinRequest)
                            .thenAccept((joinResponse) ->
                            {
                                response.reset()
                                    .remoteAddress(remoteAddress)
                                    .requestId(requestId)
                                    .writer(joinResponse);

                                output.sendResponse(response);
                            });

                        return true;
                    }

                    break;
                }

                case LeaveRequestDecoder.TEMPLATE_ID:
                {
                    leaveRequest.reset();
                    leaveRequest.wrap(buffer, offset, length);

                    if (canHandleMessage(leaveRequest.topicName(), leaveRequest.partitionId()))
                    {
                        raft.lastContactNow();

                        raft.handleLeaveRequest(leaveRequest)
                            .thenAccept((leaveResponse) ->
                            {
                                response.reset()
                                    .remoteAddress(remoteAddress)
                                    .requestId(requestId)
                                    .writer(leaveResponse);

                                output.sendResponse(response);
                            });

                        return true;

                    }
                    break;
                }

                case ConfigureRequestDecoder.TEMPLATE_ID:
                {
                    configureRequest.reset();
                    configureRequest.wrap(buffer, offset, length);

                    if (canHandleMessage(configureRequest.topicName(), configureRequest.partitionId()))
                    {
                        raft.lastContactNow();

                        final ConfigureResponse configureResponse = raft.handleConfigureRequest(configureRequest);
                        response.reset()
                            .remoteAddress(remoteAddress)
                            .requestId(requestId)
                            .writer(configureResponse);

                        return output.sendResponse(response);
                    }

                    break;
                }
                default:
                {
                    // TODO: respond with an error
                    return true;
                }
            }
        }

        return true;
    }

    @Override
    public boolean onMessage(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset,
            int length)
    {
        headerDecoder.wrap(buffer, offset);

        final int schemaId = headerDecoder.schemaId();

        if (AppendRequestDecoder.SCHEMA_ID == schemaId)
        {
            final int templateId = headerDecoder.templateId();

            switch (templateId)
            {
                case AppendRequestDecoder.TEMPLATE_ID:
                {
                    appendRequest.reset();
                    appendRequest.wrap(buffer, offset, length);

                    if (canHandleMessage(appendRequest.topicName(), appendRequest.partitionId()))
                    {
                        raft.lastContactNow();

                        final AppendResponse appendResponse = raft.handleAppendRequest(appendRequest);

                        message.reset()
                            .remoteAddress(remoteAddress)
                            .writer(appendResponse);

                        return output.sendMessage(message);
                    }

                    break;
                }

                case AppendResponseDecoder.TEMPLATE_ID:
                {
                    appendResponse.reset();
                    appendResponse.wrap(buffer, offset, length);

                    if (canHandleMessage(appendResponse.topicName(), appendResponse.partitionId()))
                    {
                        raft.lastContactNow();

                        raft.handleAppendResponse(appendResponse);
                    }

                    return true;
                }
                default:
                {
                    // TODO: respond with an error
                    return true;
                }
            }
        }

        return true;
    }

}
