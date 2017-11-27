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
import java.util.regex.Pattern;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.startup.BrokerRestartTest;
import io.zeebe.client.WorkflowsClient;
import io.zeebe.client.task.TaskSubscription;
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
    public static final int CREATION_TIMES = 1_000_000;
    public static final URL PATH = LargeWorkflowTest.class.getResource("");

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(() -> brokerConfig(PATH.getPath()));
    public ClientRule clientRule = new ClientRule();

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

            if (i % 100_000 == 0)
            {
                System.out.println("Iteration: " + i);
            }
        }
    }

    @Test
    public void shouldCreateAndCompleteWorkflowInstances()
    {
        final WorkflowsClient workflowService = clientRule.workflows();

        // when
        for (int i = 0; i < CREATION_TIMES; i++)
        {
            workflowService.create(clientRule.getDefaultTopic())
                           .bpmnProcessId("extended-order-process")
                           .latestVersion()
                           .payload("{ \"orderId\": 31243, \"orderStatus\": \"NEW\", \"orderItems\": [435, 182, 376] }")
                           .executeAsync();
        }

        // then
        final TaskSubscription reserveOrderSubscription = clientRule.tasks()
                                                                    .newTaskSubscription(clientRule.getDefaultTopic())
                                                                    .taskType("reserveOrderItems")
                                                                    .lockOwner("stocker")
                                                                    .lockTime(Duration.ofMinutes(5))
                                                                    .handler((tasksClient, task) -> {
                                                                        tasksClient.complete(task)
                                                                                   .payload("{ \"orderStatus\": \"RESERVED\" }")
                                                                                   .execute();
                                                                    })
                                                                    .open();
    }
}
