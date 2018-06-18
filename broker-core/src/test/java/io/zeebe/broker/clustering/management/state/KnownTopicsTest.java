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
package io.zeebe.broker.clustering.management.state;

import static io.zeebe.broker.clustering.orchestration.ClusterOrchestrationLayerServiceNames.KNOWN_TOPICS_SERVICE_NAME;

import io.zeebe.broker.clustering.orchestration.state.KnownTopics;
import io.zeebe.broker.clustering.orchestration.state.TopicInfo;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.test.util.TestUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;

public class KnownTopicsTest {

  @Rule public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  private KnownTopics knownTopics;

  private KnownTopics getKnownTopics() throws Exception {
    final Injector<KnownTopics> injector = new Injector<>();
    brokerRule
        .getBroker()
        .getBrokerContext()
        .getServiceContainer()
        .createService(
            ServiceName.newServiceName("test-cluster-topic-state", Void.class), () -> null)
        .dependency(KNOWN_TOPICS_SERVICE_NAME, injector)
        .install()
        .get(1, TimeUnit.SECONDS);

    return injector.getValue();
  }

  @Test
  public void shouldContainSystemTopic() throws Exception {
    // then
    assertSystemTopicExists();
  }

  @Test
  public void shouldContainSystemTopicAfterRestart() throws Exception {
    // given
    assertSystemTopicExists();

    // when
    brokerRule.restartBroker();

    // then
    assertSystemTopicExists();
  }

  private void assertSystemTopicExists() throws Exception {
    knownTopics = getKnownTopics();
    TestUtil.doRepeatedly(this::getTopicNames)
        .until(
            topicNames -> {
              System.out.println(topicNames);
              return topicNames.contains(Protocol.SYSTEM_TOPIC);
            });
  }

  private List<String> getTopicNames() {
    return knownTopics.queryTopics(this::collectTopicNames).join();
  }

  private List<String> collectTopicNames(final Iterable<TopicInfo> topicInfos) {
    final List<String> topicNames = new ArrayList<>();
    topicInfos.forEach(info -> topicNames.add(info.getTopicName()));
    return topicNames;
  }
}
