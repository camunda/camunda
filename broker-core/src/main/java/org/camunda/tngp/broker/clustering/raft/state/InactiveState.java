package org.camunda.tngp.broker.clustering.raft.state;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.raft.Configuration;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.broker.clustering.raft.Raft.State;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.transport.protocol.Protocols;

public class InactiveState extends RaftState
{
    private boolean open;

    public InactiveState(final RaftContext context)
    {
        super(context);
    }

    @Override
    public State state()
    {
        return State.INACTIVE;
    }

    @Override
    public void open()
    {
        open = true;
    }

    @Override
    public void close()
    {
        open = false;
    }

    @Override
    public int doWork()
    {
        return 0;
    }

    @Override
    public boolean isClosed()
    {
        return !open;
    }

    @Override
    public int onVoteRequest(DirectBuffer buffer, int offset, int length, int channelId, long connectionId, long requestId)
    {
        voteResponse.reset();
        voteResponse
            .id(raft.id())
            .term(raft.term())
            .granted(false);

        messageWriter.protocol(Protocols.REQUEST_RESPONSE)
            .channelId(channelId)
            .connectionId(connectionId)
            .requestId(requestId)
            .message(voteResponse)
            .tryWriteMessage();

        return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }

    @Override
    public int onAppendRequest(DirectBuffer buffer, int offset, int length, int channelId)
    {
        appendResponse.reset();
        appendResponse
            .id(raft.id())
            .term(raft.term())
            .succeeded(false)
            .entryPosition(logStreamState.lastReceivedPosition());

        messageWriter.protocol(Protocols.FULL_DUPLEX_SINGLE_MESSAGE)
            .channelId(channelId)
            .message(appendResponse)
            .tryWriteMessage();

        return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }

    @Override
    public int onAppendResponse(final DirectBuffer buffer, final int offset, final int length)
    {
        return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }

    @Override
    public int onJoinRequest(DirectBuffer buffer, int offset, int length, int channelId, long connectionId, long requestId)
    {
        joinResponse.reset();
        joinResponse
            .id(raft.id())
            .term(raft.term())
            .succeeded(false);

        messageWriter.protocol(Protocols.REQUEST_RESPONSE)
            .channelId(channelId)
            .connectionId(connectionId)
            .requestId(requestId)
            .message(joinResponse)
            .tryWriteMessage();

        return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }

    @Override
    public int onConfigureRequest(DirectBuffer buffer, int offset, int length, int channelId, long connectionId, long requestId)
    {
        configureRequest.reset();
        configureRequest.wrap(buffer, offset, length);

        final int term = configureRequest.term();
        final long configurationEntryPosition = configureRequest.configurationEntryPosition();
        final int configurationEntryTerm = configureRequest.configurationEntryTerm();
        final List<Member> members = configureRequest.members();

        updateTermAndLeader(term, null);

        raft.configure(new Configuration(configurationEntryPosition, configurationEntryTerm, new CopyOnWriteArrayList<>(members)));

        if (raft.commitPosition() >= raft.configuration().configurationEntryPosition())
        {
            // TODO: store to file!
        }

        configureResponse.reset();
        configureResponse.reset();
        configureResponse
            .id(raft.id())
            .term(raft.term());

        messageWriter.protocol(Protocols.REQUEST_RESPONSE)
            .channelId(channelId)
            .connectionId(connectionId)
            .requestId(requestId)
            .message(configureResponse)
            .tryWriteMessage();

        return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }

}
