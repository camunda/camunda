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
package io.zeebe.broker.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.TestLoggers;
import io.zeebe.broker.task.data.TaskState;
import io.zeebe.client.TasksClient;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.task.TaskHandler;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.slf4j.Logger;

public class LargeCreateTaskClusterTest
{

    public static final Logger LOG = TestLoggers.TEST_LOGGER;
    public static final int CREATION_TIMES = 1_000_000;
    public static final int REPORT_INTERVAL = 100;

    public AutoCloseableRule closeables = new AutoCloseableRule();
    public ClientRule clientRule = new ClientRule(false);
    public ClusteringRule clusteringRule = new ClusteringRule(closeables, clientRule);

    @Rule
    public RuleChain ruleChain =
        RuleChain.outerRule(closeables)
                 .around(clientRule)
                 .around(clusteringRule);


    private void fillBrokersLeadingPartitionsMap(HashMap<SocketAddress, List<Integer>> brokersLeadingPartitions)
    {
        final SocketAddress brokerOne = ClusteringRule.BROKER_1_CLIENT_ADDRESS;
        brokersLeadingPartitions.put(brokerOne, clusteringRule.getBrokersLeadingPartitions(brokerOne));

        final SocketAddress brokerTwo = ClusteringRule.BROKER_2_CLIENT_ADDRESS;
        brokersLeadingPartitions.put(brokerTwo, clusteringRule.getBrokersLeadingPartitions(brokerTwo));

        final SocketAddress brokerThree = ClusteringRule.BROKER_3_CLIENT_ADDRESS;
        brokersLeadingPartitions.put(brokerThree, clusteringRule.getBrokersLeadingPartitions(brokerThree));
    }

    private void assertBrokersLeadingPartitions(HashMap<SocketAddress, List<Integer>> oldBrokersLeadingPartitions)
    {
        assertBrokerLeadingPartition(oldBrokersLeadingPartitions, ClusteringRule.BROKER_1_CLIENT_ADDRESS);
        assertBrokerLeadingPartition(oldBrokersLeadingPartitions, ClusteringRule.BROKER_2_CLIENT_ADDRESS);
        assertBrokerLeadingPartition(oldBrokersLeadingPartitions, ClusteringRule.BROKER_3_CLIENT_ADDRESS);
    }

    private void assertBrokerLeadingPartition(HashMap<SocketAddress, List<Integer>> oldBrokersLeadingPartitions, SocketAddress broker)
    {
        final List<Integer> newBrokerOneLeadingPartitions = clusteringRule.getBrokersLeadingPartitions(broker);
        final List<Integer> oldBrokerOneLeadingPartitions = oldBrokersLeadingPartitions.get(broker);
        assertThat(oldBrokerOneLeadingPartitions).containsExactlyInAnyOrder(newBrokerOneLeadingPartitions.toArray(new Integer[newBrokerOneLeadingPartitions.size()]));
    }

    @Test
    public void shouldCompleteStandaloneTasksAsync() throws Exception
    {
        // given cluster
        clusteringRule.createTopic(clientRule.getDefaultTopic(), 3);
        final HashMap<SocketAddress, List<Integer>> brokersLeadingPartitions = new HashMap<>();
        fillBrokersLeadingPartitionsMap(brokersLeadingPartitions);

        final CompletableFuture<Void> finished = new CompletableFuture<>();
        final AtomicLong completed = new AtomicLong(0);

        // when
        clientRule.tasks()
                  .newTaskSubscription(clientRule.getDefaultTopic())
                  .taskType("test")
                  .lockOwner("test")
                  .lockTime(Duration.ofMinutes(5))
                  .handler(getTaskHandler(CREATION_TIMES, finished, completed)).open();

        final TasksClient taskClient = clientRule.tasks();
        final int tasks = CREATION_TIMES;

        LOG.info("Creating {} tasks", tasks);


        final List<Future<TaskEvent>> futures = new ArrayList<>();
        for (int i = 0; i < tasks; i++)
        {
            final Future<TaskEvent> future = taskClient.create(clientRule.getDefaultTopic(), "test")
                                                     .executeAsync();
            futures.add(future);
        }

        // then
        waitForTaskCreation(futures);

        finished.get();

        assertBrokersLeadingPartitions(brokersLeadingPartitions);
    }


    private void waitForTaskCreation(final List<Future<TaskEvent>> futures)
    {
        long tasksCreated = 0;

        for (final Future<TaskEvent> future : futures)
        {
            try
            {
                final TaskEvent taskEvent = future.get();
                assertThat(taskEvent.getState()).isEqualTo(TaskState.CREATED.toString());
                tasksCreated++;

                if (tasksCreated % REPORT_INTERVAL == 0)
                {
                    LOG.info("Tasks created: {}/{} ({}%)", tasksCreated, CREATION_TIMES, tasksCreated * 100 / CREATION_TIMES);
                }
            }
            catch (final Exception e)
            {
                LOG.error("Failed to create task instance", e);
            }
        }

        assertThat(tasksCreated)
            .isEqualTo(CREATION_TIMES)
            .withFailMessage("Failed to create {} tasks instances. Only created {} instances", CREATION_TIMES, tasksCreated);
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
