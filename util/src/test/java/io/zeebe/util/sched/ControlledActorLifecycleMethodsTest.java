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

import io.zeebe.util.sched.future.ActorFuture;
import org.junit.Rule;
import org.junit.Test;

import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;

public class ControlledActorLifecycleMethodsTest
{
    @Rule
    public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

    @Test
    public void shouldNotExecuteAnyFurtherJobAfterOnCloseCallback()
    {
        // given
        final List<String> lifecycle = new ArrayList<>();

        final ZbActor actor = new ZbActor()
        {
            @Override
            protected void onActorClosing()
            {
                lifecycle.add("closing");
                super.onActorClosing();
            }
        };

        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        schedulerRule.workUntilDone();
        startingFuture.join();

        // when
        actor.actor.close(); // => adds closejob
        actor.actor.call(() -> lifecycle.add("call"));
        schedulerRule.workUntilDone();

        // then
        assertThat(lifecycle).containsExactly("closing"); // and not "call"
    }
}
