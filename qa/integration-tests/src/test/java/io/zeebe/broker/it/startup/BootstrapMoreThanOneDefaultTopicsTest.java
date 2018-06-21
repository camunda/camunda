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
package io.zeebe.broker.it.startup;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.clustering.ClusteringRule;
import io.zeebe.client.api.commands.Partition;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class BootstrapMoreThanOneDefaultTopicsTest {
  private static final String BOOTSTRAPPED_BROKER_CONFIG =
      "zeebe.cluster.moreDefaultTopics.cfg.toml";
  private static final String OTHER_BROKER_CONFIG = "zeebe.cluster.defaultTopics.2.cfg.toml";
  private final String[] clusterConfigs = {
    BOOTSTRAPPED_BROKER_CONFIG, OTHER_BROKER_CONFIG, ClusteringRule.BROKER_3_TOML
  };
  private final SocketAddress[] clusterAddresses = {
    ClusteringRule.BROKER_1_CLIENT_ADDRESS,
    ClusteringRule.BROKER_2_CLIENT_ADDRESS,
    ClusteringRule.BROKER_3_CLIENT_ADDRESS
  };

  private AutoCloseableRule closeables = new AutoCloseableRule();
  private Timeout testTimeout = Timeout.seconds(30);
  private ClientRule clientRule = new ClientRule();
  private ClusteringRule clusteringRule =
      new ClusteringRule(closeables, clientRule, clusterAddresses, clusterConfigs);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(closeables).around(testTimeout).around(clientRule).around(clusteringRule);

  @Test
  public void shouldCreateMoreDefaultTopics() {
    // given
    final String theOneAndOnlyTopicName = "theOneAndOnlyTopic";
    final String defaultTopicName = "default-topic-1";
    final String otherTopicName = "other-topic";
    final String thisTopicName = "thisTopic";

    clusteringRule.waitForTopicPartitionReplicationFactor(defaultTopicName, 1, 1);
    clusteringRule.waitForTopicPartitionReplicationFactor(otherTopicName, 2, 2);
    clusteringRule.waitForTopicPartitionReplicationFactor(theOneAndOnlyTopicName, 3, 2);
    clusteringRule.waitForTopicPartitionReplicationFactor(thisTopicName, 4, 1);

    // when
    final Map<String, List<Partition>> topics = clientRule.topicsByName();

    // then
    assertThat(topics.containsKey(defaultTopicName)).isTrue();
    assertThat(topics.get(defaultTopicName).size()).isEqualTo(1);

    assertThat(topics.containsKey(otherTopicName)).isTrue();
    assertThat(topics.get(otherTopicName).size()).isEqualTo(2);

    assertThat(topics.containsKey(theOneAndOnlyTopicName)).isTrue();
    assertThat(topics.get(theOneAndOnlyTopicName).size()).isEqualTo(3);

    assertThat(topics.containsKey(thisTopicName)).isTrue();
    assertThat(topics.get(thisTopicName).size()).isEqualTo(4);

    assertThat(topics.size()).isEqualTo(5); // default + internal
  }
}
