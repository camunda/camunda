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
package io.zeebe.util.sched.lifecycle;

import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.STARTED;
import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.STARTING;
import static io.zeebe.util.sched.lifecycle.LifecycleRecordingActor.FULL_LIFECYCLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.newArrayList;

import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;

public class ActorLifecyclePhasesTest
{
    @Rule
    public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

    @Test
    public void shouldStartActor()
    {
        // given
        final LifecycleRecordingActor actor = new LifecycleRecordingActor();

        // when
        final ActorFuture<Void> startedFuture = schedulerRule.submitActor(actor);
        schedulerRule.workUntilDone();

        // then
        assertThat(startedFuture).isDone();
        assertThat(actor.phases).isEqualTo(newArrayList(STARTING, STARTED));
    }

    @Test
    public void shouldCloseActor()
    {
        // given
        final LifecycleRecordingActor actor = new LifecycleRecordingActor();
        schedulerRule.submitActor(actor);
        schedulerRule.workUntilDone();

        // when
        final ActorFuture<Void> closeFuture = actor.close();
        schedulerRule.workUntilDone();

        // then
        assertThat(closeFuture).isDone();
        assertThat(actor.phases).isEqualTo(FULL_LIFECYCLE);
    }

    @Test
    public void shouldDoFullLifecycleIfClosedConcurrently()
    {
        // given
        final LifecycleRecordingActor actor = new LifecycleRecordingActor();
        schedulerRule.submitActor(actor);

        // when
        final ActorFuture<Void> closeFuture = actor.close(); // request close before doing work
        schedulerRule.workUntilDone();

        // then
        assertThat(closeFuture).isDone();
        assertThat(actor.phases).isEqualTo(FULL_LIFECYCLE);
    }

}
