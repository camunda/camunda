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

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.startup.BrokerRestartTest;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.WorkflowsClient;
import io.zeebe.client.task.TaskHandler;
import io.zeebe.test.util.TestFileUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 *
 */
public class LargeWorkflowTest
{
    public static final int CREATION_TIMES = 100_000;
    public static final URL PATH = LargeWorkflowTest.class.getResource("");

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(() -> brokerConfig(PATH.getPath()));
    public ClientRule clientRule = new ClientRule(() ->
    {
        final Properties p = new Properties();
        p.setProperty(ClientProperties.CLIENT_REQUEST_TIMEOUT_SEC, "180");

        return p;
    }, true);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule)
                                          .around(clientRule);

    protected static InputStream brokerConfig(String path)
    {
        final String canonicallySeparatedPath = path.replaceAll(Pattern.quote(File.separator), "/");

        return TestFileUtil.readAsTextFileAndReplace(BrokerRestartTest.class.getClassLoader()
                                                                            .getResourceAsStream("persistent-broker.cfg.toml"), StandardCharsets.UTF_8,
                                                     Collections.singletonMap("brokerFolder", canonicallySeparatedPath));
    }

    @Before
    public void deployProcess()
    {
        final WorkflowsClient workflowService = clientRule.workflows();

        workflowService.deploy(clientRule.getDefaultTopic())
                       .addResourceFromClasspath("workflows/extended-order-process.bpmn")
                       .execute();
    }

    @Test
    public void shouldCreateBunchOfWorkflowInstances()
    {
        final WorkflowsClient workflowService = clientRule.workflows();

        // when
        for (int i = 0; i < CREATION_TIMES; i++)
        {
            workflowService.create(clientRule.getDefaultTopic())
                           .bpmnProcessId("extended-order-process")
                           .latestVersion()
                           .payload("{ \"orderId\": 31243, \"orderStatus\": \"NEW\", \"orderItems\": [435, 182, 376] }")
                           .execute();

            if (i % (CREATION_TIMES / 10) == 0)
            {
                System.out.println("Iteration: " + i);
            }
        }
    }

    @Test
    public void shouldCreateAndCompleteWorkflowInstances() throws InterruptedException
    {
        final WorkflowsClient workflowService = clientRule.workflows();

        final AtomicLong completed = new AtomicLong(0);

        clientRule.tasks()
                  .newTaskSubscription(clientRule.getDefaultTopic())
                  .taskType("reserveOrderItems")
                  .lockOwner("stocker")
                  .lockTime(Duration.ofMinutes(5))
                  .handler((tasksClient, task) -> {
                      final long c = completed.incrementAndGet();
                      tasksClient.complete(task)
                                 .payload("{ \"orderStatus\": \"RESERVED\" }")
                                 .execute();
                      if (c % CREATION_TIMES == 0)
                      {
                          System.out.println("Completed: " + c);
                      }
                  }).open();

        // when
        for (int i = 0; i < CREATION_TIMES; i++)
        {
            workflowService.create(clientRule.getDefaultTopic())
                           .bpmnProcessId("extended-order-process")
                           .latestVersion()
                           .payload("{ \"orderId\": 31243, \"orderStatus\": \"NEW\", \"orderItems\": [435, 182, 376] }")
                           .executeAsync();
        }

        while (completed.get() < CREATION_TIMES * 10)
        {
            Thread.sleep(1000);
        }
    }


    @Test
    public void shouldCreateAndCompleteWorkflowInstancesWithFortyTasks() throws InterruptedException
    {
        final WorkflowsClient workflowService = clientRule.workflows();

        final AtomicLong completed = new AtomicLong(0);

        final TaskHandler taskHandler = (tasksClient, task) -> {
            final long c = completed.incrementAndGet();
            tasksClient.complete(task)
                       .payload("{ \"orderStatus\": \"RESERVED\" }")
                       .execute();
            if (c % CREATION_TIMES == 0)
            {
                System.out.println("Completed: " + c);
            }
        };

        for (int i = 0; i < 40; i++)
        {
            clientRule.tasks()
                      .newTaskSubscription(clientRule.getDefaultTopic())
                      .taskType("reserveOrderItems" + i)
                      .lockOwner("stocker")
                      .lockTime(Duration.ofMinutes(5))
                      .handler(taskHandler).open();
        }

        // when
        for (int i = 0; i < CREATION_TIMES; i++)
        {
            workflowService.create(clientRule.getDefaultTopic())
                           .bpmnProcessId("extended-order-process")
                           .latestVersion()
                           .payload("{ \"orderId\": 31243, \"orderStatus\": \"NEW\", \"orderItems\": [435, 182, 376] }")
                           .executeAsync();
        }

        while (completed.get() < CREATION_TIMES * 40)
        {
            Thread.sleep(1000);
        }
    }
}
