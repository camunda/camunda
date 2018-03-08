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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;

public class ControlledConditionTest
{

    @Rule
    public ControlledActorSchedulerRule scheduler = new ControlledActorSchedulerRule();

    @Test
    public void shouldTerminateOnFollowUpAndYield()
    {
        // given
        final AtomicInteger invocations = new AtomicInteger(0);
        final AtomicReference<ActorCondition> condition = new AtomicReference<>();

        final Actor actor = new Actor()
        {
            @Override
            protected void onActorStarted()
            {
                condition.set(actor.onCondition("foo", this::onCondition));
            }

            protected void onCondition()
            {
                invocations.incrementAndGet();
                actor.run(this::doNothing);
                actor.yield();
            }

            protected void doNothing()
            {
            }
        };

        scheduler.submitActor(actor);
        scheduler.workUntilDone();

        // when
        condition.get().signal();
        scheduler.workUntilDone();

        // then
        assertThat(invocations.get()).isEqualTo(1);

    }
}
