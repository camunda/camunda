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

import io.zeebe.util.collection.Tuple;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            @Override
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
    public void shouldRunOnCompleteInCompletedActor()
    {
        // given
        final AtomicInteger callbackInvocations = new AtomicInteger(0);
        final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

        future.onComplete((v, throwable) ->
        {
            assertThat(ActorTaskRunner.current().getCurrentTask().actor.getName()).isEqualTo("completing");
            callbackInvocations.incrementAndGet();
        });

        final ZbActor completingActor = new ZbActor()
        {
            @Override
            public String getName()
            {
                return "completing";
            }

            @Override
            protected void onActorStarted()
            {
                future.complete(null);
            }
        };

        // when
        schedulerRule.submitActor(completingActor);
        schedulerRule.workUntilDone();

        // then
        assertThat(callbackInvocations).hasValue(1);
    }

    @Test
    public void shouldRunChainedOnComplete()
    {
        // given
        final AtomicInteger callbackInvocations = new AtomicInteger(0);
        final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

        future.onComplete((v, throwable) -> callbackInvocations.incrementAndGet())
              .onComplete((v, throwable) -> callbackInvocations.incrementAndGet())
              .onComplete((v, throwable) -> callbackInvocations.incrementAndGet());


        final ZbActor completingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                future.complete(null);
            }
        };

        // when
        schedulerRule.submitActor(completingActor);
        schedulerRule.workUntilDone();

        // then
        assertThat(callbackInvocations).hasValue(3);
    }

    @Test
    public void shouldGetCompleteValueOnComplete()
    {
        // given
        final AtomicInteger callbackInvocations = new AtomicInteger(0);
        final CompletableActorFuture<Integer> future = new CompletableActorFuture<>();
        future.onComplete((v, throwable) -> callbackInvocations.set(v));

        final ZbActor completingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                future.complete(10);
            }
        };

        // when
        schedulerRule.submitActor(completingActor);
        schedulerRule.workUntilDone();

        // then
        assertThat(callbackInvocations).hasValue(10);
    }

    @Test
    public void shouldGetCompleteValueOnChainedOnComplete()
    {
        // given
        final AtomicInteger callbackInvocations = new AtomicInteger(0);
        final CompletableActorFuture<Integer> future = new CompletableActorFuture<>();
        future.onComplete((v, throwable) -> callbackInvocations.set(v + 1))
              .onComplete((v, throwable) -> callbackInvocations.set(v + 2));

        final ZbActor completingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                future.complete(10);
            }
        };

        // when
        schedulerRule.submitActor(completingActor);
        schedulerRule.workUntilDone();

        // then
        assertThat(callbackInvocations).hasValue(12);
    }

    @Test
    public void shouldGetCompleteThrowableOnComplete()
    {
        // given
        final CompletableActorFuture<Integer> future = new CompletableActorFuture<>();
        final List<Throwable> throwables = new ArrayList<>();
        future.onComplete((v, throwable) -> throwables.add(throwable));

        final ZbActor completingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                future.completeExceptionally(new RuntimeException());
            }
        };

        // when
        schedulerRule.submitActor(completingActor);
        schedulerRule.workUntilDone();

        // then
        assertThat(throwables).hasSize(1);
        assertThat(throwables.get(0)).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void shouldInvokeCallbackOnFirstFutureCompletion()
    {
        // given
        final CompletableActorFuture<String> future1 = new CompletableActorFuture<>();
        final CompletableActorFuture<String> future2 = new CompletableActorFuture<>();

        final List<Tuple<String, Throwable>> invocations = new ArrayList<>();

        final ZbActor waitingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.runOnFirstCompletion(Arrays.asList(future1, future2), (r, t) -> invocations.add(new Tuple<>(r, t)));
            }
        };

        final ZbActor completingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                future1.complete("foo");
                future2.complete("bar");
            }
        };

        schedulerRule.submitActor(waitingActor);
        schedulerRule.workUntilDone();

        // when
        schedulerRule.submitActor(completingActor);
        schedulerRule.workUntilDone();

        // then
        assertThat(invocations).hasSize(1).contains(new Tuple<>("foo", null));
    }

    @Test
    public void shouldInvokeCallbackOnlyOnSuccessfullyFutureCompletion()
    {
        // given
        final CompletableActorFuture<String> future1 = new CompletableActorFuture<>();
        final CompletableActorFuture<String> future2 = new CompletableActorFuture<>();

        final List<Tuple<String, Throwable>> invocations = new ArrayList<>();

        final ZbActor waitingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.runOnFirstCompletion(Arrays.asList(future1, future2), (r, t) -> invocations.add(new Tuple<>(r, t)));
            }
        };

        final ZbActor completingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                future1.completeExceptionally(new RuntimeException("foo"));
                future2.complete("bar");
            }
        };

        schedulerRule.submitActor(waitingActor);
        schedulerRule.workUntilDone();

        // when
        schedulerRule.submitActor(completingActor);
        schedulerRule.workUntilDone();

        // then
        assertThat(invocations).hasSize(1).contains(new Tuple<>("bar", null));
    }

    @Test
    public void shouldInvokeCallbackOnLastExceptionallyFutureCompletion()
    {
        // given
        final CompletableActorFuture<String> future1 = new CompletableActorFuture<>();
        final CompletableActorFuture<String> future2 = new CompletableActorFuture<>();

        final List<Tuple<String, Throwable>> invocations = new ArrayList<>();

        final ZbActor waitingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.runOnFirstCompletion(Arrays.asList(future1, future2), (r, t) -> invocations.add(new Tuple<>(r, t)));
            }
        };

        final ZbActor completingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                future1.completeExceptionally(new RuntimeException("foo"));
                future2.completeExceptionally(new RuntimeException("bar"));
            }
        };

        schedulerRule.submitActor(waitingActor);
        schedulerRule.workUntilDone();

        // when
        schedulerRule.submitActor(completingActor);
        schedulerRule.workUntilDone();

        // then
        assertThat(invocations).hasSize(1);
        assertThat(invocations.get(0).getLeft()).isNull();
        assertThat(invocations.get(0).getRight().getMessage()).isEqualTo("bar");
    }

    @Test
    public void shouldInvokeCallbackOnAllFutureCompletedSuccessfully()
    {
        // given
        final CompletableActorFuture<String> future1 = new CompletableActorFuture<>();
        final CompletableActorFuture<String> future2 = new CompletableActorFuture<>();

        final List<Throwable> invocations = new ArrayList<>();
        final List<String> results = new ArrayList<>();

        final ZbActor waitingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.runOnCompletion(Arrays.asList(future1, future2), t ->
                {
                    invocations.add(t);

                    results.add(future1.join());
                    results.add(future2.join());
                });
            }
        };

        final ZbActor completingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                future1.complete("foo");
                future2.complete("bar");
            }
        };

        schedulerRule.submitActor(waitingActor);
        schedulerRule.workUntilDone();

        // when
        schedulerRule.submitActor(completingActor);
        schedulerRule.workUntilDone();

        // then
        assertThat(invocations).hasSize(1).containsNull();
        assertThat(results).contains("foo", "bar");
    }

    @Test
    public void shouldInvokeCallbackOnAllFutureCompletedExceptionally()
    {
        // given
        final CompletableActorFuture<String> future1 = new CompletableActorFuture<>();
        final CompletableActorFuture<String> future2 = new CompletableActorFuture<>();

        final List<Throwable> invocations = new ArrayList<>();

        final ZbActor waitingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.runOnCompletion(Arrays.asList(future1, future2), t -> invocations.add(t));
            }
        };

        final ZbActor completingActor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                future1.completeExceptionally(new RuntimeException("foo"));
                future2.completeExceptionally(new RuntimeException("bar"));
            }
        };

        schedulerRule.submitActor(waitingActor);
        schedulerRule.workUntilDone();

        // when
        schedulerRule.submitActor(completingActor);
        schedulerRule.workUntilDone();

        // then
        assertThat(invocations).hasSize(1);
        assertThat(invocations.get(0).getMessage()).isEqualTo("bar");
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
                actor.await(CompletableActorFuture.completed("foo"), (r, t) -> futureResult.set(r));
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
            @Override
            protected void onActorStarted()
            {
                future.complete(null);
            }
        });
        schedulerRule.workUntilDone();

        // then
        assertThat(lifecycle).containsExactly("futureDone", "call");


    }

    @Test
    public void shouldReturnCompletedFutureWithNullValue()
    {
        // given

        // when
        final CompletableActorFuture<Void> completed = CompletableActorFuture.completed(null);

        // then
        assertThat(completed).isDone();
        assertThat(completed.join()).isNull();
    }


    @Test
    public void shouldReturnCompletedFuture()
    {
        // given
        final Object result = new Object();

        // when
        final CompletableActorFuture<Object> completed = CompletableActorFuture.completed(result);

        // then
        assertThat(completed).isDone();
        assertThat(completed.join()).isEqualTo(result);
    }

    @Test
    public void shouldReturnCompletedExceptionallyFuture()
    {
        // given
        final RuntimeException result = new RuntimeException("Something bad happend!");

        // when
        final CompletableActorFuture<Object> completed = CompletableActorFuture.completedExceptionally(result);

        // then
        assertThat(completed).isDone();
        assertThat(completed.isCompletedExceptionally()).isTrue();

        assertThatThrownBy(() -> completed.join()).hasMessageContaining("Something bad happend!");
    }

    @Test
    public void shouldFailToCompletedExceptionallyFutureWithNull()
    {
        // when
        final RuntimeException result = null;

        // then
        assertThatThrownBy(() -> CompletableActorFuture.completedExceptionally(result))
            .hasMessageContaining("Throwable must not be null.");
    }
}
