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
package io.zeebe.transport;

import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.StateMachineCommand;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;

public class SingleMessageController
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_FAILED = 2;
    private static final int TRANSITION_CLOSE = 3;

    private static final StateMachineCommand<SingleMessageContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private final WaitState<SingleMessageContext> closedState = (c) ->
    {
    };
    private final WaitState<SingleMessageContext> sentState = (c) ->
    {
    };
    private final WaitState<SingleMessageContext> failedState = (c) ->
    {
    };

    private final SendMessageState sendMessageState = new SendMessageState();
    private final ClosingState closingState = new ClosingState();

    private SingleMessageContext singleMessageContext;
    private final StateMachineAgent<SingleMessageContext> singleMessageStateMachine;

    public SingleMessageController(final ClientTransport clientTransport)
    {
        this.singleMessageStateMachine = new StateMachineAgent<>(
                StateMachine.<SingleMessageContext> builder(s ->
                {
                    singleMessageContext = new SingleMessageContext(s, clientTransport);
                    return singleMessageContext;
                })
                    .initialState(closedState)

                    .from(closedState).take(TRANSITION_OPEN).to(sendMessageState)
                    .from(closedState).take(TRANSITION_CLOSE).to(closedState)

                    .from(sendMessageState).take(TRANSITION_DEFAULT).to(sentState)
                    .from(sendMessageState).take(TRANSITION_FAILED).to(failedState)

                    .from(sendMessageState).take(TRANSITION_CLOSE).to(closingState)
                    .from(sentState).take(TRANSITION_CLOSE).to(closingState)
                    .from(failedState).take(TRANSITION_CLOSE).to(closingState)

                    .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
                    .from(closingState).take(TRANSITION_CLOSE).to(closingState)

                    .build());
    }

    public void open(final SocketAddress receiver, final BufferWriter requestWriter)
    {
        if (isClosed())
        {
            final RemoteAddress remoteAddress = singleMessageContext.clientTransport.registerRemoteAddress(receiver);

            singleMessageContext.message.reset()
                .remoteAddress(remoteAddress)
                .writer(requestWriter);
            singleMessageContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    public void close()
    {
        singleMessageStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
    }

    public int doWork()
    {
        return singleMessageStateMachine.doWork();
    }

    public boolean isClosed()
    {
        return singleMessageStateMachine.getCurrentState() == closedState;
    }

    public boolean isFailed()
    {
        return singleMessageStateMachine.getCurrentState() == failedState;
    }

    public boolean isSent()
    {
        return singleMessageStateMachine.getCurrentState() == sentState;
    }

    static class SingleMessageContext extends SimpleStateMachineContext
    {
        final TransportMessage message = new TransportMessage();
        final ClientTransport clientTransport;

        SingleMessageContext(final StateMachine<?> stateMachine, ClientTransport clientTransport)
        {
            super(stateMachine);
            this.clientTransport = clientTransport;
        }
    }

    static class SendMessageState implements State<SingleMessageContext>
    {
        @Override
        public int doWork(SingleMessageContext context) throws Exception
        {
            final TransportMessage message = context.message;
            final ClientTransport transport = context.clientTransport;

            int workcount = 0;

            final boolean success = transport.getOutput().sendMessage(message);

            if (success)
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            // TODO: timeout

            return workcount;

        }

        @Override
        public void onFailure(SingleMessageContext context, Exception e)
        {
            e.printStackTrace();
            context.take(TRANSITION_FAILED);
        }
    }

    static class ClosingState implements TransitionState<SingleMessageContext>
    {
        @Override
        public void work(SingleMessageContext context) throws Exception
        {
            context.message.reset();
            context.take(TRANSITION_DEFAULT);
        }
    }
}
