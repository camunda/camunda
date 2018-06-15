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

import io.zeebe.raft.Raft;
import io.zeebe.raft.state.Heartbeat;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ScheduledTimer;

public class RaftPollService implements Service<Void>
{
    private final Raft raft;
    private final ActorControl raftActor;
    private final Heartbeat heartbeat;

    private final ConsensusRequestController pollController;

    private ScheduledTimer scheduledElection;

    public RaftPollService(final Raft raft, final ActorControl raftActor)
    {
        this.raft = raft;
        this.raftActor = raftActor;
        this.heartbeat = raft.getHeartbeat();

        pollController = new ConsensusRequestController(raft, raftActor, new PollRequestHandler()
        {

            @Override
            public void consensusFailed(final Raft raft)
            {
                super.consensusFailed(raft);
                scheduleElectionTimer();
            }
        });
    }

    @Override
    public Void get()
    {
        return null;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        raftActor.call(this::initPoll);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.run(this::stopPoll);
    }

    private void stopPoll()
    {
        if (scheduledElection != null)
        {
            scheduledElection.cancel();
        }

        pollController.close();
    }

    private void initPoll()
    {
        if (raft.getMemberSize() == 0)
        {
            onElectionTimeout();
        }
        else
        {
            scheduleElectionTimer();
        }
    }

    protected void scheduleElectionTimer()
    {
        scheduledElection = raftActor.runDelayed(heartbeat.nextElectionTimeout(), this::electionTimeoutCallback);
    }

    private void electionTimeoutCallback()
    {
        if (heartbeat.shouldElect())
        {
            onElectionTimeout();
        }
        else
        {
            scheduleElectionTimer();
        }
    }

    protected void onElectionTimeout()
    {
        pollController.sendRequest();
    }
}
