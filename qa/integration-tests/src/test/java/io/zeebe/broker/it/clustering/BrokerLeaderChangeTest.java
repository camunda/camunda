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
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.gateway.api.commands.BrokerInfo;
import io.zeebe.gateway.api.commands.PartitionBrokerRole;
import io.zeebe.gateway.api.commands.PartitionInfo;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.events.JobState;
import io.zeebe.gateway.api.subscription.JobWorker;
import io.zeebe.gateway.api.subscription.TopicSubscription;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class BrokerLeaderChangeTest {
  public static final String NULL_PAYLOAD = null;
  public static final String JOB_TYPE = "testTask";

  public Timeout testTimeout = Timeout.seconds(90);
  public ClusteringRule clusteringRule = new ClusteringRule();
  public ClientRule clientRule = new ClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldBecomeFollowerAfterRestartLeaderChange() {
    // given
    final int partition = 1;
    final int oldLeader = clusteringRule.getLeaderForPartition(partition).getNodeId();

    clusteringRule.stopBroker(oldLeader);

    waitUntil(() -> clusteringRule.getLeaderForPartition(partition).getNodeId() != oldLeader);

    // when
    clusteringRule.restartBroker(oldLeader);

    // then
    final Optional<PartitionInfo> partitionInfo =
        clusteringRule
            .getTopologyFromBroker(oldLeader)
            .stream()
            .filter(b -> b.getNodeId() == oldLeader)
            .flatMap(b -> b.getPartitions().stream().filter(p -> p.getPartitionId() == partition))
            .findFirst();

    assertThat(partitionInfo)
        .hasValueSatisfying(p -> assertThat(p.getRole()).isEqualTo(PartitionBrokerRole.FOLLOWER));
  }

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/844")
  public void shouldChangeLeaderAfterLeaderDies() {
    // given
    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(1);
    final String leaderAddress = leaderForPartition.getAddress();

    final JobEvent jobEvent =
        clientRule.getJobClient().newCreateCommand().jobType(JOB_TYPE).send().join();

    // when
    clusteringRule.stopBroker(leaderAddress);
    final JobCompleter jobCompleter = new JobCompleter(jobEvent);

    // then
    jobCompleter.waitForJobCompletion();

    jobCompleter.close();
  }

  class JobCompleter {

    private final AtomicBoolean isJobCompleted = new AtomicBoolean(false);
    private final JobWorker jobSubscription;
    private final TopicSubscription topicSubscription;

    JobCompleter(final JobEvent jobEvent) {
      final long eventKey = jobEvent.getMetadata().getKey();

      jobSubscription =
          doRepeatedly(
                  () ->
                      clientRule
                          .getJobClient()
                          .newWorker()
                          .jobType(JOB_TYPE)
                          .handler(
                              (client, job) -> {
                                if (job.getMetadata().getKey() == eventKey) {
                                  client.newCompleteCommand(job).payload(NULL_PAYLOAD).send();
                                }
                              })
                          .open())
              .until(Objects::nonNull, "Failed to open job subscription for job completion");

      topicSubscription =
          doRepeatedly(
                  () ->
                      clientRule
                          .getClient()
                          .newSubscription()
                          .name("jobObserver")
                          .jobEventHandler(
                              e -> {
                                if (JOB_TYPE.equals(e.getType())
                                    && e.getState() == JobState.COMPLETED) {
                                  isJobCompleted.set(true);
                                }
                              })
                          .startAtHead()
                          .forcedStart()
                          .open())
              .until(Objects::nonNull, "Failed to open topic subscription for job completion");
    }

    void waitForJobCompletion() {
      waitUntil(isJobCompleted::get, 100, "Failed to wait for job completion");
    }

    void close() {
      if (!jobSubscription.isClosed()) {
        jobSubscription.close();
      }

      if (!topicSubscription.isClosed()) {
        topicSubscription.close();
      }
    }
  }
}
