/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.raft.state;

import java.util.Arrays;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.raft.*;
import io.zeebe.raft.protocol.AppendResponse;
import io.zeebe.raft.protocol.JoinRequest;
import io.zeebe.transport.*;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorControl;

public class LeaderState extends AbstractRaftState
{
    private final ActorControl actor;

    private ActorCondition appendCondition;
    private LogStream logStream;

    public LeaderState(final Raft raft, final BufferedLogStorageAppender appender, ActorControl actorControl)
    {
        super(raft, appender);
        this.actor = actorControl;
        logStream = raft.getLogStream();
    }

    @Override
    public void reset()
    {
        super.reset();
        if (raft.getMembers().isEmpty())
        {
            appendCondition = actor.onCondition("append-condition", this::commitPositionOnSingleNode);
            logStream.registerOnAppendCondition(appendCondition);
        }
    }

    @Override
    public RaftState getState()
    {
        return RaftState.LEADER;
    }

    @Override
    public void joinRequest(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId, final JoinRequest joinRequest)
    {
        if (!raft.mayStepDown(joinRequest))
        {
            if (raft.isInitialEventCommitted() && raft.isConfigurationEventCommitted())
            {
                final SocketAddress socketAddress = joinRequest.getSocketAddress();
                if (raft.isMember(socketAddress))
                {
                    acceptJoinRequest(serverOutput, remoteAddress, requestId);
                }
                else
                {
                    // create new socket address object as it is stored in a map
                    raft.joinMember(serverOutput, remoteAddress, requestId, new SocketAddress(socketAddress));
                    // remove condition
                    logStream.removeOnAppendCondition(appendCondition);
                }
            }
            else
            {
                rejectJoinRequest(serverOutput, remoteAddress, requestId);
            }

        }
    }

    @Override
    public void appendResponse(final AppendResponse appendResponse)
    {
        if (!raft.mayStepDown(appendResponse))
        {
            final boolean succeeded = appendResponse.isSucceeded();
            final long eventPosition = appendResponse.getPreviousEventPosition();

            final RaftMember member = raft.getMember(appendResponse.getSocketAddress());

            if (member != null)
            {
                if (succeeded)
                {
                    member.onFollowerHasAcknowledgedPosition(eventPosition);
                    commit();
                }
                else
                {
                    member.onFollowerHasFailedPosition(eventPosition);
                }
            }
        }
    }

    private void commit()
    {
        final int memberSize = raft.getMemberSize();

        final long[] positions = new long[memberSize + 1];
        for (int i = 0; i < memberSize; i++)
        {
            positions[i] = raft.getMember(i).getMatchPosition();
        }

        // TODO(menski): `raft.getLogStream().getCurrentAppenderPosition()` is wrong as the current appender
        // position is the next position which is written. This means in a single node cluster the log
        // already committed an event which will be written in the future. `- 1` is a hotfix for this.
        // see https://github.com/zeebe-io/zeebe/issues/501
        positions[memberSize] = raft.getLogStream().getCurrentAppenderPosition() - 1;

        Arrays.sort(positions);

        final long commitPosition = positions[memberSize + 1 - raft.requiredQuorum()];
        final long initialEventPosition = raft.getInitialEventPosition();

        final LogStream logStream = raft.getLogStream();

        if (initialEventPosition >= 0 && commitPosition >= initialEventPosition && logStream.getCommitPosition() < commitPosition)
        {
            logStream.setCommitPosition(commitPosition);
        }
    }

    private void commitPositionOnSingleNode()
    {
        final long commitPosition = raft.getLogStream().getCurrentAppenderPosition() - 1;
        final long initialEventPosition = raft.getInitialEventPosition();

        if (initialEventPosition >= 0 && commitPosition >= initialEventPosition && logStream.getCommitPosition() < commitPosition)
        {
            logStream.setCommitPosition(commitPosition);
        }
    }
}
