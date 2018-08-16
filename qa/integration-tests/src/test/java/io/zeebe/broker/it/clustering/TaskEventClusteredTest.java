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

import static io.zeebe.protocol.Protocol.DEFAULT_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.commands.BrokerInfo;
import io.zeebe.gateway.api.commands.Topic;
import io.zeebe.gateway.api.commands.Topics;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.events.JobState;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TaskEventClusteredTest {
  public ClusteringRule clusteringRule = new ClusteringRule();
  public ClientRule clientRule = new ClientRule(clusteringRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(clusteringRule).around(clientRule);

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/844")
  public void shouldCreateJobWhenFollowerUnavailable() {
    // given
    final ZeebeClient client = clientRule.getClient();

    clusteringRule.waitForTopic(1);

    final Topics topics = client.newTopicsRequest().send().join();
    final Topic topic =
        topics
            .getTopics()
            .stream()
            .filter(t -> DEFAULT_TOPIC.equals(t.getName()))
            .findFirst()
            .get();

    final BrokerInfo leader =
        clusteringRule.getLeaderForPartition(topic.getPartitions().get(0).getId());

    // choosing a new leader in a raft group where the previously leading broker is no longer
    // available
    clusteringRule.stopBroker(leader.getAddress());

    // when
    final JobEvent jobEvent =
        client.topicClient().jobClient().newCreateCommand().jobType("bar").send().join();

    // then
    assertThat(jobEvent.getState()).isEqualTo(JobState.CREATED);
  }
}
