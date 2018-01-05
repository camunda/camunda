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
package io.zeebe.gossip.failuredetection;

import java.util.concurrent.CompletableFuture;

import io.zeebe.gossip.Loggers;
import io.zeebe.transport.*;
import io.zeebe.util.state.*;
import org.slf4j.Logger;

public class SubscriptionController
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    private static final String SUBSCRIPTION_NAME = "gossip";

    private static final int TRANSITION_DEFAULT = 1;
    private static final int TRANSITION_FAILED = 2;

    private final BufferingServerTransport serverTransport;
    private final ServerRequestHandler requestHandler;

    private final StateMachine<Context> stateMachine;

    public SubscriptionController(BufferingServerTransport serverTransport, ServerRequestHandler requestHandler, int pollLimit)
    {
        this.serverTransport = serverTransport;
        this.requestHandler = requestHandler;

        final OpenSubscriptionState openSubscriptionState = new OpenSubscriptionState();
        final AwaitOpenSubscriptionState awaitOpenSubscriptionState = new AwaitOpenSubscriptionState();
        final PollSubscriptionState pollSubscriptionState = new PollSubscriptionState(pollLimit);

        this.stateMachine = StateMachine.<Context> builder(Context::new)
                .initialState(openSubscriptionState)
                .from(openSubscriptionState).take(TRANSITION_DEFAULT).to(awaitOpenSubscriptionState)
                .from(awaitOpenSubscriptionState).take(TRANSITION_FAILED).to(openSubscriptionState)
                .from(awaitOpenSubscriptionState).take(TRANSITION_DEFAULT).to(pollSubscriptionState)
                .build();
    }

    public int doWork()
    {
        return stateMachine.doWork();
    }

    private class Context extends SimpleStateMachineContext
    {
        private CompletableFuture<ServerInputSubscription> future;
        private ServerInputSubscription subscription;

        Context(StateMachine<Context> stateMachine)
        {
            super(stateMachine);
        }
    }

    private class OpenSubscriptionState implements TransitionState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            final CompletableFuture<ServerInputSubscription> future = serverTransport.openSubscription(SUBSCRIPTION_NAME, null, requestHandler);

            context.future = future;
            context.take(TRANSITION_DEFAULT);
        }
    }

    private class AwaitOpenSubscriptionState implements WaitState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            final CompletableFuture<ServerInputSubscription> future = context.future;

            if (future.isDone())
            {
                final ServerInputSubscription subscription = future.get();

                context.subscription = subscription;
                context.future = null;

                context.take(TRANSITION_DEFAULT);
            }
        }

        @Override
        public void onFailure(Context context, Exception e)
        {
            LOG.warn("Failed to open subscription", e);

            context.take(TRANSITION_FAILED);
        }
    }

    private class PollSubscriptionState implements State<Context>
    {
        private final int subscriptionPollLimit;

        PollSubscriptionState(int subscriptionPollLimit)
        {
            this.subscriptionPollLimit = subscriptionPollLimit;
        }

        @Override
        public int doWork(Context context) throws Exception
        {
            final ServerInputSubscription subscription = context.subscription;

            return subscription.poll(subscriptionPollLimit);
        }
    }

}
