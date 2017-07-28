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
package io.zeebe.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.TaskEventHandler;
import io.zeebe.test.util.TestUtil;

public class TaskTopicSubscriptionTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public Timeout timeout = Timeout.seconds(20);

    protected ZeebeClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
    }

    @Test
    public void shouldReceiveTaskPOJOEvents()
    {
        // given
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
                .addCustomHeader("key", "value")
                .payload("{}")
                .retries(2)
                .execute();

        final RecordingTaskPOJOEventHandler handler = new RecordingTaskPOJOEventHandler();

        // when
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .startAtHeadOfTopic()
            .taskEventHandler(handler)
            .name("sub-1")
            .open();

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEvents() == 2);

        final TaskEvent event1 = handler.getEvent(0);
        assertThat(event1.getState()).isEqualTo("CREATE");
        assertThat(event1.getHeaders()).isEmpty();
        assertThat(event1.getCustomHeaders()).containsExactly(entry("key", "value"));
        assertThat(event1.getLockExpirationTime()).isNull();
        assertThat(event1.getLockOwner()).isNull();
        assertThat(event1.getRetries()).isEqualTo(2);
        assertThat(event1.getType()).isEqualTo("foo");
        assertThat(event1.getPayload()).isEqualTo("{}");

        final TaskEvent event2 = handler.getEvent(1);
        assertThat(event2.getState()).isEqualTo("CREATED");
    }


    @Test
    public void shouldInvokeDefaultHandler() throws IOException
    {
        // given
        final TaskEvent task = clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
                .addCustomHeader("key", "value")
                .payload("{}")
                .execute();

        final RecordingEventHandler handler = new RecordingEventHandler();

        // when no POJO handler is registered
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .startAtHeadOfTopic()
            .handler(handler)
            .name("sub-2")
            .open();

        // then
        TestUtil.waitUntil(() -> handler.numRecordedTaskEvents() == 2);

        final long taskKey = task.getMetadata().getKey();
        handler.assertTaskEvent(0, taskKey, "CREATE");
        handler.assertTaskEvent(1, taskKey, "CREATED");
    }

    protected static class RecordingTaskPOJOEventHandler implements TaskEventHandler
    {
        protected List<TaskEvent> events = new ArrayList<>();

        @Override
        public void handle(TaskEvent event) throws Exception
        {
            this.events.add(event);
        }

        public TaskEvent getEvent(int index)
        {
            return events.get(index);
        }

        public int numRecordedEvents()
        {
            return events.size();
        }

    }
}
