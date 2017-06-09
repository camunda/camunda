package org.camunda.tngp.broker.it.task;

import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.logstreams.log.LogStream.*;

import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.BrokerRequestException;
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
        final TngpClient client = clientRule.getClient();

        thrown.expect(BrokerRequestException.class);
        thrown.expectMessage(String.format("Cannot execute command. Topic with name '%s' and partition id '%d' not found", "unknown-topic", DEFAULT_PARTITION_ID));

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
        final TngpClient client = clientRule.getClient();

        thrown.expect(BrokerRequestException.class);
        thrown.expectMessage(String.format("Cannot execute command. Topic with name '%s' and partition id '%d' not found", DEFAULT_TOPIC_NAME, 999));

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
        final TngpClient client = clientRule.getClient();

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
        final TngpClient client = clientRule.getClient();

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
        final TngpClient client = clientRule.getClient();

        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("partition id must be greater than or equal to 0");

        // when
        client.taskTopic(DEFAULT_TOPIC_NAME, -1);
    }

}
