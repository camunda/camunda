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

import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.TasksClient;
import io.zeebe.client.TopicsClient;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.client.impl.clustering.BrokerInfoImpl;
import io.zeebe.client.impl.job.TaskSubscription;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;

public class BrokerLeaderChangeTest
{
    public static final String TASK_TYPE = "testTask";

    public AutoCloseableRule closeables = new AutoCloseableRule();
    public Timeout testTimeout = Timeout.seconds(90);
    public ClientRule clientRule = new ClientRule(false);
    public ClusteringRule clusteringRule = new ClusteringRule(closeables, clientRule);

    @Rule
    public RuleChain ruleChain =
        RuleChain.outerRule(closeables)
                 .around(testTimeout)
                 .around(clientRule)
                 .around(clusteringRule);

    protected TopicsClient topicClient;
    protected TasksClient taskClient;

    @Before
    public void setUp()
    {
        topicClient = clientRule.topics();
        taskClient = clientRule.tasks();
    }

    @Test
    @Ignore("https://github.com/zeebe-io/zeebe/issues/844")
    public void shouldChangeLeaderAfterLeaderDies()
    {
        // given
        clusteringRule.createTopic(clientRule.getDefaultTopic(), 1, 3);

        final BrokerInfoImpl leaderForPartition = clusteringRule.getLeaderForPartition(1);
        final SocketAddress leaderAddress = leaderForPartition.getSocketAddress();

        final TaskEvent taskEvent = taskClient.create(clientRule.getDefaultTopic(), TASK_TYPE)
                                              .execute();

        // when
        clusteringRule.stopBroker(leaderAddress);
        final TaskCompleter taskCompleter = new TaskCompleter(taskEvent);

        // then
        taskCompleter.waitForTaskCompletion();

        taskCompleter.close();
    }

    class TaskCompleter
    {

        private final AtomicBoolean isTaskCompleted = new AtomicBoolean(false);
        private final TaskSubscription taskSubscription;
        private final TopicSubscription topicSubscription;

        TaskCompleter(TaskEvent task)
        {
            final long eventKey = task.getMetadata().getKey();

            taskSubscription = doRepeatedly(() -> taskClient.newTaskSubscription(clientRule.getDefaultTopic())
                .taskType(TASK_TYPE)
                .lockOwner("taskCompleter")
                .lockTime(Duration.ofMinutes(1))
                .handler((c, t) ->
                {
                    if (t.getMetadata().getKey() == eventKey)
                    {
                        c.complete(t).withoutPayload().execute();
                    }
                })
                .open()
            )
                .until(Objects::nonNull, "Failed to open task subscription for task completion");

            topicSubscription = doRepeatedly(() -> topicClient.newSubscription(clientRule.getDefaultTopic())
                 .startAtHeadOfTopic()
                 .forcedStart()
                 .name("taskObserver")
                 .taskEventHandler(e ->
                 {
                     if (TASK_TYPE.equals(e.getType()) && "COMPLETED".equals(e.getState()))
                     {
                         isTaskCompleted.set(true);
                     }
                 })
                 .open()
            )
                .until(Objects::nonNull, "Failed to open topic subscription for task completion");
        }

        void waitForTaskCompletion()
        {
            waitUntil(isTaskCompleted::get, 100, "Failed to wait for task completion");
        }

        void close()
        {
            if (!taskSubscription.isClosed())
            {
                taskSubscription.close();
            }

            if (!topicSubscription.isClosed())
            {
                topicSubscription.close();
            }
        }
    }
}
