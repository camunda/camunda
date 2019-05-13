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
package io.zeebe.broker.engine.message;

import static io.zeebe.msgpack.spec.MsgPackHelper.EMTPY_OBJECT;
import static io.zeebe.protocol.intent.MessageIntent.PUBLISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.test.MsgPackConstants;
import io.zeebe.engine.processor.workflow.message.MessageObserver;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.MessageRecordValue;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandRequestBuilder;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class PublishMessageTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final ClientApiRule API_RULE = new ClientApiRule(BROKER_RULE::getAtomix);

  @ClassRule
  public static final RuleChain RULE_CHAIN = RuleChain.outerRule(BROKER_RULE).around(API_RULE);

  @Rule public RecordingExporterTestWatcher testWatcher = new RecordingExporterTestWatcher();

  private PartitionTestClient testClient;

  @Before
  public void setup() {
    testClient = API_RULE.partitionClient();
  }

  @Test
  public void shouldPublishMessage() {

    final ExecuteCommandResponse response =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .put("timeToLive", 1_000)
            .done()
            .sendAndAwait();

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(MessageIntent.PUBLISHED);
    assertThat(response.getValue())
        .containsExactly(
            entry("name", "order canceled"),
            entry("correlationKey", "order-123"),
            entry("timeToLive", 1_000L),
            entry("variables", EMTPY_OBJECT),
            entry("messageId", ""));

    final Record<MessageRecordValue> publishedEvent =
        testClient
            .receiveMessages()
            .withRecordKey(response.getKey())
            .withIntent(MessageIntent.PUBLISHED)
            .getFirst();
    assertThat(publishedEvent.getKey()).isEqualTo(response.getKey());
    assertThat(MsgPackUtil.asMsgPackReturnArray(publishedEvent.getValue().getVariables()))
        .isEqualTo(EMTPY_OBJECT);

    Assertions.assertThat(publishedEvent.getValue())
        .hasName("order canceled")
        .hasCorrelationKey("order-123")
        .hasTimeToLive(1000L)
        .hasMessageId("");
  }

  @Test
  public void shouldPublishMessageWithVariables() {

    final ExecuteCommandResponse response =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .put("timeToLive", 1_000)
            .put("variables", MsgPackConstants.MSGPACK_VARIABLES)
            .done()
            .sendAndAwait();

    assertThat(response.getIntent()).isEqualTo(MessageIntent.PUBLISHED);
    assertThat(response.getValue())
        .contains(entry("variables", MsgPackConstants.MSGPACK_VARIABLES));
  }

  @Test
  public void shouldPublishMessageWithMessageId() {

    final ExecuteCommandResponse response =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .put("timeToLive", 1_000)
            .put("messageId", "msg-1")
            .done()
            .sendAndAwait();

    assertThat(response.getIntent()).isEqualTo(MessageIntent.PUBLISHED);
    assertThat(response.getValue()).contains(entry("messageId", "msg-1"));
  }

  @Test
  public void shouldPublishMessageWithZeroTTL() {

    final ExecuteCommandResponse response =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .put("timeToLive", 0)
            .done()
            .sendAndAwait();

    assertThat(response.getIntent()).isEqualTo(MessageIntent.PUBLISHED);
    assertThat(response.getValue()).contains(entry("timeToLive", 0L));
  }

  @Test
  public void shouldPublishMessageWithNegativeTTL() {

    final ExecuteCommandResponse response =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .put("timeToLive", -1L)
            .done()
            .sendAndAwait();

    assertThat(response.getIntent()).isEqualTo(MessageIntent.PUBLISHED);
    assertThat(response.getValue()).contains(entry("timeToLive", -1L));
  }

  @Test
  public void shouldPublishSecondMessageWithDifferentId() {

    publishMessage("order canceled", "order-123", "msg-1");

    final ExecuteCommandResponse response = publishMessage("order canceled", "order-123", "msg-2");

    assertThat(response.getIntent()).isEqualTo(MessageIntent.PUBLISHED);
  }

  @Test
  public void shouldPublishSecondMessageWithDifferentName() {

    publishMessage("order canceled", "order-123", "msg-1");

    final ExecuteCommandResponse response = publishMessage("order shipped", "order-123", "msg-1");

    assertThat(response.getIntent()).isEqualTo(MessageIntent.PUBLISHED);
  }

  @Test
  public void shouldPublishSecondMessageWithDifferentCorrelationKey() {

    publishMessage("order canceled", "order-123", "msg-1");

    final ExecuteCommandResponse response = publishMessage("order canceled", "order-456", "msg-1");

    assertThat(response.getIntent()).isEqualTo(MessageIntent.PUBLISHED);
  }

  @Test
  public void shouldPublishSameMessageWithEmptyId() {

    publishMessage("order canceled", "order-123", "");

    final ExecuteCommandResponse response = publishMessage("order canceled", "order-123", "");

    assertThat(response.getIntent()).isEqualTo(MessageIntent.PUBLISHED);
  }

  @Test
  public void shouldPublishSameMessageWithoutId() {

    API_RULE
        .createCmdRequest()
        .type(ValueType.MESSAGE, PUBLISH)
        .command()
        .put("name", "order canceled")
        .put("correlationKey", "order-123")
        .put("timeToLive", 1_000)
        .done()
        .sendAndAwait();

    final ExecuteCommandResponse response =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .put("timeToLive", 1_000)
            .done()
            .sendAndAwait();

    assertThat(response.getIntent()).isEqualTo(MessageIntent.PUBLISHED);
  }

  @Test
  public void shouldRejectToPublishSameMessageWithId() {

    publishMessage("order canceled", "order-123", "msg-1");

    final ExecuteCommandResponse response = publishMessage("order canceled", "order-123", "msg-1");

    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.ALREADY_EXISTS);

    final Record<MessageRecordValue> rejection =
        testClient
            .receiveMessages()
            .onlyCommandRejections()
            .withRecordKey(response.getKey())
            .withIntent(PUBLISH)
            .getFirst();

    assertThat(rejection.getMetadata().getRejectionType()).isEqualTo(RejectionType.ALREADY_EXISTS);
  }

  @Test
  public void shouldDeleteMessageAfterTTL() {
    // given
    final long timeToLive = 100;

    final ExecuteCommandResponse response =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .put("timeToLive", timeToLive)
            .done()
            .sendAndAwait();

    // when
    final PartitionTestClient testClient = API_RULE.partitionClient();

    BROKER_RULE
        .getClock()
        .addTime(MessageObserver.MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL.plusMillis(timeToLive));

    // then
    final Record<MessageRecordValue> deletedEvent =
        testClient
            .receiveMessages()
            .withIntent(MessageIntent.DELETED)
            .withRecordKey(response.getKey())
            .getFirst();
    assertThat(deletedEvent.getKey()).isEqualTo(response.getKey());
    assertThat(MsgPackUtil.asMsgPackReturnArray(deletedEvent.getValue().getVariables()))
        .isEqualTo(EMTPY_OBJECT);

    Assertions.assertThat(deletedEvent.getValue())
        .hasName("order canceled")
        .hasCorrelationKey("order-123")
        .hasTimeToLive(100L)
        .hasMessageId("");
  }

  @Test
  public void shouldDeleteMessageImmediatelyWithZeroTTL() {
    // given
    final ExecuteCommandResponse response =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .put("timeToLive", 0L)
            .done()
            .sendAndAwait();

    // when
    BROKER_RULE.getClock().addTime(MessageObserver.MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL);

    // then
    final Record<MessageRecordValue> deletedEvent =
        testClient
            .receiveMessages()
            .withIntent(MessageIntent.DELETED)
            .withRecordKey(response.getKey())
            .getFirst();

    assertThat(deletedEvent.getKey()).isEqualTo(response.getKey());
    assertThat(MsgPackUtil.asMsgPackReturnArray(deletedEvent.getValue().getVariables()))
        .isEqualTo(EMTPY_OBJECT);

    Assertions.assertThat(deletedEvent.getValue())
        .hasName("order canceled")
        .hasCorrelationKey("order-123")
        .hasTimeToLive(0L)
        .hasMessageId("");
  }

  @Test
  public void shouldFailToPublishMessageWithoutName() {

    final ExecuteCommandRequestBuilder request =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("correlationKey", "order-123")
            .put("timeToLive", 1_000)
            .done();

    assertThatThrownBy(request::sendAndAwait)
        .hasMessageContaining("Property 'name' has no valid value");
  }

  @Test
  public void shouldFailToPublishMessageWithoutCorrelationKey() {

    final ExecuteCommandRequestBuilder request =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("timeToLive", 1_000)
            .done();

    assertThatThrownBy(request::sendAndAwait)
        .hasMessageContaining("Property 'correlationKey' has no valid value");
  }

  @Test
  public void shouldFailToPublishMessageWithoutTimeToLive() {

    final ExecuteCommandRequestBuilder request =
        API_RULE
            .createCmdRequest()
            .type(ValueType.MESSAGE, PUBLISH)
            .command()
            .put("name", "order canceled")
            .put("correlationKey", "order-123")
            .done();

    assertThatThrownBy(() -> request.sendAndAwait())
        .hasMessageContaining("Property 'timeToLive' has no valid value");
  }

  private ExecuteCommandResponse publishMessage(
      final String name, final String correlationKey, final String messageId) {

    return API_RULE
        .createCmdRequest()
        .type(ValueType.MESSAGE, PUBLISH)
        .command()
        .put("name", name)
        .put("correlationKey", correlationKey)
        .put("timeToLive", 1_000)
        .put("messageId", messageId)
        .done()
        .sendAndAwait();
  }
}
