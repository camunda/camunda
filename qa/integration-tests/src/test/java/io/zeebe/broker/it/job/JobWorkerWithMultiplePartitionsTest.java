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
package io.zeebe.broker.it.job;

import static io.zeebe.protocol.Protocol.DEFAULT_TOPIC;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.clients.JobClient;
import io.zeebe.gateway.api.commands.Topic;
import io.zeebe.gateway.api.commands.Topics;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.impl.job.CreateJobCommandImpl;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class JobWorkerWithMultiplePartitionsTest {

  public EmbeddedBrokerRule brokerRule =
      new EmbeddedBrokerRule("zeebe.unit-test.increased.partitions.cfg.toml");

  public ClientRule clientRule = new ClientRule(brokerRule);

  public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule, false);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(brokerRule).around(clientRule).around(eventRecorder);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Rule public Timeout timeout = Timeout.seconds(20);

  private JobClient jobClient;

  @Before
  public void setUp() {
    jobClient = clientRule.getClient().topicClient().jobClient();

    final String defaultTopic = clientRule.getClient().getConfiguration().getDefaultTopic();
    clientRule.waitUntilTopicsExists(defaultTopic);
  }

  @Test
  public void shouldReceiveJobsFromMultiplePartitions() {
    // given
    final String topicName = DEFAULT_TOPIC;

    final ZeebeClient client = clientRule.getClient();
    clientRule.waitUntilTopicsExists(topicName);

    final Topics topics = client.newTopicsRequest().send().join();
    final Topic topic =
        topics.getTopics().stream().filter(t -> t.getName().equals(topicName)).findFirst().get();

    final Integer[] partitionIds =
        topic.getPartitions().stream().mapToInt(p -> p.getId()).boxed().toArray(Integer[]::new);

    final String jobType = "foooo";

    final RecordingJobHandler handler = new RecordingJobHandler();

    createJobOfTypeOnPartition(jobType, partitionIds[0]);
    createJobOfTypeOnPartition(jobType, partitionIds[1]);
    createJobOfTypeOnPartition(jobType, partitionIds[2]);

    // when
    clientRule
        .getClient()
        .topicClient()
        .jobClient()
        .newWorker()
        .jobType(jobType)
        .handler(handler)
        .timeout(Duration.ofMinutes(5))
        .name("test")
        .open();

    // then
    waitUntil(() -> handler.getHandledJobs().size() == 3);

    final Integer[] receivedPartitionIds =
        handler
            .getHandledJobs()
            .stream()
            .map(t -> t.getMetadata().getPartitionId())
            .toArray(Integer[]::new);

    assertThat(receivedPartitionIds).containsExactlyInAnyOrder(partitionIds);
  }

  private JobEvent createJobOfTypeOnPartition(String type, int partition) {
    final CreateJobCommandImpl createCommand =
        (CreateJobCommandImpl) jobClient.newCreateCommand().jobType(type);

    createCommand.getCommand().setPartitionId(partition);

    return createCommand.send().join();
  }
}
