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

import io.zeebe.logstreams.log.LogStreamFailureListener;
import io.zeebe.raft.Raft;
import io.zeebe.raft.event.RaftEvent;
import io.zeebe.raft.protocol.JoinResponse;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.state.*;
import org.slf4j.Logger;

public class AppendRaftEventController
{

    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_FAILED = 1;
    private static final int TRANSITION_OPEN = 2;
    private static final int TRANSITION_CLOSE = 3;

    private static final StateMachineCommand<Context> CLOSE_COMMAND = context -> context.take(TRANSITION_CLOSE);

    private final WaitState<Context> committed = context -> { };

    private final StateMachine<Context> stateMachine;
    private final StateMachineAgent<Context> stateMachineAgent;

    public AppendRaftEventController(final Raft raft)
    {
        final State<Context> registerFailureListener = new RegisterFailureListenerState();
        final State<Context> appendRaftEvent = new AppendRaftEventState();
        final State<Context> awaitAppendRaftEvent = new AwaitAppendRaftEventState();
        final State<Context> awaitCommitRaftEvent = new AwaitCommitRaftEventState();

        stateMachine = StateMachine.<Context>builder(s -> new Context(s, raft))
            .initialState(committed)
            .from(committed).take(TRANSITION_OPEN).to(registerFailureListener)
            .from(committed).take(TRANSITION_CLOSE).to(committed)

            .from(registerFailureListener).take(TRANSITION_DEFAULT).to(appendRaftEvent)

            .from(appendRaftEvent).take(TRANSITION_DEFAULT).to(awaitAppendRaftEvent)
            .from(appendRaftEvent).take(TRANSITION_FAILED).to(appendRaftEvent)

            .from(awaitAppendRaftEvent).take(TRANSITION_DEFAULT).to(awaitCommitRaftEvent)
            .from(awaitAppendRaftEvent).take(TRANSITION_FAILED).to(appendRaftEvent)
            .from(awaitAppendRaftEvent).take(TRANSITION_OPEN).to(appendRaftEvent)
            .from(awaitAppendRaftEvent).take(TRANSITION_CLOSE).to(committed)

            .from(awaitCommitRaftEvent).take(TRANSITION_DEFAULT).to(committed)
            .from(awaitCommitRaftEvent).take(TRANSITION_FAILED).to(appendRaftEvent)
            .from(awaitCommitRaftEvent).take(TRANSITION_OPEN).to(appendRaftEvent)
            .from(awaitCommitRaftEvent).take(TRANSITION_CLOSE).to(committed)

            .build();

        stateMachineAgent = new LimitedStateMachineAgent<>(stateMachine);
    }

    public int doWork()
    {
        return stateMachineAgent.doWork();
    }

    public void reset()
    {
        stateMachineAgent.reset();
    }

    public void open(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId, final SocketAddress socketAddress)
    {
        // this has to happen immediately so multiple join request are not accepted
        final Context context = stateMachine.getContext();
        context.take(TRANSITION_OPEN);

        context.setServerOutput(serverOutput);
        context.setRemoteAddress(remoteAddress);
        context.setRequestId(requestId);
        context.setSocketAddress(socketAddress);
    }

    public void close()
    {
        stateMachineAgent.addCommand(CLOSE_COMMAND);
    }

    public boolean isCommitted()
    {
        return stateMachineAgent.getCurrentState() == committed;
    }

    static class RegisterFailureListenerState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            // make sure we were unregistered before
            context.unregisterFailureListener();

            context.registerFailureListener();

            context.take(TRANSITION_DEFAULT);

            return 1;
        }

        @Override
        public boolean isInterruptable()
        {
            return false;
        }

    }


    static class AppendRaftEventState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final long position = context.tryWriteRaftEvent();

            if (position >= 0)
            {
                context.setPosition(position);
                context.take(TRANSITION_DEFAULT);
            }
            else
            {
                context.getRaft().getLogger().debug("Failed to append raft event");
                context.take(TRANSITION_FAILED);
            }

            return 1;
        }

        @Override
        public boolean isInterruptable()
        {
            return false;
        }

    }

    static class AwaitAppendRaftEventState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            int workCount = 0;

            final Raft raft = context.getRaft();
            final Logger logger = raft.getLogger();

            if (context.isAppended())
            {
                logger.debug("Raft event for term {} was appended in position {}", raft.getLogStream().getTerm(), context.getPosition());

                workCount++;
                context.take(TRANSITION_DEFAULT);
            }
            else if (context.isAppendFailed())
            {
                logger.debug("Failed to append initial event in position {}", context.getPosition());

                workCount++;
                context.take(TRANSITION_FAILED);
                context.resetPosition();
            }

            return workCount;
        }

    }

    static class AwaitCommitRaftEventState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            int workCount = 0;

            if (context.isCommitted())
            {
                workCount++;

                final Raft raft = context.getRaft();
                raft.getLogger().debug("Raft event for term {} was committed on position {}", raft.getLogStream().getTerm(), context.getPosition());

                // send response
                context.acceptJoinRequest();

                context.unregisterFailureListener();
                context.take(TRANSITION_DEFAULT);
            }

            return workCount;
        }

    }


    static class Context extends SimpleStateMachineContext implements LogStreamFailureListener
    {

        private final Raft raft;

        private final RaftEvent raftEvent = new RaftEvent();
        private final JoinResponse joinResponse = new JoinResponse();

        private long position;
        private long failedPosition;

        // response state
        private ServerOutput serverOutput;
        private RemoteAddress remoteAddress;
        private long requestId;
        private SocketAddress socketAddress;

        Context(final StateMachine<Context> stateMachine, final Raft raft)
        {
            super(stateMachine);
            this.raft = raft;

            reset();
        }

        @Override
        public void reset()
        {
            raftEvent.reset();

            resetPosition();
            unregisterFailureListener();
        }

        public Raft getRaft()
        {
            return raft;
        }

        public long tryWriteRaftEvent()
        {
            return raftEvent.tryWrite(raft.getLogStream(), raft.getSocketAddress(), raft.getMembers());
        }

        public void setPosition(final long position)
        {
            this.position = position;
        }

        public boolean isAppended()
        {
            return position >= 0 && position < raft.getLogStream().getCurrentAppenderPosition();
        }

        public boolean isCommitted()
        {
            return position >= 0 && position <= raft.getLogStream().getCommitPosition();
        }

        public boolean isAppendFailed()
        {
            return position >= 0 && failedPosition >= 0 && position >= failedPosition;
        }

        public void registerFailureListener()
        {
            raft.getLogStream().registerFailureListener(this);
        }

        public void unregisterFailureListener()
        {
            raft.getLogStream().removeFailureListener(this);
        }

        public void resetPosition()
        {
            position = -1;
            failedPosition = -1;
        }

        @Override
        public void onFailed(final long failedPosition)
        {
            this.failedPosition = failedPosition;
        }

        @Override
        public void onRecovered()
        {
            // ignore
        }

        public void setServerOutput(final ServerOutput serverOutput)
        {
            this.serverOutput = serverOutput;
        }

        public void setRemoteAddress(final RemoteAddress remoteAddress)
        {
            this.remoteAddress = remoteAddress;
        }

        public void setRequestId(final long requestId)
        {
            this.requestId = requestId;
        }

        public void setSocketAddress(final SocketAddress socketAddress)
        {
            this.socketAddress = socketAddress;
        }

        public void acceptJoinRequest()
        {
            joinResponse
                .reset()
                .setSucceeded(true)
                .setRaft(raft);

            raft.sendResponse(serverOutput, remoteAddress, requestId, joinResponse);
        }
        public long getPosition()
        {
            return position;
        }

    }
}
