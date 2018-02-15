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
package io.zeebe.util.sched;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.future.CompletedActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;

public class ActorFutureTest
{

    @Rule
    public ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

    @Test
    public void shouldInvokeCallbackOnFutureCompletion()
    {
        // given
        final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
        final AtomicInteger callbackInvocations = new AtomicInteger(0);

        final ZbActor waitingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.runOnCompletion(future, (r, t) -> callbackInvocations.incrementAndGet());
            }
        };

        final ZbActor completingActor = new ZbActor()
        {
            protected void onActorStarted()
            {
                future.complete(null);
            }
        };

        schedulerRule.submitActor(waitingActor);
        schedulerRule.workUntilDone();

        // when
        schedulerRule.submitActor(completingActor);
        schedulerRule.workUntilDone();

        // then
        assertThat(callbackInvocations).hasValue(1);
    }

    @Test
    public void shouldNotBlockExecutionWhenRegisteredOnFuture()
    {
        // given
        final BlockedCallActor actor = new BlockedCallActor();
        schedulerRule.submitActor(actor);
        schedulerRule.workUntilDone();

        // when
        final ActorFuture<Integer> future = actor.call(42);
        schedulerRule.workUntilDone();
        final Integer result = future.join();

        // then
        assertThat(result).isEqualTo(42);
    }

    @Test
    public void awaitCompletedFuture()
    {
        // given
        final AtomicReference<String> futureResult = new AtomicReference<>();

        schedulerRule.submitActor(new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.await(new CompletedActorFuture<>("foo"), (r, t) -> futureResult.set(r));
            }
        });

        // when
        schedulerRule.workUntilDone();

        // then
        assertThat(futureResult.get()).isEqualTo("foo");
    }

    class BlockedCallActor extends ZbActor
    {

        @Override
        protected void onActorStarted()
        {
            final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
            actor.runOnCompletion(future, (r, t) ->
            {
            });
        }

        public ActorFuture<Integer> call(int returnValue)
        {
            return actor.call(() -> returnValue);
        }
    }

    @Test
    @Ignore("https://github.com/zeebe-io/zeebe/issues/669")
    public void shouldNotExecuteOtherJobsBetweenAwaitAndContinuation()
    {
        final List<String> lifecycle = new ArrayList<>();
        final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

        final ZbActor actor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.await(future, (r, t) -> lifecycle.add("futureDone"));
            }
        };

        schedulerRule.submitActor(actor);

        schedulerRule.workUntilDone();

        // when
        actor.actor.call(() -> lifecycle.add("call"));
        schedulerRule.submitActor(new ZbActor()
        {
            protected void onActorStarted()
            {
                future.complete(null);
            }
        });
        schedulerRule.workUntilDone();

        // then
        assertThat(lifecycle).containsExactly("futureDone", "call");


    }
}
