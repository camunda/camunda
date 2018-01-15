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
package io.zeebe.transport.impl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.NotConnectedException;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.RequestTimeoutException;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.WaitState;
import io.zeebe.util.time.ClockUtil;

public class ManagedClientRequestImpl implements ClientRequest
{

    private static final long RESUBMIT_TIMEOUT = 100L;

    protected final long deadline;
    protected final long id;
    protected final RemoteAddress endpoint;
    protected final FutureImpl responseFuture = new FutureImpl();
    protected final ClientRequestImpl request;

    protected ExpandableArrayBuffer requestBuffer = new ExpandableArrayBuffer();
    protected DirectBufferWriter requestWriter = new DirectBufferWriter();

    protected final StateMachineAgent<StateMachineContext> stateMachine;
    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_RESOLVE = 1;
    protected static final int TRANSITION_RETRY = 2;
    protected static final int TRANSITION_CLOSE = 3;

    protected static final SubmitRequestState OPEN_REQUEST_STATE = new SubmitRequestState();
    protected static final AwaitResponseState AWAIT_RESPONSE_STATE = new AwaitResponseState();
    protected static final ResolvedState RESOLVED_STATE = new ResolvedState();
    protected static final ClosedState CLOSED_STATE = new ClosedState();

    public ManagedClientRequestImpl(
            ClientRequestImpl request,
            RemoteAddress endpoint,
            BufferWriter writer,
            long timeout)
    {
        this.deadline = ClockUtil.getCurrentTimeInMillis() + timeout;
        this.id = request.getRequestId();
        this.endpoint = endpoint;
        this.request = request;

        final int requestLength = writer.getLength();
        writer.write(requestBuffer, 0);
        requestWriter.wrap(requestBuffer, 0, requestLength);

        responseFuture.awaitResult();

        this.stateMachine = new StateMachineAgent<>(
                StateMachine.<StateMachineContext> builder(s ->
                {
                    final StateMachineContext ctx = new StateMachineContext(s);
                    ctx.orchestrator = this;
                    ctx.responseFuture = responseFuture;
                    ctx.request = request;
                    ctx.requestWriter = requestWriter;

                    return ctx;
                })
                    .initialState(OPEN_REQUEST_STATE)

                    .from(OPEN_REQUEST_STATE).take(TRANSITION_DEFAULT).to(AWAIT_RESPONSE_STATE)
                    .from(OPEN_REQUEST_STATE).take(TRANSITION_RESOLVE).to(RESOLVED_STATE)
                    .from(OPEN_REQUEST_STATE).take(TRANSITION_CLOSE).to(CLOSED_STATE)

                    .from(AWAIT_RESPONSE_STATE).take(TRANSITION_RESOLVE).to(RESOLVED_STATE)
                    .from(AWAIT_RESPONSE_STATE).take(TRANSITION_RETRY).to(OPEN_REQUEST_STATE)
                    .from(AWAIT_RESPONSE_STATE).take(TRANSITION_CLOSE).to(CLOSED_STATE)

                    .from(RESOLVED_STATE).take(TRANSITION_CLOSE).to(CLOSED_STATE)

                    .build());
    }

    @Override
    public boolean isDone()
    {
        return responseFuture.isDone();
    }

    public boolean isClosed()
    {
        return responseFuture.isClosed();
    }

    @Override
    public void close()
    {
        // need to handle #close asynchronously as the request may
        // still be in progress in the state machine
        stateMachine.addCommand(s -> s.tryTake(TRANSITION_CLOSE));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCancelled()
    {
        return false;
    }

    @Override
    public DirectBuffer get() throws InterruptedException, ExecutionException
    {
        return responseFuture.get();
    }

    @Override
    public DirectBuffer get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException
    {
        return responseFuture.get(timeout, unit);
    }

    @Override
    public long getRequestId()
    {
        return id;
    }

    @Override
    public DirectBuffer join()
    {
        return responseFuture.join();
    }

    @Override
    public boolean isFailed()
    {
        return responseFuture.isFailed();
    }

    public int doWork()
    {
        return stateMachine.doWork();
    }

    protected boolean isTimedOut()
    {
        return ClockUtil.getCurrentTimeInMillis() > deadline;
    }

    protected static class SubmitRequestState implements State<StateMachineContext>
    {
        @Override
        public int doWork(StateMachineContext context) throws Exception
        {
            if (context.orchestrator.isTimedOut())
            {
                context.timeOut();
                return 1;
            }
            else if (!context.canSubmit())
            {
                return 0;
            }

            final boolean success = context.request.submit(context.requestWriter);

            if (success)
            {
                context.take(TRANSITION_DEFAULT);
                return 1;
            }
            else
            {
                return 0;
            }
        }
    }

    protected static class AwaitResponseState implements State<StateMachineContext>
    {

        @Override
        public int doWork(StateMachineContext context) throws Exception
        {
            if (context.orchestrator.isTimedOut())
            {
                context.timeOut();
                return 1;
            }

            if (context.request.isDone())
            {
                DirectBuffer result = null;
                try
                {
                    result = context.request.get();
                }
                catch (Exception e)
                {
                    if (e instanceof ExecutionException && ((ExecutionException) e).getCause() instanceof NotConnectedException)
                    {
                        context.doNotResubmitBefore(RESUBMIT_TIMEOUT);
                        context.take(TRANSITION_RETRY);
                    }
                    else
                    {
                        context.fail("Request failed", e);
                    }
                }

                if (result != null)
                {
                    context.responseFuture.complete(result, 0, result.capacity());
                    context.resolve();
                }

                return 1;
            }

            return 0;
        }
    }

    /**
     * Entered when the request is resolved successfully, has finally failed, etc. (i.e. any
     * state from which no more progress is made.
     */
    protected static class ResolvedState implements WaitState<StateMachineContext>
    {
        @Override
        public void work(StateMachineContext context) throws Exception
        {
        }
    }

    protected static class ClosedState implements WaitState<StateMachineContext>
    {
        @Override
        public void onEnter(StateMachineContext context)
        {
            final boolean responseDone = context.responseFuture.isDone();
            final boolean nowClosed = context.responseFuture.close();

            if (nowClosed && !responseDone)
            {
                // We need to avoid calling #close more than once (=> goes back into the pool).
                // If the responseFuture is already done, then the request got already closed.
                context.request.close();
            }
        }

        @Override
        public void work(StateMachineContext context) throws Exception
        {
        }
    }

    protected static class StateMachineContext extends SimpleStateMachineContext
    {

        protected ManagedClientRequestImpl orchestrator;
        protected FutureImpl responseFuture;
        protected ClientRequestImpl request;
        protected BufferWriter requestWriter;

        protected long submitTimeout = -1;

        public StateMachineContext(StateMachine<?> stateMachine)
        {
            super(stateMachine);
        }

        protected void doNotResubmitBefore(long duration)
        {
            submitTimeout = ClockUtil.getCurrentTimeInMillis() + duration;
        }

        protected boolean canSubmit()
        {
            return submitTimeout < ClockUtil.getCurrentTimeInMillis();
        }

        protected void timeOut()
        {
            final String reason = "Request timed out";
            fail(reason, new RequestTimeoutException(reason));
        }

        protected void fail(String reason, Exception cause)
        {
            responseFuture.fail(reason, cause);
            resolve();
        }

        protected void resolve()
        {
            request.close();
            take(TRANSITION_RESOLVE);
        }
    }

}
