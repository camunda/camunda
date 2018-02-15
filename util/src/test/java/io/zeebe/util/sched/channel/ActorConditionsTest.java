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
package io.zeebe.util.sched.channel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorConditionImpl;
import io.zeebe.util.sched.ActorJob;

public class ActorConditionsTest
{

    @Test
    public void shouldAddCondition()
    {
        // given
        final ActorConditions actorConditions = new ActorConditions();

        // when
        final ActorJob job = new ActorJob();
        final ActorCondition condition = new ActorConditionImpl("name", job);
        actorConditions.registerConsumer(condition);

        // then
        assertThat(actorConditions.consumers).hasSize(1);
        assertThat(actorConditions.consumers).containsExactly(condition);
    }

    @Test
    public void shouldAddConditions()
    {
        // given
        final ActorConditions actorConditions = new ActorConditions();

        // when
        final ActorJob job = new ActorJob();
        final ActorCondition condition1 = new ActorConditionImpl("1", job);
        actorConditions.registerConsumer(condition1);

        final ActorCondition condition2 = new ActorConditionImpl("2", job);
        actorConditions.registerConsumer(condition2);

        final ActorCondition condition3 = new ActorConditionImpl("3", job);
        actorConditions.registerConsumer(condition3);

        // then
        assertThat(actorConditions.consumers).hasSize(3);
        assertThat(actorConditions.consumers).containsExactly(condition1, condition2, condition3);
    }

    @Test
    public void shouldRemoveCondition()
    {
        // given
        final ActorConditions actorConditions = new ActorConditions();
        final ActorJob job = new ActorJob();
        final ActorCondition condition = new ActorConditionImpl("name", job);
        actorConditions.registerConsumer(condition);

        // when
        actorConditions.removeConsumer(condition);

        // then
        assertThat(actorConditions.consumers).hasSize(0);
    }

    @Test
    public void shouldRemoveConditionInMiddle()
    {
        // given
        final ActorConditions actorConditions = new ActorConditions();
        final ActorJob job = new ActorJob();
        final ActorCondition condition1 = new ActorConditionImpl("1", job);
        actorConditions.registerConsumer(condition1);

        final ActorCondition condition2 = new ActorConditionImpl("2", job);
        actorConditions.registerConsumer(condition2);

        final ActorCondition condition3 = new ActorConditionImpl("3", job);
        actorConditions.registerConsumer(condition3);

        // when
        actorConditions.removeConsumer(condition2);

        // then
        assertThat(actorConditions.consumers).hasSize(2);
        assertThat(actorConditions.consumers).containsExactly(condition1, condition3);
    }

    @Test
    public void shouldRemoveFirstCondition()
    {
        // given
        final ActorConditions actorConditions = new ActorConditions();
        final ActorJob job = new ActorJob();
        final ActorCondition condition1 = new ActorConditionImpl("1", job);
        actorConditions.registerConsumer(condition1);

        final ActorCondition condition2 = new ActorConditionImpl("2", job);
        actorConditions.registerConsumer(condition2);

        final ActorCondition condition3 = new ActorConditionImpl("3", job);
        actorConditions.registerConsumer(condition3);

        // when
        actorConditions.removeConsumer(condition1);

        // then
        assertThat(actorConditions.consumers).hasSize(2);
        assertThat(actorConditions.consumers).containsExactly(condition2, condition3);
    }

}
