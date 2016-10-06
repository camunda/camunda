package org.camunda.bpm.broker.it.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;
import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.bpm.broker.it.TestUtil;
import org.camunda.bpm.broker.it.process.ProcessModels;
import org.camunda.tngp.client.AsyncTasksClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.WorkflowsClient;
import org.camunda.tngp.client.cmd.WorkflowDefinition;
import org.camunda.tngp.client.task.PollableTaskSubscription;
import org.camunda.tngp.client.task.Task;
import org.camunda.tngp.client.task.TaskHandler;
import org.camunda.tngp.client.task.TaskSubscription;
import org.camunda.tngp.client.task.TaskSubscriptionBuilder;
import org.camunda.tngp.client.task.impl.TaskSubscriptionBuilderImpl;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class TaskSubscriptionTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testHandler() throws InterruptedException
    {
        // given
        final TngpClient client = clientRule.getClient();
        final AsyncTasksClient taskService = client.tasks();

        final Long taskId = taskService.create()
            .taskQueueId(0)
            .payload("foo")
            .taskType("bar")
            .execute();

        // when
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        taskService.newSubscription()
            .handler(taskHandler)
            .taskType("bar")
            .open();

        // then
        TestUtil.doRepeatedly(() -> null)
            .until((o) -> !taskHandler.handledTasks.isEmpty());

        assertThat(taskHandler.handledTasks).hasSize(1);
        assertThat(taskHandler.handledTasks.get(0).getId()).isEqualTo(taskId);
    }

    @Test
    public void testCompletionInHandler() throws InterruptedException
    {
        // given
        final TngpClient client = clientRule.getClient();
        final AsyncTasksClient taskService = client.tasks();
        final WorkflowsClient workflowsClient = client.workflows();

        final WorkflowDefinition workflowDefinition = workflowsClient.deploy()
            .bpmnModelInstance(ProcessModels.TWO_TASKS_PROCESS)
            .execute();

        workflowsClient
            .start()
            .workflowDefinitionId(workflowDefinition.getId())
            .execute();

        // when
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        taskService.newSubscription()
            .handler(taskHandler)
            .taskType("bar")
            .open();

        taskService.newSubscription()
            .handler(taskHandler)
            .taskType("foo")
            .open();

        // then
        TestUtil.waitUntil(() -> taskHandler.handledTasks.size() == 2);

        assertThat(taskHandler.handledTasks).hasSize(2);

        assertThat(taskHandler.handledTasks.get(0).getType()).isEqualTo("foo");
        assertThat(taskHandler.handledTasks.get(1).getType()).isEqualTo("bar");
    }

    @Test
    public void testCloseSubscription() throws InterruptedException
    {
        // given
        final TngpClient client = clientRule.getClient();
        final AsyncTasksClient taskService = client.tasks();
        final WorkflowsClient workflowsClient = client.workflows();

        final WorkflowDefinition workflowDefinition = workflowsClient.deploy()
            .bpmnModelInstance(ProcessModels.ONE_TASK_PROCESS)
            .execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        final TaskSubscription subscription = taskService.newSubscription()
            .handler(taskHandler)
            .taskType("foo")
            .open();

        // when
        subscription.close();

        // then
        workflowsClient
            .start()
            .workflowDefinitionId(workflowDefinition.getId())
            .execute();

        Thread.sleep(1000L);

        assertThat(taskHandler.handledTasks).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHandlerThrowingExceptionShouldNotBreakSubscription()
    {
        // given
        final TngpClient client = clientRule.getClient();
        final AsyncTasksClient taskService = client.tasks();
        final WorkflowsClient workflowsClient = client.workflows();

        final WorkflowDefinition workflowDefinition = workflowsClient.deploy()
            .bpmnModelInstance(ProcessModels.TWO_TASKS_PROCESS)
            .execute();

        final QueueBasedHandler taskHandler = new QueueBasedHandler(
            (t) ->
            {
                throw new RuntimeException("oh no");
            },
            (t) -> t.complete()
            );

        taskService.newSubscription()
            .handler(taskHandler)
            .taskType("foo")
            .open();

        // when
        workflowsClient
            .start()
            .workflowDefinitionId(workflowDefinition.getId())
            .execute();

        // then the subscription is not broken and other tasks are still handled
        TestUtil.waitUntil(() -> taskHandler.getNumTasksHandled() == 1);

        workflowsClient
            .start()
            .workflowDefinitionId(workflowDefinition.getId())
            .execute();

        TestUtil.waitUntil(() -> taskHandler.getNumTasksHandled() == 2);
    }

    @Test
    public void testPollableSubscription()
    {
        // given
        final TngpClient client = clientRule.getClient();
        final AsyncTasksClient taskService = client.tasks();

        final PollableTaskSubscription subscription = taskService.newPollableSubscription()
            .lockTime(123L)
            .taskType("foo")
            .taskQueueId(0)
            .open();

        final Long taskId = taskService.create()
            .taskQueueId(0)
            .taskType("foo")
            .execute();

        // when
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        TestUtil.doRepeatedly(() -> subscription.poll(taskHandler))
            .until((workCount) -> workCount == 1);

        assertThat(taskHandler.handledTasks).hasSize(1);
        assertThat(taskHandler.handledTasks.get(0).getId()).isEqualTo(taskId);
    }

    @Test
    public void shouldOpenSubscriptionAfterClientReconnect()
    {
        // given
        final TngpClient client = clientRule.getClient();

        client.tasks().create()
            .taskQueueId(0)
            .taskType("foo")
            .execute();

        // when
        client.disconnect();
        client.connect();

        // then
        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        client.tasks().newSubscription()
                .taskQueueId(0)
                .taskType("foo")
                .lockTime(100L)
                .handler(taskHandler)
                .open();

        TestUtil.waitUntil(() -> !taskHandler.getHandledTasks().isEmpty());

        assertThat(taskHandler.getHandledTasks()).hasSize(1);

    }

    @Test
    public void shouldHandleMoreTasksThanPrefetchConfigured()
    {
        // given
        final TngpClient client = clientRule.getClient();

        final int numTasks = 100;
        for (int i = 0; i < numTasks; i++)
        {
            client.tasks().create()
                .taskQueueId(0)
                .taskType("foo")
                .execute();
        }

        final RecordingTaskHandler handler = new RecordingTaskHandler();

        final TaskSubscriptionBuilder builder = client.tasks().newSubscription()
            .taskQueueId(0)
            .taskType("foo")
            .lockTime(10000L)
            .handler(handler);

        // when
        ((TaskSubscriptionBuilderImpl) builder)
            .taskPrefetchSize(2)
            .open();

        TestUtil.waitUntil(() -> handler.handledTasks.size() == numTasks);

        // then
        assertThat(handler.handledTasks).hasSize(numTasks);
    }

    @Test
    public void shouldGiveTaskToSingleSubscription()
    {
        // given
        final TngpClient client = clientRule.getClient();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();

        client.tasks().newSubscription()
            .taskQueueId(0)
            .lockTime(Duration.ofHours(1L))
            .taskType("foo")
            .handler(taskHandler)
            .open();

        client.tasks().newSubscription()
            .taskQueueId(0)
            .lockTime(Duration.ofHours(1L))
            .taskType("foo")
            .handler(taskHandler)
            .open();

        // when
        client
            .tasks()
            .create()
            .taskQueueId(0)
            .taskType("foo")
            .execute();

        TestUtil.waitUntil(() -> taskHandler.handledTasks.size() == 1);

        // then
        assertThat(taskHandler.handledTasks).hasSize(1);
    }

    public static class RecordingTaskHandler implements TaskHandler
    {
        protected List<Task> handledTasks = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void handle(Task task)
        {
            handledTasks.add(task);
            task.complete();
        }

        public List<Task> getHandledTasks()
        {
            return handledTasks;
        }

        public void clear()
        {
            handledTasks.clear();
        }
    }

    public static class QueueBasedHandler implements TaskHandler
    {
        protected final ManyToManyConcurrentArrayQueue<Consumer<Task>> actionsQueue;
        protected AtomicInteger numTasksHandled = new AtomicInteger();

        public QueueBasedHandler(Consumer<Task>... actions)
        {
            actionsQueue = new ManyToManyConcurrentArrayQueue<>(actions.length);
            for (Consumer<Task> action : actions)
            {
                actionsQueue.add(action);
            }
        }

        @Override
        public void handle(Task task)
        {
            numTasksHandled.incrementAndGet();

            final Consumer<Task> action = actionsQueue.poll();

            if (action != null)
            {
                action.accept(task);
            }
        }

        public int getNumTasksHandled()
        {
            return numTasksHandled.get();
        }
    }

}
