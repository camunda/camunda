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
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftMember;
import io.zeebe.raft.protocol.AppendRequest;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ScheduledTimer;

public class ReplicateLogController
{
    private final Raft raft;

    private final AppendRequest appendRequest = new AppendRequest();
    private final ActorControl actor;
    private final ActorCondition actorCondition;
    private ScheduledTimer heartBeatTimer;

    public ReplicateLogController(final Raft raft, ActorControl actorControl)
    {
        this.raft = raft;
        this.actor = actorControl;
        actorCondition = actor.onCondition("raft-event-append", this::sendAppendRequestRepeatly);
    }

    public void open()
    {
        final int memberSize = raft.getMemberSize();
        for (int i = 0; i < memberSize; i++)
        {
            raft.getMember(i).reset();
        }

        heartBeatTimer = actor.runDelayed(raft.getConfiguration().getHeartbeatInterval(), this::heartBeat);
        raft.getLogStream().registerOnAppendCondition(actorCondition);
    }

    /**
     * Sends append request. Is scheduled via runDelayed.
     * This method reschedule them self on method begin.
     */
    private void heartBeat()
    {
        heartBeatTimer = actor.runDelayed(raft.getConfiguration().getHeartbeatInterval(), this::heartBeat);
        sendAppendRequest();
    }

    /**
     * Method is called if event is appended to the log stream or a new member joins the cluster.
     *
     * Method reschedule itself, if there are more events to send and the last event was
     * successfully written to the send buffer.
     *
     * If not, we have backpressure and send next events on the next heartbeat.
     */
    private void sendAppendRequestRepeatly()
    {
        final boolean hasNext = sendAppendRequest();
        if (hasNext)
        {
            actor.submit(this::sendAppendRequestRepeatly);
        }
    }

    /**
     * <p>
     *     Sends append request's to all raft members.
     * </p>
     *
     * <p>
     * Returns true, if there are more events for a member to be sent AND
     * the last event was successfully writen to the send ring buffer.
     * </p>
     *
     * <p>
     * If the last event, was not successfully writen, we have backpressure and
     * we should wait. In that case or if the members has no more events false is returned.
     * </p>
     *
     * @return <p>true, if one member has next event AND <br/>
     *                  the last event was written to the send ring buffer successfully
     *        </p>
     *        <p>
     *         false, if the members had no more events OR <br/>
     *                  the last event was not written to the send ring buffer successfully
     *        </p>
     */
    private boolean sendAppendRequest()
    {
        final int memberSize = raft.getMemberSize();
        boolean hasNext = false;
        for (int i = 0; i < memberSize; i++)
        {
            final RaftMember member = raft.getMember(i);
            final boolean wasSent = trySendNextEventToMember(member);
            hasNext |= member.hasNextEvent() && wasSent;
        }
        return hasNext;
    }

    private boolean trySendNextEventToMember(RaftMember member)
    {
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

        final boolean wasSent = raft.sendMessage(member.getRemoteAddress(), appendRequest);
        if (event != null)
        {
            if (wasSent)
            {
                member.setPreviousEvent(event);
            }
            else
            {
                member.setBufferedEvent(event);
            }
        }
        return wasSent;
    }

    public void close()
    {
        if (heartBeatTimer != null)
        {
            heartBeatTimer.cancel();
            heartBeatTimer = null;
        }
        raft.getLogStream().removeOnCommitPositionUpdatedCondition(actorCondition);
    }

}
