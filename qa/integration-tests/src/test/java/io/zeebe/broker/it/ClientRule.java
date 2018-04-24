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

import io.zeebe.client.TasksClient;
import io.zeebe.client.TopicsClient;
import io.zeebe.client.WorkflowsClient;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.clustering.impl.BrokerPartitionState;
import io.zeebe.client.clustering.impl.TopologyBroker;
import io.zeebe.client.clustering.impl.TopologyResponse;
import io.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.topic.Partition;
import io.zeebe.client.topic.Topic;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.clustering.*;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.sched.clock.ControlledActorClock;
import org.junit.rules.ExternalResource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.zeebe.test.util.TestUtil.doRepeatedly;

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
        client.topics().create(DEFAULT_TOPIC, 1).execute();
        waitUntilTopicsExists(DEFAULT_TOPIC);

        final TopologyImpl topology = client.requestTopology().execute();

        defaultPartition = -1;
        final List<BrokerInfoImpl> topologyBrokers = topology.getBrokers();

        for (BrokerInfoImpl leader : topologyBrokers)
        {
            final List<PartitionInfoImpl> partitions = leader.getPartitions();
            for (PartitionInfoImpl brokerPartitionState : partitions)
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
        return topics().getTopics().execute()
                       .getTopics()
                       .stream()
                       .collect(Collectors.toMap(Topic::getName, Topic::getPartitions));
    }

    public TopicsClient topics()
    {
        return client.topics();
    }

    public TasksClient tasks()
    {
        return client.tasks();
    }

    public WorkflowsClient workflows()
    {
        return client.workflows();
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
}
