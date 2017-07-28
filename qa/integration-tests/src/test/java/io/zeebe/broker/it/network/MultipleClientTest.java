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
package io.zeebe.broker.it.network;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingTaskHandler;
import io.zeebe.client.event.TaskEvent;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class MultipleClientTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule client1 = new ClientRule();
    public ClientRule client2 = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(client1)
        .around(client2);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldOpenTopicSubscriptions()
    {
        // given
        final List<TaskEvent> taskEventsClient1 = new CopyOnWriteArrayList<>();
        final List<TaskEvent> taskEventsClient2 = new CopyOnWriteArrayList<>();

        client1.topics().newSubscription(client1.getDefaultTopic())
            .name("client-1")
            .taskEventHandler(e -> taskEventsClient1.add(e))
            .open();

        client2.topics().newSubscription(client2.getDefaultTopic())
            .name("client-2")
            .taskEventHandler(e -> taskEventsClient2.add(e))
            .open();

        // when
        client1.tasks().create(client1.getDefaultTopic(), "foo").execute();
        client2.tasks().create(client2.getDefaultTopic(), "bar").execute();

        // then
        waitUntil(() -> taskEventsClient1.size() + taskEventsClient2.size() >= 8);

        assertThat(taskEventsClient1).hasSize(4);
        assertThat(taskEventsClient2).hasSize(4);
    }

    @Test
    public void shouldOpenTaskSubscriptionsForDifferentTypes()
    {
        // given
        final RecordingTaskHandler handler1 = new RecordingTaskHandler();
        final RecordingTaskHandler handler2 = new RecordingTaskHandler();

        client1.tasks().newTaskSubscription(client1.getDefaultTopic())
            .handler(handler1)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("client1")
            .open();

        client2.tasks().newTaskSubscription(client2.getDefaultTopic())
            .handler(handler2)
            .taskType("bar")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("client2")
            .open();

        // when
        final TaskEvent task1 = client1.tasks().create(client1.getDefaultTopic(), "foo").execute();
        final TaskEvent task2 = client1.tasks().create(client1.getDefaultTopic(), "bar").execute();

        // then
        waitUntil(() -> handler1.getHandledTasks().size() + handler2.getHandledTasks().size() >= 2);

        assertThat(handler1.getHandledTasks()).hasSize(1);
        assertThat(handler1.getHandledTasks().get(0).getMetadata().getKey()).isEqualTo(task1.getMetadata().getKey());

        assertThat(handler2.getHandledTasks()).hasSize(1);
        assertThat(handler2.getHandledTasks().get(0).getMetadata().getKey()).isEqualTo(task2.getMetadata().getKey());
    }

}
