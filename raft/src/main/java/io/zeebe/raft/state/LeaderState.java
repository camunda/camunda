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

import io.zeebe.raft.BufferedLogStorageAppender;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftMember;
import io.zeebe.raft.protocol.AppendResponse;
import io.zeebe.raft.protocol.JoinRequest;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerOutput;

public class LeaderState extends AbstractRaftState
{

    public LeaderState(final Raft raft, final BufferedLogStorageAppender appender)
    {
        super(raft, appender);
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
                if (raft.isMember(joinRequest.getSocketAddress()))
                {
                    acceptJoinRequest(serverOutput, remoteAddress, requestId);
                }
                else
                {
                    raft.joinMember(serverOutput, remoteAddress, requestId, joinRequest.getSocketAddress());
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
                    member.setMatchPosition(eventPosition);
                    member.resetFailures();
                }
                else
                {
                    member.failure();
                    member.resetToPosition(eventPosition);
                }
            }
        }
    }
}
