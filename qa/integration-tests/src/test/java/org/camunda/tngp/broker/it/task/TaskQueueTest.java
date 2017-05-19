package org.camunda.tngp.broker.it.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.logstreams.log.LogStream.DEFAULT_PARTITION_ID;
import static org.camunda.tngp.logstreams.log.LogStream.DEFAULT_TOPIC_NAME;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.broker.it.util.ParallelRequests;
import org.camunda.tngp.broker.it.util.ParallelRequests.SilentFuture;
import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.BrokerRequestException;
import org.camunda.tngp.client.task.LockedTask;
import org.camunda.tngp.client.task.LockedTasksBatch;
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
    public void testCycle()
    {
        final TaskTopicClient topicClient = clientRule.taskTopic();

        System.out.println("Creating task");

        final Long taskId = topicClient.create()
            .payload("{}")
            .taskType("bar")
            .execute();

        assertThat(taskId).isGreaterThanOrEqualTo(0);

        System.out.println("Locking task");

        final LockedTasksBatch lockedTasksBatch = topicClient.pollAndLock()
            .taskType("bar")
            .lockTime(100 * 1000)
            .execute();

        assertThat(lockedTasksBatch.getLockedTasks()).hasSize(1);

        final LockedTask task = lockedTasksBatch.getLockedTasks().get(0);
        assertThat(task.getId()).isEqualTo(taskId);

        System.out.println("Completing task");

        final Long completedTaskId = topicClient.complete()
            .taskKey(taskId)
            .execute();

        assertThat(completedTaskId).isEqualTo(taskId);
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
    @Ignore
    public void testCannotCompleteTaskTwiceInParallel()
    {
        // given
        final TaskTopicClient topicClient = clientRule.taskTopic();

        final Long taskId = topicClient.create()
            .payload("foo")
            .taskType("bar")
            .execute();

        topicClient.pollAndLock()
            .taskType("bar")
            .lockTime(Duration.ofHours(1L))
            .execute();


        final ParallelRequests parallelRequests = ParallelRequests.prepare();

        final SilentFuture<Long> future1 = parallelRequests.submitRequest(
            () -> topicClient.complete()
                .taskKey(taskId)
                .execute());

        final SilentFuture<Long> future2 = parallelRequests.submitRequest(
            () -> topicClient.complete()
                .taskKey(taskId)
                .execute());

        // when
        parallelRequests.execute();

        // then
        final Set<Long> results = new HashSet<>();
        results.add(future1.get());
        results.add(future2.get());

        assertThat(results).contains(taskId, null);
    }

    @Test
    @Ignore
    public void testLockZeroTasks()
    {
        // given
        final TaskTopicClient topicClient = clientRule.taskTopic();

        // when
        final LockedTasksBatch lockedTasksBatch = topicClient.pollAndLock()
                .taskType("bar")
                .lockTime(100 * 1000)
                .execute();

        // when
        assertThat(lockedTasksBatch.getLockedTasks()).isEmpty();
    }

    @Test
    @Ignore
    public void testLockTaskWithPayload()
    {
        // given
        final TaskTopicClient topicClient = clientRule.taskTopic();

        System.out.println("Creating task");

        final Long taskId = topicClient.create()
            .payload("foo")
            .taskType("bar")
            .execute();

        // when
        final LockedTasksBatch lockedTasksBatch = topicClient.pollAndLock()
            .taskType("bar")
            .lockTime(10000L)
            .execute();

        // then
        assertThat(lockedTasksBatch).isNotNull();

        final List<LockedTask> tasks = lockedTasksBatch.getLockedTasks();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getId()).isEqualTo(taskId);
        assertThat(tasks.get(0).getPayloadString()).isEqualTo("foo");

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
