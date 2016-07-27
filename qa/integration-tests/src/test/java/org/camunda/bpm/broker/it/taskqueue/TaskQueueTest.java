package org.camunda.bpm.broker.it.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.client.AsyncTasksClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.BrokerRequestException;
import org.camunda.tngp.client.cmd.LockedTask;
import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

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
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testCycle()
    {
        final TngpClient client = clientRule.getClient();
        final AsyncTasksClient taskService = client.tasks();

        System.out.println("Creating task");

        final Long taskId = taskService.create()
            .taskQueueId(0)
            .payload("foo")
            .taskType("bar")
            .execute();

        assertThat(taskId).isGreaterThanOrEqualTo(0);

        System.out.println("Locking task");

        final LockedTasksBatch lockedTasksBatch = taskService.pollAndLock()
            .taskQueueId(0)
            .taskType("bar")
            .lockTime(100 * 1000)
            .execute();

        assertThat(lockedTasksBatch.getLockedTasks()).hasSize(1);

        final LockedTask task = lockedTasksBatch.getLockedTasks().get(0);
        assertThat(task.getId()).isEqualTo(taskId);

        System.out.println("Completing task");

        final Long completedTaskId = taskService.complete()
            .taskQueueId(0)
            .taskId(taskId)
            .execute();

        assertThat(completedTaskId).isEqualTo(taskId);
    }

    @Test
    public void testCannotCompleteUnlockedTask()
    {
        final TngpClient client = clientRule.getClient();
        final AsyncTasksClient taskService = client.tasks();

        final Long taskId = taskService.create()
            .taskQueueId(0)
            .payload("foo")
            .taskType("bar")
            .execute();

        assertThat(taskId).isGreaterThanOrEqualTo(0);

        exception.expect(BrokerRequestException.class);
        exception.expectMessage("Task does not exist or is not locked");

        taskService.complete()
            .taskQueueId(0)
            .taskId(taskId)
            .execute();
    }

    @Test
    public void testCannotCompleteTaskTwiceInParallel() throws InterruptedException
    {
        // given
        final TngpClient client = clientRule.getClient();
        final AsyncTasksClient taskClient = client.tasks();

        final Long taskId = taskClient.create()
            .taskQueueId(0)
            .payload("foo")
            .taskType("bar")
            .execute();

        taskClient.pollAndLock()
            .taskQueueId(0)
            .taskType("bar")
            .lockTime(100 * 1000)
            .execute();

        final CompleteTaskRunnable r1 = new CompleteTaskRunnable(taskClient, taskId);
        final CompleteTaskRunnable r2 = new CompleteTaskRunnable(taskClient, taskId);

        final Thread t1 = new Thread(r1);
        final Thread t2 = new Thread(r2);

        // when
        t1.start();
        t2.start();

        t1.join();
        t2.join();

        // then
        final Set<Long> results = new HashSet<>();
        results.add(r1.result);
        results.add(r2.result);

        assertThat(results).contains(taskId, null);
    }

    public static class CompleteTaskRunnable implements Runnable
    {
        protected AsyncTasksClient taskClient;
        protected long taskId;
        protected Long result;

        public CompleteTaskRunnable(AsyncTasksClient taskClient, long taskId)
        {
            this.taskClient = taskClient;
            this.taskId = taskId;
        }

        public void run()
        {
            result = taskClient.complete()
                    .taskQueueId(0)
                    .taskId(taskId)
                    .execute();
        }

        public Long getResult()
        {
            return result;
        }

    }
}
