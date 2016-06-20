package org.camunda.bpm.broker.it.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.client.AsyncTaskService;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.LockedTask;
import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * Tests the entire cycle of task creation, polling and completion as a smoke test for when something gets broken
 *
 * @author Lindhauer
 */
public class TaskQueueIT
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Test
    public void testCycle()
    {
        final TngpClient client = clientRule.getClient();
        final AsyncTaskService taskService = client.tasks();

        Long taskId = taskService.create()
            .taskQueueId(0)
            .payload("foo")
            .taskType("bar")
            .execute();

        assertThat(taskId).isGreaterThanOrEqualTo(0);

        LockedTasksBatch lockedTasksBatch = taskService.pollAndLock()
          .taskQueueId(0)
          .taskType("bar")
          .execute();

        assertThat(lockedTasksBatch.getLockedTasks()).hasSize(1);

        LockedTask task = lockedTasksBatch.getLockedTasks().get(0);
        assertThat(task.getId()).isEqualTo(taskId);

        Long completedTaskId = taskService.complete()
            .taskId(taskId)
            .execute();

        assertThat(completedTaskId).isEqualTo(taskId);
    }
}
