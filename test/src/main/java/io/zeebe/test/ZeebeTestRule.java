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
package io.zeebe.test;

import static io.zeebe.test.TopicEventRecorder.taskKey;
import static io.zeebe.test.TopicEventRecorder.wfInstanceKey;
import static org.assertj.core.api.Assertions.fail;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.WorkflowInstanceEvent;
import org.junit.rules.ExternalResource;

public class ZeebeTestRule extends ExternalResource
{
    private EmbeddedBrokerRule brokerRule;
    private ClientRule clientRule;
    private TopicEventRecorder topicEventRecorder;

    public ZeebeTestRule()
    {
        this(() -> null, () -> new Properties());
    }

    public ZeebeTestRule(Supplier<InputStream> configSupplier, Supplier<Properties> propertiesProvider)
    {
        brokerRule = new EmbeddedBrokerRule(configSupplier);
        clientRule = new ClientRule(propertiesProvider, true);

        topicEventRecorder = new TopicEventRecorder(clientRule, clientRule.getDefaultTopic());
    }

    public ZeebeClient getClient()
    {
        return clientRule.getClient();
    }

    @Override
    protected void before() throws Throwable
    {
        brokerRule.before();
        clientRule.before();
        topicEventRecorder.before();
    }

    @Override
    protected void after()
    {
        topicEventRecorder.after();
        clientRule.after();
        brokerRule.after();
    }

    public void waitUntilWorklowInstanceCompleted(long key)
    {
        waitUntil(() -> !topicEventRecorder.getWorkflowInstanceEvents(wfInstanceKey(key)).isEmpty(), "no workflow instance found with key " + key);

        waitUntil(() ->
        {
            final WorkflowInstanceEvent event = topicEventRecorder.getLastWorkflowInstanceEvent(wfInstanceKey(key));
            return event.getState().equals("WORKFLOW_INSTANCE_COMPLETED");
        }, "workflow instance is not completed");
    }

    public void waitUntilTaskCompleted(long key)
    {
        waitUntil(() -> !topicEventRecorder.getTaskEvents(taskKey(key)).isEmpty(), "no task found with key " + key);

        waitUntil(() ->
        {
            final TaskEvent event = topicEventRecorder.getLastTaskEvent(taskKey(key));
            return event.getState().equals("COMPLETED");
        }, "task is not completed");
    }

    public void printWorkflowInstanceEvents(long key)
    {
        topicEventRecorder.getWorkflowInstanceEvents(wfInstanceKey(key)).forEach(event ->
        {
            System.out.println("> " + event);
        });
    }

    private void waitUntil(BooleanSupplier condition, String failureMessage)
    {
        final long timeout = Instant.now().plus(Duration.ofSeconds(5)).toEpochMilli();

        while (!condition.getAsBoolean() && System.currentTimeMillis() < timeout)
        {
            sleep(100);
        }

        if (!condition.getAsBoolean())
        {
            fail(failureMessage);
        }
    }

    private void sleep(int millies)
    {
        try
        {
            Thread.sleep(millies);
        }
        catch (InterruptedException e)
        {
            // ignore
        }
    }

    public String getDefaultTopic()
    {
        return clientRule.getDefaultTopic();
    }

    public int getDefaultPartition()
    {
        return clientRule.getDefaultPartition();
    }

}
