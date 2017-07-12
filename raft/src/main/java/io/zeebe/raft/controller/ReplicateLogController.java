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
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftMember;
import io.zeebe.raft.protocol.AppendRequest;
import io.zeebe.util.state.*;

public class ReplicateLogController
{

    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_CLOSE = 2;

    private static final StateMachineCommand<Context> OPEN_COMMAND = context -> context.take(TRANSITION_OPEN);
    private static final StateMachineCommand<Context> CLOSE_COMMAND = context -> context.take(TRANSITION_CLOSE);

    private final StateMachineAgent<Context> stateMachineAgent;

    public ReplicateLogController(final Raft raft)
    {
        final State<Context> opening = new OpeningState();
        final State<Context> open = new OpenState();
        final WaitState<Context> closed = context -> { };

        final StateMachine<Context> stateMachine = StateMachine.<Context>builder(s -> new Context(s, raft))
            .initialState(closed)
            .from(closed).take(TRANSITION_OPEN).to(opening)
            .from(closed).take(TRANSITION_CLOSE).to(closed)
            .from(opening).take(TRANSITION_DEFAULT).to(open)
            .from(open).take(TRANSITION_OPEN).to(open)
            .from(open).take(TRANSITION_CLOSE).to(closed)
            .build();

        stateMachineAgent = new LimitedStateMachineAgent<>(stateMachine);
    }

    public int doWork()
    {
        return stateMachineAgent.doWork();
    }

    public void open()
    {
        stateMachineAgent.addCommand(OPEN_COMMAND);
    }

    public void close()
    {
        stateMachineAgent.addCommand(CLOSE_COMMAND);
    }

    static class OpeningState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final Raft raft = context.getRaft();

            final long nextHeartbeat = raft.nextHeartbeat();

            final int memberSize = raft.getMemberSize();

            for (int i = 0; i < memberSize; i++)
            {
                raft.getMember(i).reset(nextHeartbeat);
            }

            context.take(TRANSITION_DEFAULT);

            return memberSize;
        }

        @Override
        public boolean isInterruptable()
        {
            return false;
        }

    }

    static class OpenState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            int workCount = 0;

            final Raft raft = context.getRaft();

            final long now = System.currentTimeMillis();
            final long nextHeartbeat = raft.nextHeartbeat();

            final AppendRequest appendRequest = context.getAppendRequest().reset().setRaft(raft);

            final int memberSize = raft.getMemberSize();

            for (int i = 0; i < memberSize; i++)
            {
                final RaftMember member = raft.getMember(i);
                final boolean heartbeatRequired = member.getHeartbeat() <= now;

                LoggedEventImpl event = null;

                if (!member.hasFailures())
                {
                    event = member.getNextEvent();
                }

                if (event != null || heartbeatRequired)
                {
                    workCount++;

                    member.setHeartbeat(nextHeartbeat);

                    appendRequest
                        .setPreviousEventPosition(member.getPreviousPosition())
                        .setPreviousEventTerm(member.getPreviousTerm())
                        .setEvent(event);

                    final boolean sent = raft.sendMessage(member.getRemoteAddress(), appendRequest);

                    if (event != null && sent)
                    {
                        final BrokerEventMetadata metadata = new BrokerEventMetadata();
                        event.readMetadata(metadata);
                        raft.getLogger().debug("Send event {}/{} with prev {}/{} to {}", event.getPosition(), metadata.getRaftTermId(), member.getPreviousPosition(), member.getPreviousTerm(), member.getRemoteAddress().getAddress());

                        member.setPreviousEvent(event);
                    }
                }

            }

            return workCount;
        }

    }

    static class Context extends SimpleStateMachineContext
    {

        private final Raft raft;

        private final AppendRequest appendRequest = new AppendRequest();

        Context(final StateMachine<Context> stateMachine, final Raft raft)
        {
            super(stateMachine);
            this.raft = raft;
        }

        public Raft getRaft()
        {
            return raft;
        }

        public AppendRequest getAppendRequest()
        {
            return appendRequest;
        }

    }
}
