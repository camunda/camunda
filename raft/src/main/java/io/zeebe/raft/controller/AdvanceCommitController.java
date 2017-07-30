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

import java.util.Arrays;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.raft.Raft;
import io.zeebe.util.state.*;

public class AdvanceCommitController
{

    private static final int TRANSITION_OPEN = 0;
    private static final int TRANSITION_CLOSE = 1;

    private static final StateMachineCommand<Context> OPEN_COMMAND = context -> context.take(TRANSITION_OPEN);
    private static final StateMachineCommand<Context> CLOSE_COMMAND = context -> context.take(TRANSITION_CLOSE);

    private final StateMachineAgent<Context> stateMachineAgent;

    public AdvanceCommitController(final Raft raft)
    {
        final State<Context> open = new OpenState();
        final WaitState<Context> closed = context -> { };

        final StateMachine<Context> stateMachine = StateMachine.<Context>builder(s -> new Context(s, raft))
            .initialState(closed)
            .from(closed).take(TRANSITION_OPEN).to(open)
            .from(closed).take(TRANSITION_CLOSE).to(closed)
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

    static class OpenState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final Raft raft = context.getRaft();

            final int memberSize = raft.getMemberSize();

            final long[] positions = new long[memberSize + 1];

            for (int i = 0; i < memberSize; i++)
            {
                positions[i] = raft.getMember(i).getMatchPosition();
            }

            // TODO(menski): this is wrong as the current appender position is the next position which is written
            // this means in a single node cluster the log already committed an event which will be written in the future
            // see https://github.com/camunda-tngp/zb-logstreams/issues/68
            positions[memberSize] = raft.getLogStream().getCurrentAppenderPosition();

            Arrays.sort(positions);

            final long commitPosition = positions[memberSize + 1 - raft.requiredQuorum()];
            final long initialEventPosition = raft.getInitialEventPosition();

            final LogStream logStream = raft.getLogStream();

            if (initialEventPosition >= 0 && commitPosition >= initialEventPosition && logStream.getCommitPosition() < commitPosition)
            {
                raft.getLogger().debug("Committing position {} as {}", commitPosition, raft.getState());
                logStream.setCommitPosition(commitPosition);
            }

            return 0;
        }

    }

    static class Context extends SimpleStateMachineContext
    {

        private final Raft raft;

        Context(final StateMachine<Context> stateMachine, final Raft raft)
        {
            super(stateMachine);
            this.raft = raft;
        }

        public Raft getRaft()
        {
            return raft;
        }
    }

}
