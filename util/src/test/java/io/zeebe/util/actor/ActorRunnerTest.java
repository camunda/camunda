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
package io.zeebe.util.actor;

import static io.zeebe.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.ErrorHandler;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.status.AtomicCounter;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ActorRunnerTest
{
    private static final int BASE_ITERATIONS_PER_ACTOR = 1;

    @Mock
    private IdleStrategy mockIdleStrategy;

    @Mock
    private ErrorHandler mockErrorHandler;

    @Mock
    private AtomicCounter mockCounter;

    private ExecutorService executorService;

    private ActorRunner actorRunner;

    @Before
    public void init()
    {
        MockitoAnnotations.initMocks(this);

        executorService = Executors.newSingleThreadExecutor();

        actorRunner = new ActorRunner(BASE_ITERATIONS_PER_ACTOR,
                mockIdleStrategy,
                mockErrorHandler,
                Duration.ofNanos(0),
                new HashMap<>());
    }

    @After
    public void cleanUp()
    {
        actorRunner.close();

        executorService.shutdown();
    }

    @Test
    public void shouldSubmitActor()
    {
        final RecordingActor actor = new RecordingActor(3, Actor.PRIORITY_LOW);

        actorRunner.submitActor(new ActorReferenceImpl(actor, 16));

        executorService.submit(actorRunner);

        waitUntil(() -> actor.invocations > 0);
    }

    @Test
    public void shouldReclaimActor()
    {
        final RecordingActor actor = new RecordingActor(3, Actor.PRIORITY_LOW);
        final ActorReferenceImpl actorRef = new ActorReferenceImpl(actor, 16);

        final AtomicBoolean isReclaimed = new AtomicBoolean(false);

        actorRunner.submitActor(actorRef);
        actorRunner.reclaimActor(actorRef, t -> isReclaimed.set(true));

        executorService.submit(actorRunner);

        waitUntil(() -> isReclaimed.get());
    }

    @Test
    public void shouldInvokeActorDependsOnPriority()
    {
        final RecordingActor actor1 = new RecordingActor(10, Actor.PRIORITY_LOW);
        final RecordingActor actor2 = new RecordingActor(10, Actor.PRIORITY_LOW);
        final RecordingActor actor3 = new RecordingActor(10, Actor.PRIORITY_HIGH);

        actorRunner.submitActor(new ActorReferenceImpl(actor1, 16));
        actorRunner.submitActor(new ActorReferenceImpl(actor2, 16));
        actorRunner.submitActor(new ActorReferenceImpl(actor3, 16));

        executorService.submit(actorRunner);

        waitUntil(() -> actor1.invocations > 10);

        actorRunner.close();

        waitUntil(() -> actor3.invocations > actor1.invocations);

        assertThat(actor1.invocations).isEqualTo(actor2.invocations);
        assertThat(actor3.invocations).isGreaterThan(actor1.invocations);
        assertThat(actor3.invocations).isGreaterThan(actor2.invocations);
    }

    @Test
    public void shouldRemovedActor()
    {
        final RecordingActor actor1 = new RecordingActor(10, Actor.PRIORITY_LOW);
        final RecordingActor actor2 = new RecordingActor(10, Actor.PRIORITY_LOW);

        final ActorReferenceImpl actorRef1 = new ActorReferenceImpl(actor1, 16);
        final ActorReferenceImpl actorRef2 = new ActorReferenceImpl(actor2, 16);

        actorRunner.submitActor(actorRef1);
        actorRunner.submitActor(actorRef2);

        actorRef1.close();

        executorService.submit(actorRunner);

        waitUntil(() -> actor2.invocations > 0);

        assertThat(actor1.invocations).isEqualTo(0);
        assertThat(actorRunner.getActors()).hasSize(1);
    }

    @Test
    public void shouldInvokeIdleStrategy()
    {
        final RecordingActor actor1 = new RecordingActor(10, Actor.PRIORITY_LOW);
        final RecordingActor actor2 = new RecordingActor(10, Actor.PRIORITY_LOW);

        actorRunner.submitActor(new ActorReferenceImpl(actor1, 16));
        actorRunner.submitActor(new ActorReferenceImpl(actor2, 16));

        executorService.submit(actorRunner);

        waitUntil(() -> actor1.invocations > 10);

        verify(mockIdleStrategy, atLeastOnce()).idle(0);
    }

    @Test
    public void shouldInvokeErrorHandler()
    {
        final RecordingActor actor = new RecordingActor(10, Actor.PRIORITY_LOW);
        final Actor failingActor = () ->
        {
            throw new RuntimeException();
        };

        actorRunner.submitActor(new ActorReferenceImpl(actor, 16));
        actorRunner.submitActor(new ActorReferenceImpl(failingActor, 16));

        executorService.submit(actorRunner);

        waitUntil(() -> actor.invocations > 10);

        verify(mockErrorHandler, atLeast(10)).onError(any(RuntimeException.class));
    }

    @Test
    public void shouldRecordActorDuration()
    {
        final RecordingActor actor = new RecordingActor(BASE_ITERATIONS_PER_ACTOR * 100, Actor.PRIORITY_LOW);
        final ActorReferenceImpl actorRef = new ActorReferenceImpl(actor, 16);

        actorRunner.submitActor(actorRef);

        executorService.submit(actorRunner);

        waitUntil(() -> actorRef.getDuration() > 0);

        assertThat(actor.invocations).isGreaterThan(0);
    }

    private class RecordingActor implements Actor
    {
        private int workToDo;
        private int priority;

        private int invocations = 0;

        RecordingActor(int workToDo, int priority)
        {
            this.workToDo = workToDo;
            this.priority = priority;
        }

        @Override
        public int doWork() throws Exception
        {
            invocations += 1;

            if (workToDo > 0)
            {
                workToDo -= 1;
                return 1;
            }
            else
            {
                return 0;
            }
        }

        @Override
        public int getPriority(long now)
        {
            return priority;
        }
    }

}
