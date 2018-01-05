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

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.util.TestUtil.waitUntil;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

public class DynamicActorSchedulerTest
{
    private static final Actor DUMMY_ACTOR = () -> 1;

    @Mock
    private ActorRunner mockRunner1;

    @Mock
    private ActorRunner mockRunner2;

    private ActorSchedulerRunnable scheduler;
    private ExecutorService executorService;

    private ActorReferenceImpl[] actorRefs;
    private AtomicInteger submittedActors;

    @Before
    public void init()
    {
        MockitoAnnotations.initMocks(this);

        submittedActors = new AtomicInteger(0);

        final Answer<Void> submitActorCallback = i ->
        {
            submittedActors.incrementAndGet();
            return null;
        };

        doAnswer(submitActorCallback).when(mockRunner1).submitActor(any());
        doAnswer(submitActorCallback).when(mockRunner2).submitActor(any());

        final ActorRunner[] runners = new ActorRunner[] {mockRunner1, mockRunner2};

        scheduler = new ActorSchedulerRunnable(
            runners,
            t -> actorRefs[0],
            0.25,
            Duration.ofSeconds(10),
            Duration.ofSeconds(10),
            new HashMap<>());

        actorRefs = new ActorReferenceImpl[5];
        for (int i = 0; i < 5; i++)
        {
            actorRefs[i] = new ActorReferenceImpl(DUMMY_ACTOR, 4);
        }

        executorService = Executors.newSingleThreadExecutor();
    }

    @After
    public void cleanUp()
    {
        scheduler.close();

        executorService.shutdown();
    }

    @Test
    public void shouldSubmitTasks()
    {
        // when
        scheduler.schedule(DUMMY_ACTOR);
        scheduler.schedule(DUMMY_ACTOR);
        scheduler.schedule(DUMMY_ACTOR);
        scheduler.schedule(DUMMY_ACTOR);

        executorService.submit(scheduler);

        waitUntil(() -> submittedActors.get() >= 4);

        // then
        verify(mockRunner1, times(2)).submitActor(any());
        verify(mockRunner2, times(2)).submitActor(any());
    }

    @Test
    public void shouldCalculateActorDurationAvg()
    {
        final ActorReferenceImpl actorRef = actorRefs[0];

        // when
        actorRef.addDurationSample(50);
        actorRef.addDurationSample(40);
        actorRef.addDurationSample(60);
        actorRef.addDurationSample(50);

        // then
        assertThat(actorRef.getDuration()).isEqualTo(50);

        actorRef.addDurationSample(90);

        assertThat(actorRef.getDuration()).isEqualTo(60);
    }


    @Test
    public void shouldNotCalculateNegativeAvg()
    {
        final ActorReferenceImpl actorRef = actorRefs[0];

        // when
        actorRef.addDurationSample(800);
        actorRef.addDurationSample(2);
        actorRef.addDurationSample(2);
        actorRef.addDurationSample(2);

        // then
        assertThat(actorRef.getDuration()).isEqualTo(201.5);

        actorRef.addDurationSample(5);
        assertThat(actorRef.getDuration()).isEqualTo(2.75);

        actorRef.addDurationSample(1);
        assertThat(actorRef.getDuration()).isEqualTo(2.5);

        actorRef.addDurationSample(1);
        assertThat(actorRef.getDuration()).isEqualTo(2.25);

        actorRef.addDurationSample(1);
        assertThat(actorRef.getDuration()).isEqualTo(2.0);

        actorRef.addDurationSample(1);
        assertThat(actorRef.getDuration()).isEqualTo(1.0);
    }

    @Test
    @Ignore("disable rescheduling of actors to test if related to bug https://github.com/zeebe-io/zeebe/issues/596")
    public void shouldBalanceWorkload() throws Exception
    {
        // given
        actorRefs[0].addDurationSample(100);
        actorRefs[1].addDurationSample(20);
        actorRefs[2].addDurationSample(10);

        actorRefs[3].addDurationSample(40);
        actorRefs[4].addDurationSample(30);

        when(mockRunner1.getActors()).thenReturn(Arrays.asList(actorRefs[0], actorRefs[1], actorRefs[2]));
        when(mockRunner2.getActors()).thenReturn(Arrays.asList(actorRefs[3], actorRefs[4]));

        // when
        scheduler.schedule(DUMMY_ACTOR);
        executorService.submit(scheduler);

        waitUntil(() -> submittedActors.get() > 0);

        // then
        verify(mockRunner1).reclaimActor(eq(actorRefs[1]), any());
    }

    @Test
    public void shouldNotBalanceWorkloadIfLessThanThreshold() throws Exception
    {
        // given
        actorRefs[0].addDurationSample(70);
        actorRefs[1].addDurationSample(20);
        actorRefs[2].addDurationSample(10);

        actorRefs[3].addDurationSample(40);
        actorRefs[4].addDurationSample(40);

        when(mockRunner1.getActors()).thenReturn(Arrays.asList(actorRefs[0], actorRefs[1], actorRefs[2]));
        when(mockRunner2.getActors()).thenReturn(Arrays.asList(actorRefs[3], actorRefs[4]));

        // when
        scheduler.schedule(DUMMY_ACTOR);
        executorService.submit(scheduler);

        waitUntil(() -> submittedActors.get() > 0);

        // then
        verify(mockRunner1, never()).reclaimActor(any(), any());
    }

    @Test
    public void shouldNotBalanceWorkloadIfOnlyOneActor() throws Exception
    {
        // given
        actorRefs[0].addDurationSample(100);

        actorRefs[1].addDurationSample(20);
        actorRefs[2].addDurationSample(10);

        when(mockRunner1.getActors()).thenReturn(Arrays.asList(actorRefs[0]));
        when(mockRunner2.getActors()).thenReturn(Arrays.asList(actorRefs[1], actorRefs[2]));

        // when
        scheduler.schedule(DUMMY_ACTOR);
        executorService.submit(scheduler);

        waitUntil(() -> submittedActors.get() > 0);

        // then
        verify(mockRunner1, never()).reclaimActor(any(), any());
    }

    @Test
    public void shouldNotBalanceWorkloadIfThisLeadToImbalance() throws Exception
    {
        // given
        actorRefs[0].addDurationSample(60);
        actorRefs[1].addDurationSample(40);

        actorRefs[2].addDurationSample(50);

        when(mockRunner1.getActors()).thenReturn(Arrays.asList(actorRefs[0], actorRefs[1]));
        when(mockRunner2.getActors()).thenReturn(Arrays.asList(actorRefs[2]));

        // when
        scheduler.schedule(DUMMY_ACTOR);
        executorService.submit(scheduler);

        waitUntil(() -> submittedActors.get() > 0);

        // then
        verify(mockRunner1, never()).reclaimActor(any(), any());
    }

}
