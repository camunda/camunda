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
import io.zeebe.raft.protocol.JoinRequest;
import io.zeebe.raft.protocol.JoinResponse;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.state.*;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class JoinController
{

    public static final long DEFAULT_JOIN_TIMEOUT_MS = 500;

    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_FAILED = 1;
    private static final int TRANSITION_OPEN = 2;
    private static final int TRANSITION_CLOSE = 3;
    private static final int TRANSITION_SINGLE_NODE = 4;

    private static final StateMachineCommand<Context> OPEN_COMMAND = context -> context.take(TRANSITION_OPEN);
    private static final StateMachineCommand<Context> CLOSE_COMMAND = context -> context.take(TRANSITION_CLOSE);

    private final StateMachineAgent<Context> stateMachineAgent;

    private final WaitState<Context> joined = context -> { };

    public JoinController(final Raft raft)
    {
        final State<Context> sendJoinRequest = new SendJoinRequestState();
        final State<Context> awaitJoinResponse = new AwaitJoinResponseState();
        final State<Context> abortRequest = new AbortRequestState();
        final WaitState<Context> closed = context -> { };

        final StateMachine<Context> stateMachine = StateMachine.<Context>builder(s -> new Context(s, raft))
            .initialState(closed)
            .from(closed).take(TRANSITION_OPEN).to(sendJoinRequest)
            .from(closed).take(TRANSITION_CLOSE).to(closed)

            .from(sendJoinRequest).take(TRANSITION_DEFAULT).to(awaitJoinResponse)
            .from(sendJoinRequest).take(TRANSITION_FAILED).to(sendJoinRequest)
            .from(sendJoinRequest).take(TRANSITION_SINGLE_NODE).to(joined)

            .from(awaitJoinResponse).take(TRANSITION_DEFAULT).to(joined)
            .from(awaitJoinResponse).take(TRANSITION_FAILED).to(sendJoinRequest)
            .from(awaitJoinResponse).take(TRANSITION_OPEN).to(awaitJoinResponse)
            .from(awaitJoinResponse).take(TRANSITION_CLOSE).to(abortRequest)

            .from(abortRequest).take(TRANSITION_DEFAULT).to(closed)

            .from(joined).take(TRANSITION_OPEN).to(joined)
            .from(joined).take(TRANSITION_CLOSE).to(closed)

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

    public void open()
    {
        stateMachineAgent.addCommand(OPEN_COMMAND);
    }

    public void close()
    {
        stateMachineAgent.addCommand(CLOSE_COMMAND);
    }

    public boolean isJoined()
    {
        return stateMachineAgent.getCurrentState() == joined;
    }

    static class SendJoinRequestState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final RemoteAddress nextMember = context.getNextMember();

            if (nextMember != null)
            {
                final Raft raft = context.getRaft();

                final JoinRequest joinRequest = context.getJoinRequest().reset().setRaft(raft);

                try
                {
                    final ClientRequest clientRequest = raft.sendRequest(nextMember, joinRequest);
                    if (clientRequest != null)
                    {
                        context.setClientRequest(clientRequest);
                        context.take(TRANSITION_DEFAULT);
                    }
                    else
                    {
                        context.take(TRANSITION_FAILED);
                    }
                }
                catch (final Exception e)
                {
                    raft.getLogger().debug("Failed to send join request to {}", nextMember, e);
                    context.take(TRANSITION_FAILED);
                }
            }
            else
            {
                context.take(TRANSITION_SINGLE_NODE);
            }

            return 1;
        }

        @Override
        public boolean isInterruptable()
        {
            return false;
        }

    }

    static class AwaitJoinResponseState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            int workCount = 0;

            final Raft raft = context.getRaft();
            final Logger logger = raft.getLogger();
            final ClientRequest clientRequest = context.getClientRequest();

            if (clientRequest.isDone())
            {
                workCount++;

                try
                {
                    final JoinResponse joinResponse = context.getJoinResponse();

                    final DirectBuffer responseBuffer = clientRequest.get();
                    joinResponse.wrap(responseBuffer, 0, responseBuffer.capacity());

                    if (!raft.mayStepDown(joinResponse) && raft.isTermCurrent(joinResponse))
                    {
                        // update members to maybe discover leader
                        raft.addMembers(joinResponse.getMembers());

                        if (joinResponse.isSucceeded())
                        {
                            logger.debug("Join request was accepted in term {}", joinResponse.getTerm());
                            context.take(TRANSITION_DEFAULT);
                        }
                        else
                        {
                            context.take(TRANSITION_FAILED);
                        }
                    }
                    else
                    {
                        // received response from different term
                        context.take(TRANSITION_FAILED);
                    }

                }
                catch (final Exception e)
                {
                    logger.debug("Failed to read join response", e);
                    context.take(TRANSITION_FAILED);
                }
                finally
                {
                    context.reset();
                }
            }
            else if (context.isTimeout())
            {
                logger.debug("Timeout while waiting for join response");
                context.take(TRANSITION_FAILED);
                context.reset();
            }

            return workCount;
        }

    }

    static class AbortRequestState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            context.reset();

            return 1;
        }


        @Override
        public boolean isInterruptable()
        {
            return false;
        }
    }

    static class Context extends SimpleStateMachineContext
    {

        private final JoinRequest joinRequest = new JoinRequest();
        private final JoinResponse joinResponse = new JoinResponse();

        private final Raft raft;

        // will not be reset to continue to select new members on every retry
        private int currentMember;

        private ClientRequest clientRequest;
        private long timeout;

        Context(final StateMachine<Context> stateMachine, final Raft raft)
        {
            super(stateMachine);
            this.raft = raft;

            reset();
        }

        @Override
        public void reset()
        {
            joinRequest.reset();
            joinResponse.reset();

            if (clientRequest != null)
            {
                clientRequest.close();
            }
            clientRequest = null;
            timeout = -1;
        }

        public Raft getRaft()
        {
            return raft;
        }

        public JoinRequest getJoinRequest()
        {
            return joinRequest;
        }

        public JoinResponse getJoinResponse()
        {
            return joinResponse;
        }

        public RemoteAddress getNextMember()
        {
            final int memberSize = raft.getMemberSize();
            if (memberSize > 0)
            {
                final int nextMember = currentMember % memberSize;

                currentMember++;

                return raft.getMember(nextMember).getRemoteAddress();
            }
            else
            {
                return null;
            }
        }

        public void setClientRequest(final ClientRequest clientRequest)
        {
            this.timeout = System.currentTimeMillis() + DEFAULT_JOIN_TIMEOUT_MS;
            this.clientRequest = clientRequest;
        }

        public ClientRequest getClientRequest()
        {
            return clientRequest;
        }

        public boolean isTimeout()
        {
            return System.currentTimeMillis() > timeout;
        }

    }

}
