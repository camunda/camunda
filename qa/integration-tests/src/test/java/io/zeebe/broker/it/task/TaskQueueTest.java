/**
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
import static io.zeebe.logstreams.log.LogStream.DEFAULT_PARTITION_ID;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_TOPIC_NAME;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.TaskTopicClient;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.cmd.BrokerRequestException;
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

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public Timeout testTimeout = Timeout.seconds(10);


    @Test
    public void shouldCreateTask()
    {
        final Long taskKey = clientRule.taskTopic().create()
            .taskType("foo")
            .addHeader("k1", "a")
            .addHeader("k2", "b")
            .payload("{ \"payload\" : 123 }")
            .execute();

        assertThat(taskKey).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void shouldFailCreateTaskIfTopicNameIsNotValid()
    {
        final ZeebeClient client = clientRule.getClient();

        thrown.expect(RuntimeException.class);
        thrown.expectMessage(String.format("Cannot execute command. No broker for topic with name '%s' and partition id '%d' found", "unknown-topic", DEFAULT_PARTITION_ID));

        client.taskTopic("unknown-topic", DEFAULT_PARTITION_ID).create()
            .taskType("foo")
            .addHeader("k1", "a")
            .addHeader("k2", "b")
            .payload("{ \"payload\" : 123 }")
            .execute();
    }

    @Test
    public void shouldFailCreateTaskIfPartitionIdIsNotValid()
    {
        final ZeebeClient client = clientRule.getClient();

        thrown.expect(RuntimeException.class);
        thrown.expectMessage(String.format("Cannot execute command. No broker for topic with name '%s' and partition id '%d' found", DEFAULT_TOPIC_NAME, 999));

        client.taskTopic(DEFAULT_TOPIC_NAME, 999).create()
            .taskType("foo")
            .addHeader("k1", "a")
            .addHeader("k2", "b")
            .payload("{ \"payload\" : 123 }")
            .execute();
    }

    @Test
    @Ignore
    public void testCannotCompleteUnlockedTask()
    {
        final TaskTopicClient topicClient = clientRule.taskTopic();

        final Long taskId = topicClient.create()
            .payload("{}")
            .taskType("bar")
            .execute();

        assertThat(taskId).isGreaterThanOrEqualTo(0);

        thrown.expect(BrokerRequestException.class);
        thrown.expectMessage("Task does not exist or is not locked");

        topicClient.complete()
            .taskKey(taskId)
            .execute();
    }

    @Test
    public void testValidateTopicNameNotEmpty()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("topic name must not be empty");

        // when
        client.taskTopic("", DEFAULT_PARTITION_ID);
    }

    @Test
    public void testValidateTopicNameNotNull()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("topic name must not be null");

        // when
        client.taskTopic(null, DEFAULT_PARTITION_ID);
    }

    @Test
    public void testValidatePartitionId()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("partition id must be greater than or equal to 0");

        // when
        client.taskTopic(DEFAULT_TOPIC_NAME, -1);
    }

}
