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
package io.zeebe.broker.it.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.TestLoggers;
import io.zeebe.broker.workflow.data.WorkflowInstanceState;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.WorkflowsClient;
import io.zeebe.client.event.WorkflowInstanceEvent;
import io.zeebe.client.task.TaskHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.slf4j.Logger;

public class LargeWorkflowTest
{
    public static final Logger LOG = TestLoggers.TEST_LOGGER;
    public static final int CREATION_TIMES = 1_000_000;
    public static final int REPORT_INTERVAL = 10000;

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule(() ->
    {
        final Properties p = new Properties();
        p.setProperty(ClientProperties.CLIENT_REQUEST_TIMEOUT_SEC, "180");
        p.setProperty(ClientProperties.CLIENT_TASK_EXECUTION_THREADS, "4");
        p.setProperty(ClientProperties.CLIENT_MAXREQUESTS, "256");

        return p;
    }, true);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule)
                                          .around(clientRule);

    @Before
    public void deployProcess()
    {
        final WorkflowsClient workflowService = clientRule.workflows();

        workflowService.deploy(clientRule.getDefaultTopic())
                       .addResourceFromClasspath("workflows/extended-order-process.bpmn")
                       .execute();

        workflowService.deploy(clientRule.getDefaultTopic())
                       .addResourceFromClasspath("workflows/forty-tasks-process.bpmn")
                       .execute();
    }

    @Test
    public void shouldCreateWorkflowInstancesSynchronously()
    {
        final WorkflowsClient workflowService = clientRule.workflows();

        LOG.info("Creating {} workflows", CREATION_TIMES);

        for (int i = 0; i < CREATION_TIMES; i++)
        {
            workflowService.create(clientRule.getDefaultTopic())
                           .bpmnProcessId("extended-order-process")
                           .latestVersion()
                           .payload("{ \"orderId\": 31243, \"orderStatus\": \"NEW\", \"orderItems\": [435, 182, 376] }")
                           .execute();

            if (i % REPORT_INTERVAL == 0)
            {
                LOG.info("Workflows started: {}/{} ({}%)", i, CREATION_TIMES, i * 100 / CREATION_TIMES);
            }
        }
    }

    @Test
    public void shouldCreateAndCompleteWorkflowInstances() throws Exception
    {
        final WorkflowsClient workflowService = clientRule.workflows();

        final int taskCount = 10;
        final int expectedTasks = CREATION_TIMES * taskCount;
        final CompletableFuture<Void> finished = new CompletableFuture<>();
        final AtomicLong completed = new AtomicLong(0);

        // open task subscription for all tasks
        clientRule.tasks()
                  .newTaskSubscription(clientRule.getDefaultTopic())
                  .taskType("reserveOrderItems")
                  .lockOwner("test")
                  .lockTime(Duration.ofMinutes(5))
                  .taskFetchSize(128)
                  .handler(getTaskHandler(expectedTasks, finished, completed)).open();

        // create workflow instances
        final List<Future<WorkflowInstanceEvent>> futures = new ArrayList<>();
        for (int i = 0; i < CREATION_TIMES; i++)
        {
            final Future<WorkflowInstanceEvent> future =
                workflowService.create(clientRule.getDefaultTopic())
                               .bpmnProcessId("extended-order-process")
                               .latestVersion()
                               .payload("{ \"orderId\": 31243, \"orderStatus\": \"NEW\", \"orderItems\": [435, 182, 376] }")
                               .executeAsync();

            futures.add(future);
        }

        waitForWorkflowInstanceCreation(futures);

        // wait for task completion
        finished.get();
    }

    @Test
    public void shouldCreateAndCompleteWorkflowInstancesWithFortyTasks() throws Exception
    {
        final WorkflowsClient workflowService = clientRule.workflows();

        final int taskCount = 40;
        final int expectedTasks = CREATION_TIMES * taskCount;
        final CompletableFuture<Void> finished = new CompletableFuture<>();
        final AtomicLong completed = new AtomicLong(0);

        // open task subscription for all tasks
        for (int i = 0; i < 40; i++)
        {
            clientRule.tasks()
                      .newTaskSubscription(clientRule.getDefaultTopic())
                      .taskType("reserveOrderItems" + i)
                      .lockOwner("test")
                      .lockTime(Duration.ofMinutes(5))
                      .taskFetchSize(128)
                      .handler(getTaskHandler(expectedTasks, finished, completed)).open();
        }

        // create workflow instance
        final List<Future<WorkflowInstanceEvent>> futures = new ArrayList<>();
        for (int i = 0; i < CREATION_TIMES; i++)
        {
            final Future<WorkflowInstanceEvent> future =
                workflowService.create(clientRule.getDefaultTopic())
                               .bpmnProcessId("forty-tasks-process")
                               .latestVersion()
                               .payload("{ \"orderId\": 31243, \"orderStatus\": \"NEW\", \"orderItems\": [435, 182, 376] }")
                               .executeAsync();

            futures.add(future);
        }

        waitForWorkflowInstanceCreation(futures);

        // wait for task completion
        finished.get();
    }

    private void waitForWorkflowInstanceCreation(final List<Future<WorkflowInstanceEvent>> futures)
    {
        long workflowInstancesCreated = 0;

        for (final Future<WorkflowInstanceEvent> future : futures)
        {
            try
            {
                final WorkflowInstanceEvent workflowInstanceEvent = future.get();
                assertThat(workflowInstanceEvent.getState()).isEqualTo(WorkflowInstanceState.WORKFLOW_INSTANCE_CREATED.toString());
                workflowInstancesCreated++;

                if (workflowInstancesCreated % REPORT_INTERVAL == 0)
                {
                    LOG.info("Workflows started: {}/{} ({}%)", workflowInstancesCreated, CREATION_TIMES, workflowInstancesCreated * 100 / CREATION_TIMES);
                }
            }
            catch (final Exception e)
            {
                LOG.error("Failed to create workflow instance", e);
            }
        }

        assertThat(workflowInstancesCreated)
            .isEqualTo(CREATION_TIMES)
            .withFailMessage("Failed to create {} workflow instances. Only created {} instances", CREATION_TIMES, workflowInstancesCreated);
    }

    private TaskHandler getTaskHandler(final int expectedTasks, final CompletableFuture<Void> finished, final AtomicLong completed)
    {
        return (tasksClient, task) -> {
            final long c = completed.incrementAndGet();
            tasksClient.complete(task)
                       .payload("{ \"orderStatus\": \"RESERVED\" }")
                       .execute();
            if (c % REPORT_INTERVAL == 0)
            {
                LOG.info("Tasks completed: {}/{} ({}%)", c, expectedTasks, c * 100.0 / expectedTasks);
            }

            if (c >= expectedTasks)
            {
                finished.complete(null);
            }
        };
    }

}
