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

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.zeebe.broker.Broker;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.clustering.impl.TopicLeader;
import io.zeebe.client.clustering.impl.TopologyResponse;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;
import org.junit.rules.ExternalResource;

public class ClusteringRule extends ExternalResource
{
    public static final String BROKER_1_TOML = "zeebe.cluster.1.cfg.toml";
    public static final SocketAddress BROKER_1_CLIENT_ADDRESS = new SocketAddress("localhost", 51015);

    public static final String BROKER_2_TOML = "zeebe.cluster.2.cfg.toml";
    public static final SocketAddress BROKER_2_CLIENT_ADDRESS = new SocketAddress("localhost", 41015);

    public static final String BROKER_3_TOML = "zeebe.cluster.3.cfg.toml";
    public static final SocketAddress BROKER_3_CLIENT_ADDRESS = new SocketAddress("localhost", 31015);

    private SocketAddress[] brokerAddresses = new SocketAddress[]{BROKER_1_CLIENT_ADDRESS, BROKER_2_CLIENT_ADDRESS, BROKER_3_CLIENT_ADDRESS};
    private String[] brokerConfigs = new String[]{BROKER_1_TOML, BROKER_2_TOML, BROKER_3_TOML};

    private final AutoCloseableRule autoCloseableRule;
    private final ClientRule clientRule;

    private ZeebeClient zeebeClient;
    protected final Map<SocketAddress, Broker> brokers = new HashMap<>();

    public ClusteringRule(AutoCloseableRule autoCloseableRule, ClientRule clientRule)
    {
        this.autoCloseableRule = autoCloseableRule;
        this.clientRule = clientRule;
    }

    @Override
    protected void before()
    {
        zeebeClient = clientRule.getClient();

        brokers.put(BROKER_1_CLIENT_ADDRESS, startBroker(BROKER_1_TOML));
        brokers.put(BROKER_2_CLIENT_ADDRESS, startBroker(BROKER_2_TOML));
        brokers.put(BROKER_3_CLIENT_ADDRESS,  startBroker(BROKER_3_TOML));

        waitForExactBrokerCount(3);
    }

    protected Broker startBroker(String configFile)
    {
        final InputStream config = this.getClass().getClassLoader().getResourceAsStream(configFile);
        final Broker broker = new Broker(config);
        autoCloseableRule.manage(broker);
        return broker;
    }

    protected void startBroker(int index)
    {
        startBroker(brokerConfigs[index]);
    }

    protected void stopBroker(SocketAddress socketAddress)
    {
        brokers.remove(socketAddress).close();
    }

    protected void stopBroker(int index)
    {
        stopBroker(brokerAddresses[index]);
    }

    public List<SocketAddress> waitForExactBrokerCount(int brokerCount)
    {
        return doRepeatedly(() -> {
            final TopologyResponse execute = zeebeClient.requestTopology()
                                                        .execute();
            Loggers.CLUSTERING_LOGGER.info("Topology response {}", execute);
            return execute.getBrokers();
        }).until(topologyBroker -> topologyBroker != null && topologyBroker.size() == brokerCount);
    }

    public List<TopicLeader> waitForGreaterOrEqualLeaderCount(int leaderCount)
    {
        return doRepeatedly(() -> zeebeClient.requestTopology().execute().getTopicLeaders())
            .until(leaders -> leaders != null && leaders.size() >= leaderCount);

    }

    public TopicLeader filterLeadersByPartition(List<TopicLeader> topicLeaders, int partition)
    {
        return topicLeaders
            .stream()
            .filter((leader) -> leader.getPartitionId() == partition)
            .findAny().get();
    }

    public Set<SocketAddress> getBrokerAddresses()
    {
        return brokers.keySet();
    }

    public void assertThatBrokersListContains(List<SocketAddress> addressList)
    {
        assertThat(addressList).contains(brokers.keySet().toArray(new SocketAddress[3]));
    }
}
