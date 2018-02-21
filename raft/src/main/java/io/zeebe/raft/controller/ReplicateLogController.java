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
package io.zeebe.raft.controller;

import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.raft.Loggers;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftMember;
import io.zeebe.raft.protocol.AppendRequest;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ScheduledTimer;

public class ReplicateLogController
{
    private final Raft raft;

    private final AppendRequest appendRequest = new AppendRequest();
    private final ActorControl actor;
    private ScheduledTimer heartBeatTimer;

    public ReplicateLogController(final Raft raft, ActorControl actorControl)
    {
        this.raft = raft;
        this.actor = actorControl;
    }

    public void open()
    {
        final int memberSize = raft.getMemberSize();
        for (int i = 0; i < memberSize; i++)
        {
            raft.getMember(i).reset();
        }

        heartBeatTimer = actor.runAtFixedRate(raft.getConfiguration().getHeartbeatInterval(), this::sendAppendRequest);
    }

    // TODO perhaps possible to improve this
    // only send events if available - otherwise empty heartbeat
    public void sendAppendRequest()
    {
        final int memberSize = raft.getMemberSize();

        for (int i = 0; i < memberSize; i++)
        {
            final RaftMember member = raft.getMember(i);
            LoggedEventImpl event = null;
            if (!member.hasFailures())
            {
                event = member.getNextEvent();
            }


            appendRequest.reset().setRaft(raft);

            appendRequest
                .setPreviousEventPosition(member.getPreviousPosition())
                .setPreviousEventTerm(member.getPreviousTerm())
                .setEvent(event);

            final boolean sent = raft.sendMessage(member.getRemoteAddress(), appendRequest);

            Loggers.RAFT_LOGGER.debug("Send append request to {}, sent {}", member.getRemoteAddress().getAddress(), sent);
            if (event != null)
            {
                if (sent)
                {
                    member.setPreviousEvent(event);
                }
                else
                {
                    member.setBufferedEvent(event);
                }
            }
        }
    }

    public void close()
    {
        if (heartBeatTimer != null)
        {
            heartBeatTimer.cancel();
            heartBeatTimer = null;
        }
    }

}
