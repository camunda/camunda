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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

public class SingleThreadActorSchedulerTest
{
    private static final Actor DUMMY_ACTOR = () -> 1;

    @Rule
    public Timeout timeout = Timeout.seconds(5);

    @Mock
    private ActorRunner mockRunner;

    private SingleThreadActorScheduler scheduler;

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

        doAnswer(submitActorCallback).when(mockRunner).submitActor(any());

        scheduler = new SingleThreadActorScheduler(() -> mockRunner, t -> new ActorReferenceImpl(t, 16));

        actorRefs = new ActorReferenceImpl[3];
        for (int i = 0; i < 3; i++)
        {
            actorRefs[i] = new ActorReferenceImpl(DUMMY_ACTOR, 16);
        }
    }

    @After
    public void cleanUp()
    {
        scheduler.close();
    }

    @Test
    public void shouldSubmitTasks()
    {
        // when
        scheduler.schedule(DUMMY_ACTOR);
        scheduler.schedule(DUMMY_ACTOR);
        scheduler.schedule(DUMMY_ACTOR);

        waitUntil(() -> submittedActors.get() >= 3);

        // then
        verify(mockRunner, times(3)).submitActor(any());
    }

    @Test
    public void shouldCloseRunner()
    {
        // when
        scheduler.close();

        // then
        verify(mockRunner).close();
    }

}
