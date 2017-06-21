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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

public class ActorSchedulerTest
{
    private static final Actor DUMMY_ACTOR = () -> 1;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

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

    @Test
    public void shouldVerifyThreadCount()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("thread count must be greater than 0");

        ActorSchedulerImpl.newBuilder()
            .threadCount(0)
            .build();
    }

    @Test
    public void shouldVerifyBaseIterationsPerActor()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("base iterations per actor must be greater than 0");

        ActorSchedulerImpl.newBuilder()
            .baseIterationsPerActor(0)
            .build();
    }

    @Test
    public void shouldVerifyDurationSampleCount()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("duration sample count must be greater than 0");

        ActorSchedulerImpl.newBuilder()
            .durationSampleCount(0)
            .build();
    }

    @Test
    public void shouldVerifyDurationSamplePeriodNotNull()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("duration sample period must not be null");

        ActorSchedulerImpl.newBuilder()
            .durationSamplePeriod(null)
            .build();
    }

    @Test
    public void shouldVerifyDurationSamplePeriodGreterThanZero()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("duration sample period must be greater than PT0S");

        ActorSchedulerImpl.newBuilder()
            .durationSamplePeriod(Duration.ofNanos(0))
            .build();
    }

    @Test
    public void shouldVerifyImbalanceThresholdLessThanOne()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("imbalance threshold must be less than or equal to 1.0");

        ActorSchedulerImpl.newBuilder()
            .imbalanceThreshold(1.5)
            .build();
    }

    @Test
    public void shouldVerifyImbalanceThresholdGreaterThanZero()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("imbalance threshold must be greater than or equal to 0.0");

        ActorSchedulerImpl.newBuilder()
            .imbalanceThreshold(-0.5)
            .build();
    }

    @Test
    public void shouldVerifyIdleStrategy()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("runner idle strategy must not be null");

        ActorSchedulerImpl.newBuilder()
            .runnerIdleStrategy(null)
            .build();
    }

    @Test
    public void shouldVerifyErrorHandler()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("runner error handler must not be null");

        ActorSchedulerImpl.newBuilder()
            .runnerErrorHander(null)
            .build();
    }

    @Test
    public void shouldVerifySchedulerInitialBackoffNotNull()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("scheduler initial backoff must not be null");

        ActorSchedulerImpl.newBuilder()
            .schedulerInitialBackoff(null)
            .build();
    }

    @Test
    public void shouldVerifySchedulerInitialBackoffGreterThanZero()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("scheduler initial backoff must be greater than PT0S");

        ActorSchedulerImpl.newBuilder()
            .schedulerInitialBackoff(Duration.ofNanos(0))
            .build();
    }

    @Test
    public void shouldVerifySchedulerMaxBackoffNotNull()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("scheduler max backoff must not be null");

        ActorSchedulerImpl.newBuilder()
            .schedulerMaxBackoff(null)
            .build();
    }

    @Test
    public void shouldVerifySchedulerMaxBackoffGreterThanInitialBackoff()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("scheduler max backoff must be greater than PT10S");

        ActorSchedulerImpl.newBuilder()
            .schedulerInitialBackoff(Duration.ofSeconds(10))
            .schedulerMaxBackoff(Duration.ofNanos(5))
            .build();
    }
}
