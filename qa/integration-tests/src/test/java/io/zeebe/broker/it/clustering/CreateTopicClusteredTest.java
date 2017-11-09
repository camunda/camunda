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

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;

public class CreateTopicClusteredTest
{
    private static final String BROKER1_CONFIG = "zeebe.cluster.1.cfg.toml";
    private static final String BROKER2_CONFIG = "zeebe.cluster.2.cfg.toml";
    private static final String BROKER3_CONFIG = "zeebe.cluster.3.cfg.toml";

    private static final SocketAddress BROKER1_ADDRESS = new SocketAddress("localhost", 51015);
    private static final SocketAddress BROKER2_ADDRESS = new SocketAddress("localhost", 41015);
    private static final SocketAddress BROKER3_ADDRESS = new SocketAddress("localhost", 31015);

    @Rule
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Rule
    public ClientRule clientRule = new ClientRule(false);

    @Rule
    public Timeout timeout = new Timeout(30, TimeUnit.SECONDS);

    @Test
    public void shouldCreateTopic()
    {
        // given
        startBroker(BROKER1_CONFIG);
        startBroker(BROKER2_CONFIG);
        startBroker(BROKER3_CONFIG);

        final ZeebeClient client = clientRule.getClient();

        // when
        client.topics().create("foo", 2).execute();

        // then
        final TaskEvent taskEvent = client.tasks().create("foo", "bar").execute();

        assertThat(taskEvent).isNotNull();
        assertThat(taskEvent.getState()).isEqualTo("CREATED");
    }

    @Test
    public void shouldReplicateNewTopic() throws InterruptedException
    {
        // given
        final Map<SocketAddress, Broker> brokers = new HashMap<>();

        final Broker broker1 = startBroker(BROKER1_CONFIG);
        final Broker broker2 = startBroker(BROKER2_CONFIG);
        final Broker broker3 = startBroker(BROKER3_CONFIG);
        brokers.put(BROKER1_ADDRESS, broker1);
        brokers.put(BROKER2_ADDRESS, broker2);
        brokers.put(BROKER3_ADDRESS, broker3);

        final ZeebeClient client = clientRule.getClient();
        final TopologyObserver observer = new TopologyObserver(client);

        // wait till all members are known before we create the partition => workaround for https://github.com/zeebe-io/zeebe/issues/534
        observer.waitForBroker(BROKER1_ADDRESS);
        observer.waitForBroker(BROKER2_ADDRESS);
        observer.waitForBroker(BROKER3_ADDRESS);

        client.topics().create("foo", 1).execute();
        final TaskEvent taskEvent = client.tasks().create("foo", "bar").execute();
        final int partitionId = taskEvent.getMetadata().getPartitionId();


        final SocketAddress currentLeaderAddress = observer.waitForLeader(partitionId);
        final Broker currentLeader = brokers.get(currentLeaderAddress);

        final Set<SocketAddress> expectedFollowers = new HashSet<>(brokers.keySet());
        expectedFollowers.remove(currentLeaderAddress);

        // when
        currentLeader.close();

        // then
        final SocketAddress newLeader = observer.waitForLeader(partitionId, expectedFollowers);
        assertThat(expectedFollowers).contains(newLeader);
    }

    protected Broker startBroker(String configFile)
    {
        final InputStream config = this.getClass().getClassLoader().getResourceAsStream(configFile);
        final Broker broker = new Broker(config);
        closeables.manage(broker);
        return broker;
    }
}
