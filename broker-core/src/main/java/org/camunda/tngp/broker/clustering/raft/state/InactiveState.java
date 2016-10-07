package org.camunda.tngp.broker.clustering.raft.state;

import java.util.List;

import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.message.ConfigureRequest;
import org.camunda.tngp.broker.clustering.raft.message.ConfigureResponse;
import org.camunda.tngp.broker.clustering.raft.message.JoinRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.Configuration;
import org.camunda.tngp.broker.clustering.raft.protocol.Member;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft.State;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public class InactiveState extends RaftState
{
    public InactiveState(Raft raft, LogStreamState logStreamState)
    {
        super(raft, logStreamState);
    }

    @Override
    public ConfigureResponse configure(ConfigureRequest configureRequest)
    {
        final int term = configureRequest.term();
        final long configurationEntryPosition = configureRequest.configurationEntryPosition();
        final int configurationEntryTerm = configureRequest.configurationEntryTerm();
        final List<Member> members = configureRequest.members();

        updateTermAndLeader(term, null);

        raft.configure(new Configuration(configurationEntryPosition, configurationEntryTerm, members));

        if (raft.commitPosition() >= raft.configuration().configurationEntryPosition())
        {
            // TODO: store to file!
        }

        configureResponse.reset();
        return configureResponse;
    }

    @Override
    public VoteResponse vote(VoteRequest voteRequest)
    {
        voteResponse.reset();
        voteResponse
            .log(raft.stream().getId())
            .term(raft.term())
            .granted(false);
        return voteResponse;
    }

    @Override
    public void join(JoinRequest joinRequest, DeferredResponse response)
    {
        joinResponse.reset();
        joinResponse
            .log(raft.stream().getId())
            .term(raft.term())
            .status(false)
            .members(raft.members());
//        return joinResponse;
    }

    @Override
    public AppendResponse append(AppendRequest appendRequest)
    {
        appendResponse.reset();
        appendResponse
            .log(raft.stream().getId())
            .term(raft.term())
            .succeeded(false);
        return appendResponse;
    }

    @Override
    public State state()
    {
        return State.INACTIVE;
    }

}
