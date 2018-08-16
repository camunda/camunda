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
package io.zeebe.gateway.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.gateway.api.clients.WorkflowClient;
import io.zeebe.gateway.api.events.MessageEvent;
import io.zeebe.gateway.api.events.MessageState;
import io.zeebe.gateway.cmd.ClientCommandRejectedException;
import io.zeebe.gateway.cmd.ClientException;
import io.zeebe.gateway.impl.data.MsgPackConverter;
import io.zeebe.gateway.util.ClientRule;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.SubscriptionUtil;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class PublishMessageTest {
  private static final int FIRST_PARTITION = 1;
  private static final int PARTITION_COUNT = 10;

  public StubBrokerRule brokerRule = new StubBrokerRule(PARTITION_COUNT);
  public ClientRule clientRule = new ClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);
  @Rule public ExpectedException expectedException = ExpectedException.none();

  private final MsgPackConverter msgPackConverter = new MsgPackConverter();
  private WorkflowClient workflowClient;
  private Duration defaultTimeToLive;

  @Before
  public void setUp() {
    brokerRule.workflowInstances().registerPublishMessageCommand();
    workflowClient = clientRule.getClient().topicClient().workflowClient();
    defaultTimeToLive = clientRule.getClient().getConfiguration().getDefaultMessageTimeToLive();
  }

  @Test
  public void shouldPublishMessage() {
    // when
    final MessageEvent messageEvent =
        workflowClient
            .newPublishMessageCommand()
            .messageName("order canceled")
            .correlationKey("order-123")
            .send()
            .join();

    // then
    final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
    assertThat(commandRequest.valueType()).isEqualTo(ValueType.MESSAGE);
    assertThat(commandRequest.intent()).isEqualTo(MessageIntent.PUBLISH);

    assertThat(commandRequest.getCommand())
        .containsOnly(
            entry("name", "order canceled"),
            entry("correlationKey", "order-123"),
            entry("timeToLive", defaultTimeToLive.toMillis()),
            entry("payload", MsgPackHelper.EMTPY_OBJECT));

    assertThat(messageEvent.getState()).isEqualTo(MessageState.PUBLISHED);
    assertThat(messageEvent.getName()).isEqualTo("order canceled");
    assertThat(messageEvent.getCorrelationKey()).isEqualTo("order-123");
    assertThat(messageEvent.getTimeToLive()).isEqualTo(defaultTimeToLive);
    assertThat(messageEvent.getMessageId()).isNull();
    assertThat(messageEvent.getPayload()).isEqualTo("{}");
    assertThat(messageEvent.getPayloadAsMap()).isEmpty();
  }

  @Test
  public void shouldPublishMessageWithId() {
    // when
    final MessageEvent messageEvent =
        workflowClient
            .newPublishMessageCommand()
            .messageName("order canceled")
            .correlationKey("order-123")
            .messageId("456")
            .send()
            .join();

    // then
    final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
    assertThat(commandRequest.getCommand())
        .containsOnly(
            entry("name", "order canceled"),
            entry("correlationKey", "order-123"),
            entry("timeToLive", defaultTimeToLive.toMillis()),
            entry("messageId", "456"),
            entry("payload", MsgPackHelper.EMTPY_OBJECT));

    assertThat(messageEvent.getMessageId()).isEqualTo("456");
  }

  @Test
  public void shouldPublishMessageWithTimeToLive() {
    // given
    final Duration timeToLive = Duration.ofDays(1);

    // when
    final MessageEvent messageEvent =
        workflowClient
            .newPublishMessageCommand()
            .messageName("order canceled")
            .correlationKey("order-123")
            .timeToLive(timeToLive)
            .send()
            .join();

    // then
    final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
    assertThat(commandRequest.getCommand())
        .containsOnly(
            entry("name", "order canceled"),
            entry("correlationKey", "order-123"),
            entry("timeToLive", timeToLive.toMillis()),
            entry("payload", MsgPackHelper.EMTPY_OBJECT));

    assertThat(messageEvent.getTimeToLive()).isEqualTo(timeToLive);
  }

  @Test
  public void shouldPublishMessageWithPayload() {
    // given
    final String payload = "{\"bar\":4}";

    // when
    final MessageEvent messageEvent =
        workflowClient
            .newPublishMessageCommand()
            .messageName("order canceled")
            .correlationKey("order-123")
            .payload(payload)
            .send()
            .join();

    // then
    final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
    assertThat(commandRequest.getCommand())
        .containsOnly(
            entry("name", "order canceled"),
            entry("correlationKey", "order-123"),
            entry("timeToLive", defaultTimeToLive.toMillis()),
            entry("payload", msgPackConverter.convertToMsgPack(payload)));

    assertThat(messageEvent.getPayload()).isEqualTo(payload);
    assertThat(messageEvent.getPayloadAsMap()).containsOnly(entry("bar", 4));
  }

  @Test
  public void shouldPublishMessageWithAllParameters() {
    // given
    final String payload = "{\"bar\":4}";
    final Duration timeToLive = Duration.ofDays(1);

    // when
    final MessageEvent messageEvent =
        workflowClient
            .newPublishMessageCommand()
            .messageName("order canceled")
            .correlationKey("order-123")
            .timeToLive(timeToLive)
            .messageId("456")
            .payload(payload)
            .send()
            .join();

    // then
    final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
    assertThat(commandRequest.getCommand())
        .containsOnly(
            entry("name", "order canceled"),
            entry("correlationKey", "order-123"),
            entry("timeToLive", timeToLive.toMillis()),
            entry("messageId", "456"),
            entry("payload", msgPackConverter.convertToMsgPack(payload)));

    assertThat(messageEvent.getName()).isEqualTo("order canceled");
    assertThat(messageEvent.getCorrelationKey()).isEqualTo("order-123");
    assertThat(messageEvent.getTimeToLive()).isEqualTo(timeToLive);
    assertThat(messageEvent.getMessageId()).isEqualTo("456");
    assertThat(messageEvent.getPayload()).isEqualTo(payload);
  }

  @Test
  public void shouldPublishMessageWithPayloadAsStream() {
    // given
    final String payload = "{\"bar\":4}";

    // when
    final MessageEvent messageEvent =
        workflowClient
            .newPublishMessageCommand()
            .messageName("order canceled")
            .correlationKey("order-123")
            .payload(new ByteArrayInputStream(payload.getBytes()))
            .send()
            .join();

    // then
    final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
    assertThat(commandRequest.getCommand())
        .contains(entry("payload", msgPackConverter.convertToMsgPack(payload)));

    assertThat(messageEvent.getPayload()).isEqualTo(payload);
    assertThat(messageEvent.getPayloadAsMap()).containsOnly(entry("bar", 4));
  }

  @Test
  public void shouldPublishMessageWithPayloadAsMap() {
    // given
    final String payload = "{\"foo\":\"bar\"}";

    // when
    final MessageEvent messageEvent =
        workflowClient
            .newPublishMessageCommand()
            .messageName("order canceled")
            .correlationKey("order-123")
            .payload(Collections.singletonMap("foo", "bar"))
            .send()
            .join();

    // then
    final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
    assertThat(commandRequest.getCommand())
        .contains(entry("payload", msgPackConverter.convertToMsgPack(payload)));

    assertThat(messageEvent.getPayload()).isEqualTo(payload);
    assertThat(messageEvent.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldPublishMessageWithPayloadAsObject() {
    // given
    final String payload = "{\"foo\":\"bar\"}";

    final PayloadObject payloadObj = new PayloadObject();
    payloadObj.foo = "bar";

    // when
    final MessageEvent messageEvent =
        workflowClient
            .newPublishMessageCommand()
            .messageName("order canceled")
            .correlationKey("order-123")
            .payload(payloadObj)
            .send()
            .join();

    // then
    final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
    assertThat(commandRequest.getCommand())
        .contains(entry("payload", msgPackConverter.convertToMsgPack(payload)));

    assertThat(messageEvent.getPayload()).isEqualTo(payload);
    assertThat(messageEvent.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
    assertThat(messageEvent.getPayloadAsType(PayloadObject.class).foo).isEqualTo("bar");
  }

  @Test
  public void shouldPublishMessageOnSubscriptionPartition() {
    // given
    final int expectedPartition =
        FIRST_PARTITION
            + Math.abs(SubscriptionUtil.getSubscriptionHashCode("order-123") % PARTITION_COUNT);

    // when
    final MessageEvent messageEvent =
        workflowClient
            .newPublishMessageCommand()
            .messageName("order canceled")
            .correlationKey("order-123")
            .send()
            .join();

    // then
    final ExecuteCommandRequest commandRequest = brokerRule.getReceivedCommandRequests().get(0);
    assertThat(commandRequest.partitionId()).isEqualTo(expectedPartition);
    assertThat(messageEvent.getMetadata().getPartitionId()).isEqualTo(expectedPartition);
  }

  @Test
  public void shouldPublishMessagesOnSamePartition() {
    // given
    final int expectedPartition =
        FIRST_PARTITION
            + Math.abs(SubscriptionUtil.getSubscriptionHashCode("order-123") % PARTITION_COUNT);

    // when
    workflowClient
        .newPublishMessageCommand()
        .messageName("msg-1")
        .correlationKey("order-123")
        .send()
        .join();

    workflowClient
        .newPublishMessageCommand()
        .messageName("msg-2")
        .correlationKey("order-123")
        .send()
        .join();

    // then
    assertThat(brokerRule.getReceivedCommandRequests())
        .hasSize(2)
        .extracting(ExecuteCommandRequest::partitionId)
        .containsOnly(expectedPartition);
  }

  @Test
  public void shouldPublishMessagesOnDifferentPartitions() {
    // given
    final int expectedPartition1 =
        FIRST_PARTITION
            + Math.abs(SubscriptionUtil.getSubscriptionHashCode("order-123") % PARTITION_COUNT);

    final int expectedPartition2 =
        FIRST_PARTITION
            + Math.abs(SubscriptionUtil.getSubscriptionHashCode("order-456") % PARTITION_COUNT);

    assertThat(expectedPartition1).isNotEqualTo(expectedPartition2);

    // when
    workflowClient
        .newPublishMessageCommand()
        .messageName("msg-1")
        .correlationKey("order-123")
        .send()
        .join();

    workflowClient
        .newPublishMessageCommand()
        .messageName("msg-2")
        .correlationKey("order-456")
        .send()
        .join();

    // then
    assertThat(brokerRule.getReceivedCommandRequests())
        .hasSize(2)
        .extracting(ExecuteCommandRequest::partitionId)
        .containsExactly(expectedPartition1, expectedPartition2);
  }

  @Test
  public void shouldThrowExceptionOnRejection() {
    // given
    brokerRule.workflowInstances().registerPublishMessageCommand(r -> r.rejection());

    // expect exception
    expectedException.expect(ClientCommandRejectedException.class);
    expectedException.expectMessage("Command (PUBLISH) was rejected");

    // when
    workflowClient
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .messageId("456")
        .send()
        .join();
  }

  @Test
  public void shouldThrowExceptionIfFailedToSerializePayload() {
    class NotSerializable {}

    // then
    expectedException.expect(ClientException.class);
    expectedException.expectMessage("Failed to serialize object");

    // when
    workflowClient
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .payload(new NotSerializable())
        .send()
        .join();
  }

  public static class PayloadObject {
    public String foo;
  }
}
