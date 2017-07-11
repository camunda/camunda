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
package io.zeebe.broker.it;

import static io.zeebe.logstreams.log.LogStream.DEFAULT_PARTITION_ID;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_TOPIC_NAME;

import java.util.Properties;
import java.util.function.Supplier;

import org.junit.rules.ExternalResource;

import io.zeebe.client.TaskTopicClient;
import io.zeebe.client.TopicClient;
import io.zeebe.client.WorkflowTopicClient;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.transport.ClientTransport;

public class ClientRule extends ExternalResource
{

    protected final Properties properties;

    protected ZeebeClient client;

    public ClientRule()
    {
        this(() -> new Properties());
    }

    public ClientRule(Supplier<Properties> propertiesProvider)
    {
        this.properties = propertiesProvider.get();

    }

    @Override
    protected void before() throws Throwable
    {
        client = ZeebeClient.create(properties);
        client.connect();
    }

    @Override
    protected void after()
    {
        client.close();
    }

    public ZeebeClient getClient()
    {
        return client;
    }

    public void interruptBrokerConnections()
    {
        final ClientTransport transport = ((ZeebeClientImpl) client).getTransport();
        transport.interruptAllChannels();
    }

    public TopicClient topic()
    {
        return client.topic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);
    }

    public TaskTopicClient taskTopic()
    {
        return client.taskTopic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);
    }

    public WorkflowTopicClient workflowTopic()
    {
        return client.workflowTopic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);
    }

}
