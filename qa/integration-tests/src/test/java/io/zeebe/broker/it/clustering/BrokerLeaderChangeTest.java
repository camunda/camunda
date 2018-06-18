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

import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.JobState;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.client.api.subscription.TopicSubscription;
import io.zeebe.test.util.AutoCloseableRule;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class BrokerLeaderChangeTest {
  public static final String JOB_TYPE = "testTask";

  public AutoCloseableRule closeables = new AutoCloseableRule();
  public Timeout testTimeout = Timeout.seconds(90);
  public ClientRule clientRule = new ClientRule();
  public ClusteringRule clusteringRule = new ClusteringRule(closeables, clientRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(closeables).around(testTimeout).around(clientRule).around(clusteringRule);

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/844")
  public void shouldChangeLeaderAfterLeaderDies() {
    // given
    clusteringRule.createTopic(clientRule.getDefaultTopic(), 1, 3);

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

    JobCompleter(JobEvent jobEvent) {
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
                                  client.newCompleteCommand(job).withoutPayload().send();
                                }
                              })
                          .open())
              .until(Objects::nonNull, "Failed to open job subscription for job completion");

      topicSubscription =
          doRepeatedly(
                  () ->
                      clientRule
                          .getTopicClient()
                          .newSubscription()
                          .name("jobObserver")
                          .jobEventHandler(
                              e -> {
                                if (JOB_TYPE.equals(e.getType())
                                    && e.getState() == JobState.COMPLETED) {
                                  isJobCompleted.set(true);
                                }
                              })
                          .startAtHeadOfTopic()
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
