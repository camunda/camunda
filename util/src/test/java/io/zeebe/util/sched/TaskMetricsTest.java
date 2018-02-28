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

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.agrona.concurrent.status.ConcurrentCountersManager;
import org.junit.Rule;
import org.junit.Test;

public class TaskMetricsTest
{
    private static final String SOME_ACTOR = "someActor";

    @Rule
    public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();

    class MyActor extends ZbActor
    {
        @Override
        public String getName()
        {
            return SOME_ACTOR;
        }

        @Override
        protected void onActorStarted()
        {
            actor.runDelayed(Duration.ofHours(1), () ->
            {
                // keepalive
            });
        }

        public ActorFuture<Void> close()
        {
            return actor.close();
        }

    }

    @Test
    public void shouldAllocateTaskMetricsWhenMetricsEnabled()
    {
        // when
        actorSchedulerRule.submitActor(new MyActor(), true);

        //then
        final Set<String> labels = getAllocatedCounterLabels();
        assertThat(labels).contains(String.format("%s.taskExecutionCount", SOME_ACTOR));
        assertThat(labels).contains(String.format("%s.taskTotalExecutionTime", SOME_ACTOR));
        assertThat(labels).contains(String.format("%s.taskMaxExecutionTime", SOME_ACTOR));
    }

    @Test
    public void shouldNotAllocateTaskMetricsWhenMetricsDisabled()
    {
        // when
        actorSchedulerRule.submitActor(new MyActor(), false);

        //then
        final Set<String> labels = getAllocatedCounterLabels();
        assertThat(labels).doesNotContain(String.format("%s.taskExecutionCount", SOME_ACTOR));
        assertThat(labels).doesNotContain(String.format("%s.taskTotalExecutionTime", SOME_ACTOR));
        assertThat(labels).doesNotContain(String.format("%s.taskMaxExecutionTime", SOME_ACTOR));
    }

    @Test
    public void shouldFreeTaskMetricsWhenActorClosed()
    {
        // given
        final MyActor actor = new MyActor();
        actorSchedulerRule.submitActor(actor, true);
        Set<String> labels = getAllocatedCounterLabels();
        assertThat(labels).contains(String.format("%s.taskExecutionCount", SOME_ACTOR));
        assertThat(labels).contains(String.format("%s.taskTotalExecutionTime", SOME_ACTOR));
        assertThat(labels).contains(String.format("%s.taskMaxExecutionTime", SOME_ACTOR));

        // when
        actor.close().join();

        // then
        labels = getAllocatedCounterLabels();
        assertThat(labels).doesNotContain(String.format("%s.taskExecutionCount", SOME_ACTOR));
        assertThat(labels).doesNotContain(String.format("%s.taskTotalExecutionTime", SOME_ACTOR));
        assertThat(labels).doesNotContain(String.format("%s.taskMaxExecutionTime", SOME_ACTOR));

    }

    private Set<String> getAllocatedCounterLabels()
    {
        final ConcurrentCountersManager manager = actorSchedulerRule.getBuilder().getCountersManager();
        final Set<String> labels = new HashSet<>();

        manager.forEach((id, l) ->
        {
            labels.add(l);
        });

        return labels;
    }
}
