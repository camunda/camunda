/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.raft.state;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import io.zeebe.broker.clustering.raft.Configuration;
import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.broker.clustering.raft.RaftContext;
import io.zeebe.broker.clustering.raft.message.AppendRequest;
import io.zeebe.broker.clustering.raft.message.AppendResponse;
import io.zeebe.broker.clustering.raft.message.ConfigureRequest;
import io.zeebe.broker.clustering.raft.message.ConfigureResponse;
import io.zeebe.broker.clustering.raft.message.JoinRequest;
import io.zeebe.broker.clustering.raft.message.JoinResponse;
import io.zeebe.broker.clustering.raft.message.LeaveRequest;
import io.zeebe.broker.clustering.raft.message.LeaveResponse;
import io.zeebe.broker.clustering.raft.message.PollRequest;
import io.zeebe.broker.clustering.raft.message.PollResponse;
import io.zeebe.broker.clustering.raft.message.VoteRequest;
import io.zeebe.broker.clustering.raft.message.VoteResponse;
import io.zeebe.clustering.gossip.RaftMembershipState;


public class InactiveState extends RaftState
{
    private boolean open;

    public InactiveState(final RaftContext context)
    {
        super(context);
    }

    @Override
    public RaftMembershipState state()
    {
        return RaftMembershipState.INACTIVE;
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

    public PollResponse poll(final PollRequest pollRequest)
    {
        throw new IllegalStateException();
    }

    public VoteResponse vote(final VoteRequest voteRequest)
    {
        throw new IllegalStateException();
    }

    public AppendResponse append(final AppendRequest appendRequest)
    {
        throw new IllegalStateException();
    }

    public void appended(final AppendResponse appendResponse)
    {
        throw new IllegalStateException();
    }

    public CompletableFuture<JoinResponse> join(final JoinRequest joinRequest)
    {
        throw new IllegalStateException();
    }

    public CompletableFuture<LeaveResponse> leave(final LeaveRequest leaveRequest)
    {
        throw new IllegalStateException();
    }

    public ConfigureResponse configure(final ConfigureRequest configureRequest)
    {
        final int term = configureRequest.term();
        final long configurationEntryPosition = configureRequest.configurationEntryPosition();
        final int configurationEntryTerm = configureRequest.configurationEntryTerm();
        final List<Member> members = configureRequest.members();

        updateTermAndLeader(term, null);

        raft.configure(new Configuration(
                configurationEntryPosition,
                configurationEntryTerm,
                new CopyOnWriteArrayList<>(members)));

        if (raft.commitPosition() >= raft.configuration().configurationEntryPosition())
        {
            // TODO: store to file!
        }

        configureResponse.reset();
        return configureResponse
            .term(raft.term());
    }

}
