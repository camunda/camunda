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

import org.agrona.DirectBuffer;

import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.StateMachineCommand;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;

public class RequestResponseController
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_FAILED = 2;
    private static final int TRANSITION_CLOSE = 3;

    private static final StateMachineCommand<RequestResponseContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private final WaitState<RequestResponseContext> closedState = (c) ->
    {
    };
    private final WaitState<RequestResponseContext> responseAvailableState = (c) ->
    {
    };
    private final WaitState<RequestResponseContext> failedState = (c) ->
    {
    };

    private final ClosingState closingState = new ClosingState();
    private final SendRequestState sendRequestState = new SendRequestState();
    private final PollResponseState pollResponseState = new PollResponseState();

    private RequestResponseContext requestResponseContext;
    private final StateMachineAgent<RequestResponseContext> requestStateMachine;

    public RequestResponseController(ClientTransport transport)
    {
        this(transport, -1);
    }

    public RequestResponseController(
            final ClientTransport transport,
            final int timeout)
    {
        this.requestStateMachine = new StateMachineAgent<>(
                StateMachine.<RequestResponseContext> builder(s ->
                {
                    requestResponseContext = new RequestResponseContext(s, timeout, transport);
                    return requestResponseContext;
                })
                    .initialState(closedState)

                    .from(closedState).take(TRANSITION_OPEN).to(sendRequestState)
                    .from(closedState).take(TRANSITION_CLOSE).to(closedState)

                    .from(sendRequestState).take(TRANSITION_DEFAULT).to(pollResponseState)
                    .from(sendRequestState).take(TRANSITION_FAILED).to(failedState)
                    .from(sendRequestState).take(TRANSITION_CLOSE).to(closingState)

                    .from(pollResponseState).take(TRANSITION_DEFAULT).to(responseAvailableState)
                    .from(pollResponseState).take(TRANSITION_FAILED).to(failedState)
                    .from(pollResponseState).take(TRANSITION_CLOSE).to(closingState)

                    .from(responseAvailableState).take(TRANSITION_CLOSE).to(closingState)
                    .from(failedState).take(TRANSITION_CLOSE).to(closingState)

                    .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
                    .from(closingState).take(TRANSITION_CLOSE).to(closingState)

                    .build());
    }

    /**
     * @param receiver
     * @param requestWriter
     * @param responseReader is optional
     */
    public void open(final SocketAddress receiver, final BufferWriter requestWriter, BufferReader responseReader)
    {
        if (isClosed())
        {
            requestResponseContext.receiver.wrap(receiver);
            requestResponseContext.requestWriter = requestWriter;
            requestResponseContext.responseReader = responseReader;
            requestResponseContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    public void close()
    {
        requestStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
    }

    public int doWork()
    {
        return requestStateMachine.doWork();
    }

    public boolean isFailed()
    {
        return requestStateMachine.getCurrentState() == failedState;
    }

    public Exception getFailure()
    {
        return requestResponseContext.failure;
    }

    public boolean isResponseAvailable()
    {
        return requestStateMachine.getCurrentState() == responseAvailableState;
    }

    public boolean isClosed()
    {
        return requestStateMachine.getCurrentState() == closedState;
    }

    public DirectBuffer getResponseBuffer()
    {
        if (requestResponseContext.response == null)
        {
            throw new RuntimeException("no response available");
        }

        return requestResponseContext.response;
    }

    public int getResponseLength()
    {
        if (requestResponseContext.response == null)
        {
            throw new RuntimeException("no response available");
        }

        return requestResponseContext.response.capacity();
    }

    public RemoteAddress getReceiverRemote()
    {
        return requestResponseContext.receiverRemote;
    }


    static class RequestResponseContext extends SimpleStateMachineContext
    {
        final SocketAddress receiver;
        final int timeout;
        final ClientTransport transport;

        BufferWriter requestWriter;
        BufferReader responseReader;
        DirectBuffer response;
        Exception failure;

        ClientRequest request;
        RemoteAddress receiverRemote;

        RequestResponseContext(StateMachine<?> stateMachine, final int timeout, ClientTransport transport)
        {
            super(stateMachine);
            this.receiver = new SocketAddress();
            this.timeout = timeout;
            this.transport = transport;
        }

        public ClientRequest getRequest()
        {
            return request;
        }

        public void setRequest(ClientRequest request)
        {
            this.request = request;
        }
    }

    static class SendRequestState implements TransitionState<RequestResponseContext>
    {
        @Override
        public void work(RequestResponseContext context) throws Exception
        {
            final ClientTransport transport = context.transport;

            context.receiverRemote = transport.registerRemoteAddress(context.receiver);

            // TODO: should submit timeout here
            final ClientRequest request = transport.getOutput().sendRequest(context.receiverRemote, context.requestWriter);

            if (request != null)
            {
                context.request = request;
                context.take(TRANSITION_DEFAULT);
            }
        }

        @Override
        public void onFailure(RequestResponseContext context, Exception e)
        {
            context.failure = e;
            context.take(TRANSITION_FAILED);
        }
    }

    static class PollResponseState implements State<RequestResponseContext>
    {
        @Override
        public int doWork(RequestResponseContext context) throws Exception
        {
            int workcount = 0;
            final ClientRequest request = context.request;

            if (request.isDone())
            {
                workcount += 1;
                context.response = request.get();

                if (context.responseReader != null)
                {
                    context.responseReader.wrap(context.response, 0, context.response.capacity());
                }

                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }

        @Override
        public void onFailure(RequestResponseContext context, Exception e)
        {
            context.failure = e;
            context.take(TRANSITION_FAILED);
        }
    }

    static class ClosingState implements TransitionState<RequestResponseContext>
    {
        @Override
        public void work(RequestResponseContext context) throws Exception
        {
            final ClientRequest request = context.request;
            if (request != null)
            {
                request.close();
            }

            context.response = null;
            context.request = null;
            context.requestWriter = null;
            context.responseReader = null;
            context.receiver.reset();

            context.take(TRANSITION_DEFAULT);
        }
    }

}
