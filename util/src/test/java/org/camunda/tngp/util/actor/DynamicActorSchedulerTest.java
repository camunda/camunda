/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.util.actor;

import static org.camunda.tngp.util.TestUtil.waitUntil;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
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

        scheduler = new ActorSchedulerRunnable(runners, t -> new ActorReferenceImpl(t, 16), 0.25, Duration.ofSeconds(10), Duration.ofSeconds(10));

        actorRefs = new ActorReferenceImpl[5];
        for (int i = 0; i < 5; i++)
        {
            actorRefs[i] = new ActorReferenceImpl(DUMMY_ACTOR, 16);
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
