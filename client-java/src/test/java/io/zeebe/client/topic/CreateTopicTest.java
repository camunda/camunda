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
package io.zeebe.client.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.events.TopicEvent;
import io.zeebe.client.api.events.TopicState;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.TopicIntent;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CreateTopicTest {

  public ClientRule clientRule = new ClientRule();
  public StubBrokerRule brokerRule = new StubBrokerRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  protected ZeebeClient client;

  @Test
  public void shouldCreateTopic() {
    // given
    final Instant expectedTimestamp = Instant.now();
    brokerRule
        .onExecuteCommandRequest(Protocol.SYSTEM_PARTITION, ValueType.TOPIC, TopicIntent.CREATE)
        .respondWith()
        .event()
        .key(123)
        .position(456)
        .timestamp(expectedTimestamp)
        .intent(TopicIntent.CREATING)
        .value()
        .allOf(ExecuteCommandRequest::getCommand)
        .done()
        .register();

    // when
    final TopicEvent responseEvent =
        clientRule
            .getClient()
            .newCreateTopicCommand()
            .name("newTopic")
            .partitions(14)
            .replicationFactor(3)
            .send()
            .join();

    // then
    final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
    assertThat(request.valueType()).isEqualTo(ValueType.TOPIC);
    assertThat(request.partitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
    assertThat(request.intent()).isEqualTo(TopicIntent.CREATE);
    assertThat(request.position()).isEqualTo(ExecuteCommandRequestEncoder.positionNullValue());

    assertThat(request.getCommand())
        .containsOnly(
            entry("name", "newTopic"), entry("partitions", 14), entry("replicationFactor", 3));

    assertThat(responseEvent.getMetadata().getKey()).isEqualTo(123L);
    assertThat(responseEvent.getMetadata().getTopicName()).isEqualTo(Protocol.SYSTEM_TOPIC);
    assertThat(responseEvent.getMetadata().getPartitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
    assertThat(responseEvent.getMetadata().getPosition()).isEqualTo(456);
    assertThat(responseEvent.getMetadata().getTimestamp()).isEqualTo(expectedTimestamp);
    assertThat(responseEvent.getState()).isEqualTo(TopicState.CREATING);
  }

  @Test
  public void shouldValidateTopicNameNotNull() {
    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("name must not be null");

    // when
    clientRule
        .getClient()
        .newCreateTopicCommand()
        .name(null)
        .partitions(14)
        .replicationFactor(3)
        .send()
        .join();
  }

  @Test
  public void shouldValidatePartitionsGreaterThanZero() {
    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("partitions must be greater than 0");

    // when
    clientRule
        .getClient()
        .newCreateTopicCommand()
        .name("foo")
        .partitions(-1)
        .replicationFactor(3)
        .send()
        .join();
  }

  @Test
  public void shouldValidateReplicationFactorGreaterThanZero() {
    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("replicationFactor must be greater than 0");

    // when
    clientRule
        .getClient()
        .newCreateTopicCommand()
        .name("foo")
        .partitions(14)
        .replicationFactor(-1)
        .send()
        .join();
  }
}
