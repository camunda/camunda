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


import static io.zeebe.test.util.TestUtil.doRepeatedly;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.*;
import io.zeebe.client.api.commands.*;
import io.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.sched.clock.ControlledActorClock;
import org.junit.rules.ExternalResource;

public class ClientRule extends ExternalResource
{
    public static final String DEFAULT_TOPIC = "default-topic";
    protected int defaultPartition;

    protected final Properties properties;
    protected final boolean createDefaultTopic;

    protected ZeebeClient client;
    private ControlledActorClock actorClock = new ControlledActorClock();

    public ClientRule()
    {
        this(true);
    }

    public ClientRule(boolean createDefaultTopic)
    {
        this(() -> new Properties(), createDefaultTopic);
    }

    public ClientRule(Supplier<Properties> propertiesProvider, boolean createDefaultTopic)
    {
        this.properties = propertiesProvider.get();
        this.createDefaultTopic = createDefaultTopic;
    }

    @Override
    protected void before()
    {
        client = ((ZeebeClientBuilderImpl) ZeebeClient.newClient(properties)).setActorClock(actorClock).create();

        if (createDefaultTopic)
        {
            createDefaultTopic();
        }
    }

    private void createDefaultTopic()
    {
        client.newCreateTopicCommand()
            .name(DEFAULT_TOPIC)
            .partitions(1)
            .replicationFactor(1)
            .send()
            .join();

        waitUntilTopicsExists(DEFAULT_TOPIC);

        final Topology topology = client.newTopologyRequest().send().join();

        defaultPartition = -1;
        final List<BrokerInfo> topologyBrokers = topology.getBrokers();

        for (BrokerInfo leader : topologyBrokers)
        {
            final List<PartitionInfo> partitions = leader.getPartitions();
            for (PartitionInfo brokerPartitionState : partitions)
            {
                if (DEFAULT_TOPIC.equals(brokerPartitionState.getTopicName())
                    && brokerPartitionState.isLeader())
                {
                    defaultPartition = brokerPartitionState.getPartitionId();
                    break;
                }
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

    public void waitUntilTopicsExists(final String... topicNames)
    {
        final List<String> expectedTopicNames = Arrays.asList(topicNames);

        doRepeatedly(this::topicsByName)
            .until(t -> t.keySet().containsAll(expectedTopicNames));
    }

    public Map<String, List<Partition>> topicsByName()
    {
        final Topics topics = client.newTopicsRequest().send().join();
        return topics.getTopics()
                .stream()
                .collect(Collectors.toMap(Topic::getName, Topic::getPartitions));
    }

    public String getDefaultTopic()
    {
        return DEFAULT_TOPIC;
    }

    public int getDefaultPartition()
    {
        return defaultPartition;
    }

    public ControlledActorClock getActorClock()
    {
        return actorClock;
    }

    public WorkflowClient getWorkflowClient()
    {
        return getClient().topicClient().workflowClient();
    }

    public JobClient getJobClient()
    {
        return getClient().topicClient().jobClient();
    }

    public SubscriptionClient getSubscriptionClient()
    {
        return getClient().topicClient().subscriptionClient();
    }
}
