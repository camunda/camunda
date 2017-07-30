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

import java.util.concurrent.CompletableFuture;

import io.zeebe.raft.Raft;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ServerInputSubscription;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;

public class SubscriptionController
{

    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_FAILED = 1;

    private StateMachine<Context> stateMachine;

    public SubscriptionController(final Raft raft, final BufferingServerTransport serverTransport)
    {
        final OpenSubscriptionState openSubscription = new OpenSubscriptionState();
        final AwaitOpenSubscriptionState awaitOpenSubscription = new AwaitOpenSubscriptionState();
        final PollState poll = new PollState();

        stateMachine = StateMachine.<Context>builder(s -> new Context(s, raft, serverTransport))
            .initialState(openSubscription)
            .from(openSubscription).take(TRANSITION_DEFAULT).to(awaitOpenSubscription)

            .from(awaitOpenSubscription).take(TRANSITION_DEFAULT).to(poll)
            .from(awaitOpenSubscription).take(TRANSITION_FAILED).to(openSubscription)

            .build();
    }

    public int doWork()
    {
        return stateMachine.doWork();
    }

    public void reset()
    {
        stateMachine.reset();
    }

    static class OpenSubscriptionState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final Raft raft = context.getRaft();

            final CompletableFuture<ServerInputSubscription> future = context.getServerTransport().openSubscription(raft.getSubscriptionName(), raft, raft);

            context.setFuture(future);
            context.take(TRANSITION_DEFAULT);

            return 1;
        }

    }

    static class AwaitOpenSubscriptionState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            int workCount = 0;

            final CompletableFuture<ServerInputSubscription> future = context.getFuture();
            if (future.isDone())
            {
                workCount++;

                try
                {
                    context.setSubscription(future.get());
                    context.take(TRANSITION_DEFAULT);
                }
                catch (final Throwable t)
                {
                    context.getRaft().getLogger().debug("Failed to open server subscription", t);
                    context.take(TRANSITION_FAILED);
                }
                finally
                {
                    context.setFuture(null);
                }
            }

            return workCount;
        }

    }

    static class PollState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            return context.getSubscription().poll();
        }

    }

    static class Context extends SimpleStateMachineContext
    {

        private final Raft raft;
        private final BufferingServerTransport serverTransport;

        private CompletableFuture<ServerInputSubscription> future;
        private ServerInputSubscription subscription;

        Context(final StateMachine<?> stateMachine, final Raft raft, final BufferingServerTransport serverTransport)
        {
            super(stateMachine);
            this.raft = raft;
            this.serverTransport = serverTransport;

            reset();
        }

        @Override
        public void reset()
        {
            future = null;
            subscription = null;
        }

        public Raft getRaft()
        {
            return raft;
        }

        public BufferingServerTransport getServerTransport()
        {
            return serverTransport;
        }

        public CompletableFuture<ServerInputSubscription> getFuture()
        {
            return future;
        }

        public void setFuture(final CompletableFuture<ServerInputSubscription> future)
        {
            this.future = future;
        }

        public ServerInputSubscription getSubscription()
        {
            return subscription;
        }

        public void setSubscription(final ServerInputSubscription subscription)
        {
            this.subscription = subscription;
        }
    }

}
