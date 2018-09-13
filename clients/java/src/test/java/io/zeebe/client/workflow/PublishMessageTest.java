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
package io.zeebe.client.workflow;

import static io.zeebe.exporter.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.MessageRecordValue;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class PublishMessageTest {
  @Rule public EmbeddedBrokerRule rule = new EmbeddedBrokerRule();

  private ZeebeClient client;

  @Before
  public void setUp() {
    client =
        ZeebeClient.newClientBuilder()
            .brokerContactPoint(rule.getGatewayAddress().toString())
            .build();
  }

  @Test
  public void shouldPublishMessage() {
    // when
    client
        .workflowClient()
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .timeToLive(Duration.ofDays(1))
        .messageId("theId")
        .send()
        .join();

    // then
    final Record<MessageRecordValue> first =
        RecordingExporter.messageRecords().withIntent(MessageIntent.PUBLISHED).getFirst();

    assertThat(first.getValue())
        .hasName("name")
        .hasCorrelationKey("key")
        .hasMessageId("theId")
        .hasTimeToLive(Duration.ofDays(1).toMillis());
  }

  @Test
  public void shouldPublishMessageWithStringPayload() {
    // when
    client
        .workflowClient()
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .payload("{\"foo\":\"bar\"}")
        .send()
        .join();

    // then
    final Record<MessageRecordValue> first =
        RecordingExporter.messageRecords().withIntent(MessageIntent.PUBLISHED).getFirst();

    assertThat(first.getValue()).hasName("name").hasCorrelationKey("key");
    assertThat(first.getValue().getPayloadAsMap()).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldPublishMessageWithInputStreamPayload() {
    // given
    final String payload = "{\"foo\":\"bar\"}";
    final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(payload.getBytes());

    // when
    client
        .workflowClient()
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .payload(byteArrayInputStream)
        .send()
        .join();

    // then
    final Record<MessageRecordValue> first =
        RecordingExporter.messageRecords().withIntent(MessageIntent.PUBLISHED).getFirst();

    assertThat(first.getValue()).hasName("name").hasCorrelationKey("key");
    assertThat(first.getValue().getPayloadAsMap()).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldPublishMessageWithMapPayload() {
    // given
    final Map<String, Object> payload = new HashMap<>();
    payload.put("foo", "bar");

    // when
    client
        .workflowClient()
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .payload(payload)
        .send()
        .join();

    // then
    final Record<MessageRecordValue> first =
        RecordingExporter.messageRecords().withIntent(MessageIntent.PUBLISHED).getFirst();

    assertThat(first.getValue()).hasName("name").hasCorrelationKey("key");
    assertThat(first.getValue().getPayloadAsMap()).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldPublishMessageWithObjectPayload() {
    // given

    // when
    client
        .workflowClient()
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .payload(new Payload())
        .send()
        .join();

    // then
    final Record<MessageRecordValue> first =
        RecordingExporter.messageRecords().withIntent(MessageIntent.PUBLISHED).getFirst();

    assertThat(first.getValue()).hasName("name").hasCorrelationKey("key");
    assertThat(first.getValue().getPayloadAsMap()).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldNotPublishMessageWithNoJsonObjectAsPayload() {
    assertThatThrownBy(
        () ->
            client
                .workflowClient()
                .newPublishMessageCommand()
                .messageName("name")
                .correlationKey("key")
                .payload("[]")
                .send()
                .join());
  }

  public static class Payload {

    private final String foo = "bar";

    Payload() {}

    public String getFoo() {
      return foo;
    }
  }
}
