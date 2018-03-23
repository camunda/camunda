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
package io.zeebe.broker.it.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.cmd.BrokerErrorException;
import io.zeebe.client.event.TaskEvent;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

/**
 * Tests the entire cycle of task creation, polling and completion as a smoke test for when something gets broken
 *
 * @author Lindhauer
 */
public class TaskQueueTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule(() ->
    {
        final Properties p = new Properties();
        p.setProperty(ClientProperties.CLIENT_REQUEST_TIMEOUT_SEC, "3");
        return p;
    }, true);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public Timeout testTimeout = Timeout.seconds(15);


    @Test
    public void shouldCreateTask()
    {
        final TaskEvent taskEvent = clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
            .addCustomHeader("k1", "a")
            .addCustomHeader("k2", "b")
            .payload("{ \"payload\" : 123 }")
            .execute();

        assertThat(taskEvent).isNotNull();

        final long taskKey = taskEvent.getMetadata().getKey();
        assertThat(taskKey).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void shouldFailCreateTaskIfTopicNameIsNotValid()
    {
        final ZeebeClient client = clientRule.getClient();

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Cannot determine target partition for request. " +
                "Request was: [ topic = unknown-topic, partition = any, event type = TASK, state = CREATE ]");

        client.tasks().create("unknown-topic", "foo")
            .addCustomHeader("k1", "a")
            .addCustomHeader("k2", "b")
            .payload("{ \"payload\" : 123 }")
            .execute();
    }

    @Test
    @Ignore
    public void testCannotCompleteUnlockedTask()
    {
        final TaskEvent task = clientRule.tasks().create(clientRule.getDefaultTopic(), "bar")
            .payload("{}")
            .execute();

        thrown.expect(BrokerErrorException.class);
        thrown.expectMessage("Task does not exist or is not locked");

        clientRule.tasks().complete(task).execute();
    }
}
