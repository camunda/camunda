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

import static io.zeebe.protocol.Protocol.DEFAULT_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.clustering.ClusteringRule;
import io.zeebe.gateway.api.commands.Partition;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class BootstrapDefaultTopicsTest {
  private static final String BOOTSTRAPPED_BROKER_CONFIG = "zeebe.cluster.defaultTopics.1.cfg.toml";
  private static final String OTHER_BROKER_CONFIG = "zeebe.cluster.defaultTopics.2.cfg.toml";
  private final String[] clusterConfigs = {
    BOOTSTRAPPED_BROKER_CONFIG, OTHER_BROKER_CONFIG, ClusteringRule.BROKER_3_TOML
  };

  private Timeout testTimeout = Timeout.seconds(30);
  private ClusteringRule clusteringRule = new ClusteringRule(clusterConfigs);
  private ClientRule clientRule = new ClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldCreateDefaultTopicsOnBootstrappedBrokers() {
    // given
    clusteringRule.waitForTopicPartitionReplicationFactor(DEFAULT_TOPIC, 2, 2);

    // when
    final Map<String, List<Partition>> topics = clientRule.topicsByName();

    // then
    assertThat(topics.containsKey(DEFAULT_TOPIC)).isTrue();
    assertThat(topics.get(DEFAULT_TOPIC).size()).isEqualTo(2);
    assertThat(topics.containsKey("default-topic-2")).isFalse();
    assertThat(topics.size()).isEqualTo(2); // default + internal
  }
}
