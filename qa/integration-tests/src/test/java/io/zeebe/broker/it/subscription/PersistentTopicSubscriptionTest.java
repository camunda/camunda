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
package io.zeebe.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.api.clients.TopicClient;
import io.zeebe.client.api.subscription.TopicSubscription;
import io.zeebe.test.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class PersistentTopicSubscriptionTest {
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientRule clientRule = new ClientRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  protected TopicClient client;
  protected RecordingEventHandler recordingHandler;

  @Before
  public void setUp() {
    this.client = clientRule.getClient().topicClient();
    this.recordingHandler = new RecordingEventHandler();
  }

  @Test
  public void shouldResumeSubscriptionOnRestart() {
    // given a first job
    client
        .jobClient()
        .newCreateCommand()
        .jobType("foo")
        .addCustomHeader("key", "value")
        .payload("{}")
        .send()
        .join();

    final String subscriptionName = "foo";

    final TopicSubscription subscription =
        client
            .newSubscription()
            .name(subscriptionName)
            .recordHandler(recordingHandler)
            .startAtHeadOfTopic()
            .open();

    // that was received by the subscription
    TestUtil.waitUntil(() -> recordingHandler.numJobRecords() == 2);

    subscription.close();

    final long lastEventPosition =
        recordingHandler
            .getRecords()
            .get(recordingHandler.numRecords() - 1)
            .getMetadata()
            .getPosition();

    recordingHandler.reset();

    // and a second not-yet-received job
    client
        .jobClient()
        .newCreateCommand()
        .jobType("foo")
        .addCustomHeader("key", "value")
        .payload("{}")
        .send()
        .join();

    // when
    restartBroker();

    client
        .newSubscription()
        .name(subscriptionName)
        .recordHandler(recordingHandler)
        .startAtHeadOfTopic()
        .open();

    // then
    TestUtil.waitUntil(() -> recordingHandler.numRecords() > 0);

    final long firstEventPositionAfterReopen =
        recordingHandler.getRecords().get(0).getMetadata().getPosition();

    assertThat(firstEventPositionAfterReopen).isGreaterThan(lastEventPosition);
  }

  protected void restartBroker() {
    brokerRule.restartBroker();
  }
}
