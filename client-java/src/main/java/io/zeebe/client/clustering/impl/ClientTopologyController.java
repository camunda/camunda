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
package io.zeebe.client.clustering.impl;

import static io.zeebe.util.EnsureUtil.ensureNotNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import io.zeebe.transport.*;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.state.*;
import org.agrona.DirectBuffer;

public class ClientTopologyController
{
    public static final long REFRESH_INTERVAL = Duration.ofSeconds(10).toMillis();

    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_FAILED = 1;

    protected final StateMachine<Context> stateMachine;
    protected final RequestTopologyState requestTopologyState = new RequestTopologyState();
    protected final AwaitTopologyState awaitTopologyState = new AwaitTopologyState();
    protected final ClosedState closedState = new ClosedState();
    protected final IdleState idleState = new IdleState();

    private final ClientOutput output;

    public ClientTopologyController(final ClientTransport clientTransport)
    {
        output = clientTransport.getOutput();

        stateMachine = StateMachine.builder(Context::new)
            .initialState(requestTopologyState)
            .from(requestTopologyState).take(TRANSITION_DEFAULT).to(awaitTopologyState)
            .from(awaitTopologyState).take(TRANSITION_DEFAULT).to(closedState)
            .from(awaitTopologyState).take(TRANSITION_FAILED).to(closedState)
            .from(closedState).take(TRANSITION_DEFAULT).to(idleState)
            .from(idleState).take(TRANSITION_DEFAULT).to(requestTopologyState)
            .build();
    }

    public ClientTopologyController configure(final RemoteAddress socketAddress, final BufferWriter bufferWriter, final BufferReader bufferReader, final CompletableFuture<Void> resultFuture)
    {
        stateMachine.reset();

        ensureNotNull("socketAddress", socketAddress);
        ensureNotNull("bufferWriter", bufferWriter);
        ensureNotNull("bufferReader", bufferReader);
        ensureNotNull("resultFuture", resultFuture);

        final Context context = stateMachine.getContext();
        context.resultFuture = resultFuture;
        context.remoteAddress = socketAddress;
        context.bufferWriter = bufferWriter;
        context.bufferReader = bufferReader;

        return this;
    }

    public int doWork()
    {
        return stateMachine.doWork();
    }

    public boolean isIdle()
    {
        return stateMachine.getCurrentState() == idleState;
    }

    private class RequestTopologyState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final ClientRequest request = output.sendRequest(context.remoteAddress, context.bufferWriter);
            if (request != null)
            {
                context.request = request;
                context.take(TRANSITION_DEFAULT);
            }

            return 1;
        }

    }

    private class AwaitTopologyState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            int workCount = 0;

            final ClientRequest request = context.request;

            if (request.isDone())
            {
                try
                {
                    final DirectBuffer response = request.get();
                    context.bufferReader.wrap(response, 0, response.capacity());

                    if (context.resultFuture != null)
                    {
                        context.resultFuture.complete(null);
                    }
                    context.take(TRANSITION_DEFAULT);
                }
                catch (Exception e)
                {
                    if (context.resultFuture != null)
                    {
                        context.resultFuture.completeExceptionally(e);
                    }
                    context.take(TRANSITION_FAILED);
                }
                finally
                {
                    request.close();
                }

                workCount = 1;
            }

            return workCount;
        }
    }

    private class ClosedState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            context.take(TRANSITION_DEFAULT);
            context.reset();

            return 1;
        }
    }

    private class IdleState implements WaitState<Context>
    {

        @Override
        public void work(final Context context) throws Exception
        {
            if (System.currentTimeMillis() >= context.nextRefresh)
            {
                context.take(TRANSITION_DEFAULT);
            }
        }

    }


    static class Context extends SimpleStateMachineContext
    {

        // can be null if automatic refresh was trigger
        CompletableFuture<Void> resultFuture;

        ClientRequest request;

        // keep during reset to allow automatic refresh with last configuration
        RemoteAddress remoteAddress;
        BufferWriter bufferWriter;
        BufferReader bufferReader;

        // set during reset of context, e.g. on configure(...) and CloseState
        long nextRefresh;

        Context(final StateMachine<?> stateMachine)
        {
            super(stateMachine);
        }

        @Override
        public void reset()
        {
            if (resultFuture != null && !resultFuture.isDone())
            {
                resultFuture.cancel(true);
            }
            resultFuture = null;

            nextRefresh = System.currentTimeMillis() + REFRESH_INTERVAL;
        }

    }

}
