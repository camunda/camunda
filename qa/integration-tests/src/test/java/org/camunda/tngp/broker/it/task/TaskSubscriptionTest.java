package org.camunda.tngp.broker.it.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.it.util.RecordingTaskEventHandler.eventType;
import static org.camunda.tngp.broker.it.util.RecordingTaskEventHandler.retries;
import static org.camunda.tngp.test.util.TestUtil.doRepeatedly;
import static org.camunda.tngp.test.util.TestUtil.waitUntil;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.broker.it.util.RecordingTaskEventHandler;
import org.camunda.tngp.broker.it.util.RecordingTaskHandler;
import org.camunda.tngp.client.ClientProperties;
import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.event.TaskEvent;
import org.camunda.tngp.client.task.PollableTaskSubscription;
import org.camunda.tngp.client.task.Task;
import org.camunda.tngp.client.task.TaskSubscription;
import org.camunda.tngp.client.task.impl.TaskEventType;
import org.camunda.tngp.test.util.TestUtil;
import org.camunda.tngp.util.time.ClockUtil;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class TaskSubscriptionTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule(() ->
    {
        final Properties properties = new Properties();

        properties.put(ClientProperties.CLIENT_TASK_EXECUTION_AUTOCOMPLETE, false);

        return properties;
    });

    public RecordingTaskEventHandler recordingTaskEventHandler = new RecordingTaskEventHandler(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(recordingTaskEventHandler);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public Timeout timeout = Timeout.seconds(20);

    @After
    public void cleanUp()
    {
        ClockUtil.reset();
    }

    @Test
    public void shouldOpenSubscription() throws InterruptedException
    {
        // given
        final TaskTopicClient topicClient = clientRule.taskTopic();

        final Long taskKey = topicClient.create()
            .taskType("foo")
            .execute();

        // when
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        topicClient.newTaskSubscription()
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner(1)
            .open();

        // then
        waitUntil(() -> !taskHandler.getHandledTasks().isEmpty());

        assertThat(taskHandler.getHandledTasks()).hasSize(1);
        assertThat(taskHandler.getHandledTasks().get(0).getKey()).isEqualTo(taskKey);
    }

    @Test
    public void shouldCompleteTask() throws InterruptedException
    {
        // given
        final TaskTopicClient topicClient = clientRule.taskTopic();

        final Long taskKey = topicClient.create()
            .taskType("foo")
            .payload("{ \"a\" : 1 }")
            .addHeader("b", "2")
            .execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        topicClient.newTaskSubscription()
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner(5)
            .open();

        waitUntil(() -> !taskHandler.getHandledTasks().isEmpty());

        // when
        final Long result = topicClient.complete()
            .taskKey(taskKey)
            .lockOwner(5)
            .taskType("foo")
            .payload("{ \"a\" : 2 }")
            .addHeader("b", "3")
            .addHeader("c", "4")
            .execute();

        // then
        assertThat(result).isEqualTo(taskKey);
        waitUntil(() -> recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.COMPLETED)));

        TaskEvent taskEvent = recordingTaskEventHandler.getTaskEvents(eventType(TaskEventType.CREATE)).get(0);
        assertThat(taskEvent.getLockExpirationTime()).isNull();
        assertThat(taskEvent.getLockOwner()).isNull();

        taskEvent = recordingTaskEventHandler.getTaskEvents(eventType(TaskEventType.CREATED)).get(0);
        assertThat(taskEvent.getLockExpirationTime()).isNull();
        assertThat(taskEvent.getLockOwner()).isEqualTo(-1);

        taskEvent = recordingTaskEventHandler.getTaskEvents(eventType(TaskEventType.LOCKED)).get(0);
        assertThat(taskEvent.getLockExpirationTime()).isNotNull();
        assertThat(taskEvent.getLockOwner()).isEqualTo(5);
    }

    @Test
    public void shouldCompletionTaskInHandler() throws InterruptedException
    {
        // given
        final TaskTopicClient topicClient = clientRule.taskTopic();

        final Long taskKey = topicClient.create()
            .taskType("foo")
            .payload("{\"a\":1}")
            .addHeader("b", "2")
            .execute();

        // when
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler(task ->
        {
            task.setPayload("{\"a\":3}");
            task.complete();
        });

        topicClient.newTaskSubscription()
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner(2)
            .open();

        // then
        waitUntil(() -> !taskHandler.getHandledTasks().isEmpty());

        assertThat(taskHandler.getHandledTasks()).hasSize(1);

        final Task task = taskHandler.getHandledTasks().get(0);
        assertThat(task.getKey()).isEqualTo(taskKey);
        assertThat(task.getType()).isEqualTo("foo");
        assertThat(task.getLockExpirationTime()).isGreaterThan(Instant.now());

        waitUntil(() -> recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.COMPLETED)));

        final TaskEvent taskEvent = recordingTaskEventHandler.getTaskEvents(eventType(TaskEventType.COMPLETED)).get(0);
        assertThat(taskEvent.getPayload()).isEqualTo("{\"a\":3}");
        assertThat(task.getHeaders()).containsEntry("b", "2");
    }

    @Test
    public void shouldCloseSubscription() throws InterruptedException
    {
        // given
        final TaskTopicClient topicClient = clientRule.taskTopic();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        final TaskSubscription subscription = topicClient.newTaskSubscription()
                .handler(taskHandler)
                .taskType("foo")
                .lockTime(Duration.ofMinutes(5))
                .lockOwner(1)
                .open();

        // when
        subscription.close();

        // then
        topicClient.create()
            .taskType("foo")
            .execute();

        waitUntil(() -> recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.CREATED)));

        assertThat(taskHandler.getHandledTasks()).isEmpty();
        assertThat(recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.LOCK))).isFalse();
    }

    @Test
    public void shouldFetchAndHandleTasks()
    {
        // given

        final int numTasks = 50;
        for (int i = 0; i < numTasks; i++)
        {
            clientRule.taskTopic().create()
                .taskType("foo")
                .execute();
        }

        final RecordingTaskHandler handler = new RecordingTaskHandler(Task::complete);

        clientRule.taskTopic().newTaskSubscription()
            .handler(handler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner(1)
            .taskFetchSize(10)
            .open();

        // when
        waitUntil(() -> handler.getHandledTasks().size() == numTasks);

        // then
        assertThat(handler.getHandledTasks()).hasSize(numTasks);
    }

    @Test
    public void shouldMarkTaskAsFailedAndRetryIfHandlerThrowsException()
    {
        // given
        final TaskTopicClient topicClient = clientRule.taskTopic();

        final Long taskKey = topicClient.create()
            .taskType("foo")
            .execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler(
            t ->
            {
                throw new RuntimeException("expected failure");
            },
            Task::complete
            );

        // when
        topicClient.newTaskSubscription()
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner(1)
            .open();

        // then the subscription is not broken and other tasks are still handled
        waitUntil(() -> taskHandler.getHandledTasks().size() == 2);

        assertThat(taskHandler.getHandledTasks()).extracting("key").contains(taskKey, taskKey);
        assertThat(recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.FAILED))).isTrue();
        waitUntil(() -> recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.COMPLETED)));
    }

    @Test
    public void shouldNotLockTaskIfRetriesAreExhausted()
    {
        // given
        final TaskTopicClient topicClient = clientRule.taskTopic();

        topicClient.create()
            .taskType("foo")
            .retries(1)
            .execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler(
            t ->
            {
                throw new RuntimeException("expected failure");
            });

        // when
        topicClient.newTaskSubscription()
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner(1)
            .open();

        waitUntil(() -> recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.FAILED).and(retries(0))));

        assertThat(taskHandler.getHandledTasks()).hasSize(1);
    }

    @Test
    public void shouldUpdateTaskRetries()
    {
        // given
        final TaskTopicClient topicClient = clientRule.taskTopic();

        final Long taskKey = topicClient.create()
            .taskType("foo")
            .retries(1)
            .execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler(
            t ->
            {
                throw new RuntimeException("expected failure");
            },
            Task::complete);

        topicClient.newTaskSubscription()
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner(1)
            .open();

        waitUntil(() -> taskHandler.getHandledTasks().size() == 1);
        waitUntil(() -> recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.FAILED).and(retries(0))));

        // when
        final Long result = topicClient.updateRetries()
            .taskKey(taskKey)
            .taskType("foo")
            .retries(2)
            .execute();

        // then
        assertThat(result).isEqualTo(taskKey);

        waitUntil(() -> taskHandler.getHandledTasks().size() == 2);
        waitUntil(() -> recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.COMPLETED)));
    }

    @Test
    public void shouldExpireTaskLock() throws InterruptedException
    {
        // given
        final TaskTopicClient topicClient = clientRule.taskTopic();

        final Long taskKey = topicClient.create()
            .taskType("foo")
            .execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler(task ->
        {
            // don't complete the task - just wait for lock expiration
        });

        topicClient.newTaskSubscription()
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner(5)
            .open();

        waitUntil(() -> taskHandler.getHandledTasks().size() == 1);

        // when
        ClockUtil.setCurrentTime(Instant.now().plus(Duration.ofMinutes(5)));

        // then
        waitUntil(() -> taskHandler.getHandledTasks().size() == 2);

        assertThat(taskHandler.getHandledTasks())
            .hasSize(2)
            .extracting("key").contains(taskKey, taskKey);

        assertThat(recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.LOCK_EXPIRED))).isTrue();
    }

    @Test
    public void shouldOpenSubscriptionAfterClientReconnect()
    {
        // given
        final TngpClient client = clientRule.getClient();

        clientRule.taskTopic().create()
            .taskType("foo")
            .execute();

        // when
        client.disconnect();
        client.connect();

        // then
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        clientRule.taskTopic().newTaskSubscription()
                .taskType("foo")
                .lockTime(Duration.ofMinutes(5))
                .lockOwner(1)
                .handler(taskHandler)
                .open();

        waitUntil(() -> !taskHandler.getHandledTasks().isEmpty());

        assertThat(taskHandler.getHandledTasks()).hasSize(1);
    }

    @Test
    public void shouldGiveTaskToSingleSubscription()
    {
        // given
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler(Task::complete);

        clientRule.taskTopic().newTaskSubscription()
            .taskType("foo")
            .lockTime(Duration.ofHours(1))
            .lockOwner(1)
            .handler(taskHandler)
            .open();

        clientRule.taskTopic().newTaskSubscription()
            .taskType("foo")
            .lockTime(Duration.ofHours(2))
            .lockOwner(1)
            .handler(taskHandler)
            .open();

        // when
        clientRule.taskTopic()
            .create()
            .taskType("foo")
            .execute();

        waitUntil(() -> taskHandler.getHandledTasks().size() == 1);

        // then
        assertThat(taskHandler.getHandledTasks()).hasSize(1);
    }

    @Test
    public void shouldPollTasks()
    {
        // given
        final TaskTopicClient topicClient = clientRule.taskTopic();

        final PollableTaskSubscription subscription = topicClient.newPollableTaskSubscription()
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner(3)
            .open();

        final Long taskKey = topicClient.create()
            .taskType("foo")
            .payload("{ \"a\" : 1 }")
            .addHeader("b", "2")
            .execute();

        // when
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler(Task::complete);

        doRepeatedly(() -> subscription.poll(taskHandler))
            .until((workCount) -> workCount == 1);

        assertThat(taskHandler.getHandledTasks()).hasSize(1);

        final Task task = taskHandler.getHandledTasks().get(0);
        assertThat(task.getKey()).isEqualTo(taskKey);
        assertThat(task.getType()).isEqualTo("foo");
        assertThat(task.getLockExpirationTime()).isGreaterThan(Instant.now());
        assertThat(task.getPayload()).isEqualTo("{\"a\":1}");
        assertThat(task.getHeaders()).containsEntry("b", "2");

        waitUntil(() -> recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.COMPLETED)));
    }

    @Test
    public void shouldSubscribeToMultipleTypes() throws InterruptedException
    {
        // given
        final TaskTopicClient topicClient = clientRule.taskTopic();

        topicClient.create()
            .taskType("foo")
            .execute();

        topicClient.create()
            .taskType("bar")
            .execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        topicClient.newTaskSubscription()
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner(5)
            .open();

        topicClient.newTaskSubscription()
            .handler(taskHandler)
            .taskType("bar")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner(5)
            .open();

        waitUntil(() -> taskHandler.getHandledTasks().size() == 2);
    }

    @Test
    public void shouldHandleMoreTasksThanPrefetchCapacity()
    {
        // given
        final int subscriptionCapacity = 16;

        for (int i = 0; i < subscriptionCapacity + 1; i++)
        {
            clientRule.taskTopic().create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();
        }
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        // when
        clientRule.taskTopic().newTaskSubscription()
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner(5)
            .open();

        // then
        TestUtil.waitUntil(() -> taskHandler.getHandledTasks().size() > subscriptionCapacity);
    }

}
