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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;

import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;

public class ControlledRunnableExecutionTest
{

    @Rule
    public ControlledActorSchedulerRule scheduler = new ControlledActorSchedulerRule();

    @Test
    public void shouldInvokeRunFromWithinActor()
    {
        // given
        final Runner runner = new Runner()
        {
            @Override
            protected void onActorStarted()
            {
                this.doRun();
            }
        };

        scheduler.submitActor(runner);

        // when
        scheduler.workUntilDone();

        // then
        assertThat(runner.runs).isEqualTo(1);
    }

    @Test
    public void shouldInvokeRunFromAnotherActor()
    {
        // given
        final Runner runner = new Runner();
        final Actor invoker = new Actor()
        {
            protected void onActorStarted()
            {
                runner.doRun();
            }
        };

        scheduler.submitActor(runner);
        scheduler.submitActor(invoker);

        // when
        scheduler.workUntilDone();

        // then
        assertThat(runner.runs).isEqualTo(1);
    }

    @Test
    public void shouldSubmitRunnableToCorrectActorTask()
    {
        // given
        final List<Actor> actorContext = new ArrayList<>();
        final Runner runner = new Runner(() -> actorContext.add(ActorThread.current().getCurrentTask().getActor()));

        final Actor invoker = new Actor()
        {
            protected void onActorStarted()
            {
                runner.doRun();
            }
        };

        scheduler.submitActor(runner);
        scheduler.submitActor(invoker);

        // when
        scheduler.workUntilDone();

        // then
        assertThat(actorContext).containsExactly(runner);
    }

    @Test
    public void shouldNotInvokeRunFromOutside()
    {
        // given
        final Runner runner = new Runner();

        // when/then
        assertThatThrownBy(() -> runner.doRun())
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("Incorrect usage of actor. run(...): must be called from actor thread");
    }

    class Runner extends Actor
    {
        int runs = 0;
        Runnable onExecution;

        Runner()
        {
            this(null);
        }

        Runner(Runnable onExecution)
        {
            this.onExecution = onExecution;
        }

        public void doRun()
        {
            actor.run(() ->
            {
                if (onExecution != null)
                {
                    onExecution.run();
                }
                runs++;
            });
        }
    }
}

