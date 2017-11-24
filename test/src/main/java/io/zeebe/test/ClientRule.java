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

import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.clustering.impl.TopicLeader;
import io.zeebe.client.clustering.impl.TopologyResponse;
import org.junit.rules.ExternalResource;


public class ClientRule extends ExternalResource
{
    public static final String DEFAULT_TOPIC = "default-topic";
    protected int defaultPartition;

    protected ZeebeClient client;
    protected final boolean createDefaultTopic;

    protected final Properties properties;

    public ClientRule(final boolean createDefaultTopic)
    {
        this(() -> new Properties(), createDefaultTopic);
    }

    public ClientRule(Supplier<Properties> propertiesProvider, boolean createDefaultTopic)
    {
        this.properties = propertiesProvider.get();
        this.createDefaultTopic = createDefaultTopic;
    }

    public ZeebeClient getClient()
    {
        return client;
    }

    @Override
    protected void before() throws Throwable
    {
        client = ZeebeClient.create(properties);

        if (createDefaultTopic)
        {
            createDefaultTopic();
        }
    }

    protected void createDefaultTopic()
    {
        client.topics().create(DEFAULT_TOPIC, 1).execute();
        final TopologyResponse topology = client.requestTopology().execute();

        defaultPartition = -1;
        final List<TopicLeader> leaders = topology.getTopicLeaders();

        for (TopicLeader leader : leaders)
        {
            if (DEFAULT_TOPIC.equals(leader.getTopicName()))
            {
                defaultPartition = leader.getPartitionId();
            }
        }

        if (defaultPartition < 0)
        {
            throw new RuntimeException("Could not detect leader for default partition");
        }
    }

    @Override
    protected void after()
    {
        client.close();
        client = null;
    }

    public String getDefaultTopic()
    {
        return DEFAULT_TOPIC;
    }

    public int getDefaultPartition()
    {
        return defaultPartition;
    }

}
