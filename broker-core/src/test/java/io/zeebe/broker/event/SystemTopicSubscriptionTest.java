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
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ControlMessageResponse;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class SystemTopicSubscriptionTest {

  public static final int DEFAULT_PARTITION = Protocol.DEPLOYMENT_PARTITION;
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Before
  public void init() {
    brokerRule.getClock().pinCurrentTime();
  }

  @Test
  public void shouldOpenSubscription() {
    // when
    final ExecuteCommandResponse subscriptionResponse =
        apiRule.openTopicSubscription(Protocol.DEPLOYMENT_PARTITION, "foo", 0).await();

    // then
    assertThat(subscriptionResponse.key()).isGreaterThanOrEqualTo(0);
  }

  @Test
  public void shouldCloseSubscription() {
    // given
    final ExecuteCommandResponse addResponse =
        apiRule.openTopicSubscription(Protocol.DEPLOYMENT_PARTITION, "foo", 0).await();

    final long subscriberKey = addResponse.key();

    // when
    final ControlMessageResponse removeResponse =
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .partitionId(Protocol.DEPLOYMENT_PARTITION)
            .data()
            .put("subscriberKey", subscriberKey)
            .done()
            .sendAndAwait();

    // then
    assertThat(removeResponse.getData()).containsOnly(entry("subscriberKey", subscriberKey));
  }

  @Test
  public void shouldPushDeploymentEvents() {
    // given
    final long deploymentKey =
        apiRule.partition().deploy(Bpmn.createExecutableProcess("wf").startEvent().done());

    // when
    final ExecuteCommandResponse addResponse =
        apiRule.openTopicSubscription(DEFAULT_PARTITION, "foo", 0).await();

    final long subscriberKey = addResponse.key();

    // then
    final List<SubscribedRecord> deploymentEvents =
        apiRule
            .subscribedEvents()
            .filter((e) -> e.valueType() == ValueType.DEPLOYMENT)
            .limit(4)
            .collect(Collectors.toList());

    assertThat(deploymentEvents).hasSize(4);

    assertThat(deploymentEvents)
        .extracting(SubscribedRecord::subscriberKey)
        .containsOnly(subscriberKey);
    assertThat(deploymentEvents)
        .extracting(SubscribedRecord::subscriptionType)
        .containsOnly(SubscriptionType.TOPIC_SUBSCRIPTION);
    assertThat(deploymentEvents)
        .extracting(SubscribedRecord::key)
        .containsOnly(ExecuteCommandResponseDecoder.keyNullValue(), deploymentKey);
    assertThat(deploymentEvents)
        .extracting(SubscribedRecord::partitionId)
        .containsOnly(DEFAULT_PARTITION);
    assertThat(deploymentEvents)
        .extracting(SubscribedRecord::timestamp)
        .containsOnly(brokerRule.getClock().getCurrentTimeInMillis());

    assertThat(deploymentEvents)
        .extracting(SubscribedRecord::valueType)
        .containsOnly(ValueType.DEPLOYMENT);
    assertThat(deploymentEvents)
        .extracting(SubscribedRecord::sourceRecordPosition)
        .containsExactly(
            -1L,
            deploymentEvents.get(0).position(),
            deploymentEvents.get(1).position(),
            deploymentEvents.get(2).position());
    assertThat(deploymentEvents)
        .extracting(SubscribedRecord::recordType)
        .containsExactly(
            RecordType.COMMAND, RecordType.EVENT, RecordType.COMMAND, RecordType.EVENT);
    assertThat(deploymentEvents)
        .extracting(SubscribedRecord::intent)
        .containsExactly(
            DeploymentIntent.CREATE,
            DeploymentIntent.CREATED,
            DeploymentIntent.DISTRIBUTE,
            DeploymentIntent.DISTRIBUTED);
  }
}
