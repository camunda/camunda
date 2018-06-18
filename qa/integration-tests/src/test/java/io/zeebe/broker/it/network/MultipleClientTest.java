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
package io.zeebe.broker.it.network;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.client.api.events.JobEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class MultipleClientTest {
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientRule client1 = new ClientRule();
  public ClientRule client2 = new ClientRule();

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(client1).around(client2);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldOpenTopicSubscriptions() {
    // given
    final List<JobEvent> jobEventsClient1 = new CopyOnWriteArrayList<>();
    final List<JobEvent> jobEventsClient2 = new CopyOnWriteArrayList<>();

    client1.waitUntilTopicsExists(client1.getDefaultTopic());

    client1
        .getTopicClient()
        .newSubscription()
        .name("client-1")
        .jobEventHandler(e -> jobEventsClient1.add(e))
        .open();

    client2
        .getTopicClient()
        .newSubscription()
        .name("client-2")
        .jobEventHandler(e -> jobEventsClient2.add(e))
        .open();

    // when
    client1.getJobClient().newCreateCommand().jobType("foo").send();
    client2.getJobClient().newCreateCommand().jobType("bar").send();

    // then
    waitUntil(() -> jobEventsClient1.size() + jobEventsClient2.size() >= 4);

    assertThat(jobEventsClient1).hasSize(2);
    assertThat(jobEventsClient2).hasSize(2);
  }

  @Test
  public void shouldOpenTaskSubscriptionsForDifferentTypes() {
    // given
    final RecordingJobHandler handler1 = new RecordingJobHandler();
    final RecordingJobHandler handler2 = new RecordingJobHandler();

    client1.waitUntilTopicsExists(client1.getDefaultTopic());

    client1.getJobClient().newWorker().jobType("foo").handler(handler1).open();

    client2.getJobClient().newWorker().jobType("bar").handler(handler2).open();

    // when
    final JobEvent job1 = client1.getJobClient().newCreateCommand().jobType("foo").send().join();
    final JobEvent job2 = client1.getJobClient().newCreateCommand().jobType("bar").send().join();

    // then
    waitUntil(() -> handler1.getHandledJobs().size() + handler2.getHandledJobs().size() >= 2);

    assertThat(handler1.getHandledJobs()).hasSize(1);
    assertThat(handler1.getHandledJobs().get(0).getMetadata().getKey())
        .isEqualTo(job1.getMetadata().getKey());

    assertThat(handler2.getHandledJobs()).hasSize(1);
    assertThat(handler2.getHandledJobs().get(0).getMetadata().getKey())
        .isEqualTo(job2.getMetadata().getKey());
  }
}
