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

import static io.zeebe.broker.it.util.TopicEventRecorder.taskEvent;
import static io.zeebe.broker.it.util.TopicEventRecorder.taskRetries;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingTaskHandler;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.impl.job.PollableTaskSubscription;
import io.zeebe.client.impl.job.TaskSubscription;
import io.zeebe.client.impl.job.impl.CreateTaskCommandImpl;
import io.zeebe.client.impl.topic.Topic;
import io.zeebe.client.impl.topic.Topics;
import io.zeebe.test.util.TestUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.*;

public class TaskSubscriptionTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule, false);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(eventRecorder);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public Timeout timeout = Timeout.seconds(20);

    @Test
    public void shouldOpenSubscription() throws InterruptedException
    {
        // given
        final TaskEvent task = clientRule.tasks().create(clientRule.getDefaultTopic(), "foo").execute();

        // when
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("test")
            .open();

        // then
        waitUntil(() -> !taskHandler.getHandledTasks().isEmpty());

        final List<TaskEvent> tasks = taskHandler.getHandledTasks();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getMetadata().getKey()).isEqualTo(task.getMetadata().getKey());
    }

    @Test
    public void shouldCompleteTask() throws InterruptedException
    {
        // given
        eventRecorder.startRecordingEvents();

        final TaskEvent task = clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
            .payload("{ \"a\" : 1 }")
            .addCustomHeader("b", "2")
            .execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("test")
            .open();

        waitUntil(() -> !taskHandler.getHandledTasks().isEmpty());
        final TaskEvent lockedTask = taskHandler.getHandledTasks().get(0);

        // when
        final TaskEvent result = clientRule.tasks().complete(lockedTask)
            .payload("{ \"a\" : 2 }")
            .execute();

        // then
        assertThat(result.getMetadata().getKey()).isEqualTo(task.getMetadata().getKey());
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));

        TaskEvent taskEvent = eventRecorder.getTaskEvents(taskEvent("CREATE")).get(0);
        assertThat(taskEvent.getLockExpirationTime()).isNull();
        assertThat(taskEvent.getLockOwner()).isNull();

        taskEvent = eventRecorder.getTaskEvents(taskEvent("CREATED")).get(0);
        assertThat(taskEvent.getLockExpirationTime()).isNull();

        taskEvent = eventRecorder.getTaskEvents(taskEvent("LOCKED")).get(0);
        assertThat(taskEvent.getLockExpirationTime()).isNotNull();
        assertThat(taskEvent.getLockOwner()).isEqualTo("test");
    }

    @Test
    public void shouldCompletionTaskInHandler() throws InterruptedException
    {
        // given
        eventRecorder.startRecordingEvents();

        final TaskEvent task = clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
            .payload("{\"a\":1}")
            .addCustomHeader("b", "2")
            .execute();

        // when
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler((c, t) -> c.complete(t).payload("{\"a\":3}").execute());

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("test")
            .open();

        // then
        waitUntil(() -> !taskHandler.getHandledTasks().isEmpty());

        assertThat(taskHandler.getHandledTasks()).hasSize(1);

        final TaskEvent subscribedTask = taskHandler.getHandledTasks().get(0);
        assertThat(subscribedTask.getMetadata().getKey()).isEqualTo(task.getMetadata().getKey());
        assertThat(subscribedTask.getType()).isEqualTo("foo");
        assertThat(subscribedTask.getLockExpirationTime()).isAfter(Instant.now());

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));

        final TaskEvent taskEvent = eventRecorder.getTaskEvents(taskEvent("COMPLETED")).get(0);
        assertThat(taskEvent.getPayload()).isEqualTo("{\"a\":3}");
        assertThat(task.getCustomHeaders()).containsEntry("b", "2");
    }

    @Test
    public void shouldCloseSubscription() throws InterruptedException
    {
        // given
        eventRecorder.startRecordingEvents();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        final TaskSubscription subscription = clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
                .handler(taskHandler)
                .taskType("foo")
                .lockTime(Duration.ofMinutes(5))
                .lockOwner("test")
                .open();

        // when
        subscription.close();

        // then
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo").execute();

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("CREATED")));

        assertThat(taskHandler.getHandledTasks()).isEmpty();
        assertThat(eventRecorder.hasTaskEvent(taskEvent("LOCK"))).isFalse();
    }

    @Test
    public void shouldFetchAndHandleTasks()
    {
        // given
        final int numTasks = 50;
        for (int i = 0; i < numTasks; i++)
        {
            clientRule.tasks().create(clientRule.getDefaultTopic(), "foo").execute();
        }

        final RecordingTaskHandler handler = new RecordingTaskHandler((c, t) ->
        {
            c.complete(t).withoutPayload().execute();
        });

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .handler(handler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("test")
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
        eventRecorder.startRecordingEvents();

        final TaskEvent task = clientRule.tasks().create(clientRule.getDefaultTopic(), "foo").execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler(
            (c, t) ->
            {
                throw new RuntimeException("expected failure");
            },
            (c, t) -> c.complete(t).withoutPayload().execute()
            );

        // when
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("test")
            .open();

        // then the subscription is not broken and other tasks are still handled
        waitUntil(() -> taskHandler.getHandledTasks().size() == 2);

        final long taskKey = task.getMetadata().getKey();
        assertThat(taskHandler.getHandledTasks()).extracting("metadata.key").containsExactly(taskKey, taskKey);
        assertThat(eventRecorder.hasTaskEvent(taskEvent("FAILED"))).isTrue();
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
    }

    @Test
    public void shouldNotLockTaskIfRetriesAreExhausted()
    {
        // given
        eventRecorder.startRecordingEvents();

        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
            .retries(1)
            .execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler(
            (c, t) ->
            {
                throw new RuntimeException("expected failure");
            });

        // when
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("test")
            .open();

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("FAILED").and(taskRetries(0))));

        assertThat(taskHandler.getHandledTasks()).hasSize(1);
    }

    @Test
    public void shouldUpdateTaskRetries()
    {
        // given
        eventRecorder.startRecordingEvents();

        final TaskEvent task = clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
            .retries(1)
            .execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler(
            (c, t) ->
            {
                throw new RuntimeException("expected failure");
            },
            (c, t) -> c.complete(t).withoutPayload().execute());

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("test")
            .open();

        waitUntil(() -> taskHandler.getHandledTasks().size() == 1);
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("FAILED").and(taskRetries(0))));

        // when
        final TaskEvent updatedTask = clientRule.tasks().updateRetries(task)
            .retries(2)
            .execute();

        // then
        assertThat(updatedTask.getMetadata().getKey()).isEqualTo(task.getMetadata().getKey());

        waitUntil(() -> taskHandler.getHandledTasks().size() == 2);
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
    }

    @Test
    public void shouldExpireTaskLock()
    {
        // given
        eventRecorder.startRecordingEvents();

        final TaskEvent task = clientRule.tasks().create(clientRule.getDefaultTopic(), "foo").execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler((c, t) ->
        {
            // don't complete the task - just wait for lock expiration
        });

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("test")
            .open();

        waitUntil(() -> taskHandler.getHandledTasks().size() == 1);

        // then
        doRepeatedly(() -> brokerRule.getClock().addTime(Duration.ofMinutes(5)))
            .until((v) -> taskHandler.getHandledTasks().size() == 2);

        final long taskKey = task.getMetadata().getKey();
        assertThat(taskHandler.getHandledTasks())
            .hasSize(2)
            .extracting("metadata.key").containsExactly(taskKey, taskKey);

        assertThat(eventRecorder.hasTaskEvent(taskEvent("LOCK_EXPIRED"))).isTrue();
    }

    @Test
    public void shouldGiveTaskToSingleSubscription()
    {
        // given
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler((c, t) -> c.complete(t).withoutPayload().execute());

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockTime(Duration.ofHours(1))
            .lockOwner("test")
            .handler(taskHandler)
            .open();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockTime(Duration.ofHours(2))
            .lockOwner("test")
            .handler(taskHandler)
            .open();

        // when
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo").execute();

        waitUntil(() -> taskHandler.getHandledTasks().size() == 1);

        // then
        assertThat(taskHandler.getHandledTasks()).hasSize(1);
    }

    @Test
    public void shouldPollTasks()
    {
        // given
        eventRecorder.startRecordingEvents();

        final PollableTaskSubscription subscription = clientRule.tasks().newPollableTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("test")
            .open();

        final TaskEvent task = clientRule.tasks()
                .create(clientRule.getDefaultTopic(), "foo")
                .payload("{\"a\":1}")
                .addCustomHeader("b", "2")
                .execute();

        // when
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler((c, t) -> c.complete(t).withoutPayload().execute());

        doRepeatedly(() -> subscription.poll(taskHandler))
            .until((workCount) -> workCount == 1);

        assertThat(taskHandler.getHandledTasks()).hasSize(1);

        final TaskEvent subscribedTasks = taskHandler.getHandledTasks().get(0);
        assertThat(subscribedTasks.getMetadata().getKey()).isEqualTo(task.getMetadata().getKey());
        assertThat(subscribedTasks.getType()).isEqualTo("foo");
        assertThat(subscribedTasks.getLockExpirationTime()).isAfter(Instant.now());
        assertThat(subscribedTasks.getPayload()).isEqualTo("{\"a\":1}");
        assertThat(subscribedTasks.getCustomHeaders()).containsEntry("b", "2");

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
    }

    @Test
    public void shouldSubscribeToMultipleTypes() throws InterruptedException
    {
        // given
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo").execute();
        clientRule.tasks().create(clientRule.getDefaultTopic(), "bar").execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("test")
            .open();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .handler(taskHandler)
            .taskType("bar")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("test")
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
            clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
                .addCustomHeader("key", "value")
                .payload("{}")
                .execute();
        }
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        // when
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .handler(taskHandler)
            .taskType("foo")
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("test")
            .open();

        // then
        TestUtil.waitUntil(() -> taskHandler.getHandledTasks().size() > subscriptionCapacity);
    }

    @Test
    public void shouldReceiveTasksFromMultiplePartitions()
    {
        // given
        final String topicName = "gyros";
        final int numPartitions = 2;

        final ZeebeClient client = clientRule.getClient();
        client.topics().create(topicName, numPartitions).execute();
        clientRule.waitUntilTopicsExists(topicName);

        final Topics topics = client.topics().getTopics().execute();
        final Topic topic = topics.getTopics().stream()
            .filter(t -> t.getName().equals(topicName))
            .findFirst()
            .get();

        final Integer[] partitionIds = topic.getPartitions().stream()
                .mapToInt(p -> p.getId())
                .boxed()
                .toArray(Integer[]::new);

        final String taskType = "foooo";

        final RecordingTaskHandler handler = new RecordingTaskHandler();

        createTaskOnPartition(client, topicName, partitionIds[0], taskType);
        createTaskOnPartition(client, topicName, partitionIds[1], taskType);

        // when
        client.tasks().newTaskSubscription(topicName)
            .handler(handler)
            .lockOwner("foo")
            .lockTime(Duration.ofSeconds(30))
            .taskType(taskType)
            .open();

        // then
        waitUntil(() -> handler.getHandledTasks().size() == 2);

        final Integer[] receivedPartitionIds = handler.getHandledTasks().stream()
            .map(t -> t.getMetadata().getPartitionId())
            .toArray(Integer[]::new);

        assertThat(receivedPartitionIds).containsExactlyInAnyOrder(partitionIds);
    }

    protected void createTaskOnPartition(ZeebeClient client, String topic, int partition, String type)
    {
        final CreateTaskCommandImpl createTaskCommand = (CreateTaskCommandImpl) client.tasks().create(topic, type);
        createTaskCommand.getCommand().setPartitionId(partition);
        createTaskCommand.execute();
    }

}
