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

import java.time.Duration;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.raft.Loggers;
import io.zeebe.raft.Raft;
import io.zeebe.raft.event.InitialEvent;
import io.zeebe.raft.state.LeaderState;
import io.zeebe.servicecontainer.*;
import io.zeebe.util.sched.*;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.slf4j.Logger;

public class LeaderCommitInitialEvent implements Service<Void>
{
    private static final Logger LOG = Loggers.RAFT_LOGGER;
    public static final Duration COMMIT_TIMEOUT = Duration.ofSeconds(15);

    private final LeaderState leaderState;
    private final ActorControl actor;
    private final Raft raft;
    private final InitialEvent initialEvent = new InitialEvent();
    private ActorCondition commitPositionCondition;
    private final LogStream logStream;
    private final CompletableActorFuture<Void> commitFuture = new CompletableActorFuture<>();

    private long position;
    private ScheduledTimer scheduledRetry;

    public LeaderCommitInitialEvent(final Raft raft, ActorControl actorControl, LeaderState leaderState)
    {
        this.raft = raft;
        this.actor = actorControl;
        this.leaderState = leaderState;

        this.commitPositionCondition = actor.onCondition("raft-event-commited", this::onCommitPositionUpdated);
        this.logStream = raft.getLogStream();
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        startContext.async(commitFuture);
        actor.call(this::appendInitialEvent);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async(actor.call(() ->
        {
            if (scheduledRetry != null)
            {
                scheduledRetry.cancel();
            }
            if (commitPositionCondition != null)
            {
                commitPositionCondition.cancel();
            }
        }));
    }

    private void appendInitialEvent()
    {
        actor.runUntilDone(() ->
        {
            final long position = initialEvent.tryWrite(raft);

            if (position >= 0)
            {
                actor.done();

                LOG.debug("Initial event for term {} was appended on position {}", raft.getTerm(), position);

                this.position = position;
                leaderState.setInitialEventPosition(position);

                logStream.registerOnCommitPositionUpdatedCondition(commitPositionCondition);

                scheduledRetry = actor.runDelayed(COMMIT_TIMEOUT, () ->
                {
                    logStream.removeOnCommitPositionUpdatedCondition(commitPositionCondition);
                    actor.submit(() -> appendInitialEvent());
                });
            }
            else
            {
                actor.yield();
            }
        });
    }

    private void onCommitPositionUpdated()
    {
        if (isCommitted())
        {
            LOG.debug("Initial event for term {} was committed on position {}", raft.getTerm(), position);

            if (scheduledRetry != null)
            {
                scheduledRetry.cancel();
            }

            commitFuture.complete(null);
            commitPositionCondition.cancel();
            commitPositionCondition = null;
            leaderState.setInitialEventCommitted();
        }
    }

    private boolean isCommitted()
    {
        return position >= 0 && position <= logStream.getCommitPosition();
    }

    @Override
    public Void get()
    {
        return null;
    }

}
