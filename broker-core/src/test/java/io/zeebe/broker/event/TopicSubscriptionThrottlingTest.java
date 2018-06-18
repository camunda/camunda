/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.*;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.util.TestUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TopicSubscriptionThrottlingTest {
  protected static final String SUBSCRIPTION_NAME = "foo";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  public void openSubscription(int bufferSize) {
    apiRule
        .createCmdRequest()
        .type(ValueType.SUBSCRIBER, SubscriberIntent.SUBSCRIBE)
        .command()
        .put("startPosition", 0)
        .put("name", "foo")
        .put("bufferSize", bufferSize)
        .done()
        .sendAndAwait();
  }

  @Test
  public void shouldNotPushMoreThanBufferSize() throws InterruptedException {
    // given
    final int nrOfJobs = 5;
    final int bufferSize = 3;

    createJobs(nrOfJobs);

    // when
    openSubscription(bufferSize);

    // then
    TestUtil.waitUntil(() -> apiRule.numSubscribedEventsAvailable() >= 3);
    Thread.sleep(1000L); // there might be more received in case this feature is broken
    assertThat(apiRule.numSubscribedEventsAvailable()).isEqualTo(bufferSize);
  }

  @Test
  public void shouldPushMoreAfterAck() throws InterruptedException {
    // given
    final int nrOfJobs = 5;
    final int bufferSize = 3;

    createJobs(nrOfJobs);
    openSubscription(bufferSize);
    TestUtil.waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 3);

    final List<Long> eventPositions =
        apiRule.subscribedEvents().limit(3).map((e) -> e.position()).collect(Collectors.toList());

    apiRule.moveMessageStreamToTail();

    // when
    apiRule
        .createCmdRequest()
        .type(ValueType.SUBSCRIPTION, SubscriptionIntent.ACKNOWLEDGE)
        .command()
        .put("name", SUBSCRIPTION_NAME)
        .put("ackPosition", eventPositions.get(1))
        .done()
        .sendAndAwait();

    // then
    Thread.sleep(1000L); // there might be more received in case this feature is broken
    assertThat(apiRule.numSubscribedEventsAvailable()).isEqualTo(2);

    final List<Long> eventPositionsAfterAck =
        apiRule.subscribedEvents().limit(2).map((e) -> e.position()).collect(Collectors.toList());

    assertThat(eventPositionsAfterAck.get(0)).isGreaterThan(eventPositions.get(2));
    assertThat(eventPositionsAfterAck.get(1)).isGreaterThan(eventPositions.get(2));
  }

  protected void createJobs(int nrOfJobs) {
    for (int i = 0; i < nrOfJobs; i++) {
      apiRule
          .createCmdRequest()
          .type(ValueType.JOB, JobIntent.CREATE)
          .command()
          .put("type", "theJobType")
          .done()
          .sendAndAwait();
    }
  }
}
