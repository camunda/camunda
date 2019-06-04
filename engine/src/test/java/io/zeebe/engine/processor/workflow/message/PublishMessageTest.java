/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.message;

import static io.zeebe.msgpack.spec.MsgPackHelper.EMTPY_OBJECT;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.engine.processor.workflow.MsgPackConstants;
import io.zeebe.engine.util.EngineRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.MessageRecordValue;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import org.junit.ClassRule;
import org.junit.Test;

public class PublishMessageTest {

  @ClassRule public static final EngineRule ENGINE_RULE = new EngineRule();

  @Test
  public void shouldPublishMessage() {
    // when
    final Record<MessageRecordValue> publishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123", r -> r.setName("order canceled").setTimeToLive(1_000L));

    // then
    assertThat(publishedRecord.getKey()).isEqualTo(publishedRecord.getKey());
    assertThat(MsgPackUtil.asMsgPackReturnArray(publishedRecord.getValue().getVariables()))
        .isEqualTo(EMTPY_OBJECT);

    Assertions.assertThat(publishedRecord.getMetadata())
        .hasIntent(MessageIntent.PUBLISHED)
        .hasRecordType(RecordType.EVENT)
        .hasValueType(ValueType.MESSAGE);

    Assertions.assertThat(publishedRecord.getValue())
        .hasName("order canceled")
        .hasCorrelationKey("order-123")
        .hasTimeToLive(1000L)
        .hasMessageId("");
  }

  @Test
  public void shouldPublishMessageWithVariables() throws Exception {
    // when
    final Record<MessageRecordValue> publishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123",
            r ->
                r.setName("order canceled")
                    .setTimeToLive(1_000L)
                    .setVariables(MsgPackConstants.MSGPACK_VARIABLES));

    // then
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    final JsonNode expectedJson = objectMapper.readTree(MsgPackConstants.JSON_DOCUMENT);

    final JsonNode actualJson = objectMapper.readTree(publishedRecord.getValue().getVariables());

    assertThat(actualJson).isEqualTo(expectedJson);
  }

  @Test
  public void shouldPublishMessageWithMessageId() {
    // when
    final Record<MessageRecordValue> publishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123",
            r ->
                r.setName("order canceled")
                    .setTimeToLive(1_000L)
                    .setMessageId("shouldPublishMessageWithMessageId"));

    // then
    Assertions.assertThat(publishedRecord.getValue())
        .hasMessageId("shouldPublishMessageWithMessageId");
  }

  @Test
  public void shouldPublishMessageWithZeroTTL() {
    // when
    final Record<MessageRecordValue> publishedRecord =
        ENGINE_RULE.publishMessage("order-123", r -> r.setName("order canceled").setTimeToLive(0L));

    // then
    Assertions.assertThat(publishedRecord.getValue()).hasTimeToLive(0L);
  }

  @Test
  public void shouldPublishMessageWithNegativeTTL() {
    // when
    final Record<MessageRecordValue> publishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123", r -> r.setName("order canceled").setTimeToLive(-1L));

    // then
    Assertions.assertThat(publishedRecord.getValue()).hasTimeToLive(-1L);
  }

  @Test
  public void shouldPublishSecondMessageWithDifferentId() {
    // when
    final Record<MessageRecordValue> publishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123",
            r ->
                r.setName("order canceled")
                    .setTimeToLive(1_000L)
                    .setMessageId("shouldPublishSecondMessageWithDifferentId"));

    final Record<MessageRecordValue> secondPublishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123",
            r ->
                r.setName("order canceled")
                    .setTimeToLive(1_000L)
                    .setMessageId("shouldPublishSecondMessageWithDifferentId-2"));

    // then
    assertThat(publishedRecord.getKey()).isLessThan(secondPublishedRecord.getKey());
  }

  @Test
  public void shouldPublishSecondMessageWithDifferentName() {
    // when
    final Record<MessageRecordValue> publishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123",
            r ->
                r.setName("order canceled")
                    .setTimeToLive(1_000L)
                    .setMessageId("shouldPublishSecondMessageWithDifferentName"));

    final Record<MessageRecordValue> secondPublishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123",
            r ->
                r.setName("order shipped")
                    .setTimeToLive(1_000L)
                    .setMessageId("shouldPublishSecondMessageWithDifferentName"));

    // then
    assertThat(publishedRecord.getKey()).isLessThan(secondPublishedRecord.getKey());
  }

  @Test
  public void shouldPublishSecondMessageWithDifferentCorrelationKey() {
    // when
    final Record<MessageRecordValue> publishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123",
            r ->
                r.setName("order canceled")
                    .setTimeToLive(1_000L)
                    .setMessageId("shouldPublishSecondMessageWithDifferentCorrelationKey"));

    final Record<MessageRecordValue> secondPublishedRecord =
        ENGINE_RULE.publishMessage(
            "order-456",
            r ->
                r.setName("order shipped")
                    .setTimeToLive(1_000L)
                    .setMessageId("shouldPublishSecondMessageWithDifferentCorrelationKey"));

    // then
    assertThat(publishedRecord.getKey()).isLessThan(secondPublishedRecord.getKey());
  }

  @Test
  public void shouldPublishSameMessageWithEmptyId() {
    // when
    final Record<MessageRecordValue> publishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123", r -> r.setName("order canceled").setTimeToLive(1_000L).setMessageId(""));

    final Record<MessageRecordValue> secondPublishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123", r -> r.setName("order shipped").setTimeToLive(1_000L).setMessageId(""));

    // then
    assertThat(publishedRecord.getKey()).isLessThan(secondPublishedRecord.getKey());
  }

  @Test
  public void shouldPublishSameMessageWithoutId() {
    // when
    final Record<MessageRecordValue> publishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123", r -> r.setName("order canceled").setTimeToLive(1_000L));

    final Record<MessageRecordValue> secondPublishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123", r -> r.setName("order shipped").setTimeToLive(1_000L));

    // then
    assertThat(publishedRecord.getKey()).isLessThan(secondPublishedRecord.getKey());
  }

  @Test
  public void shouldRejectToPublishSameMessageWithId() {
    // when
    ENGINE_RULE.publishMessage(
        "order-123",
        r ->
            r.setName("order canceled")
                .setTimeToLive(1_000L)
                .setMessageId("shouldRejectToPublishSameMessageWithId"));

    final Record<MessageRecordValue> rejectedCommand =
        ENGINE_RULE.publishMessageWithExpectedRejection(
            "order-123",
            r ->
                r.setName("order canceled")
                    .setTimeToLive(1_000L)
                    .setMessageId("shouldRejectToPublishSameMessageWithId"));

    // then
    assertThat(rejectedCommand.getMetadata().getRecordType())
        .isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejectedCommand.getMetadata().getRejectionType())
        .isEqualTo(RejectionType.ALREADY_EXISTS);
  }

  @Test
  public void shouldDeleteMessageAfterTTL() {
    // given
    final long timeToLive = 100;

    // when
    final Record<MessageRecordValue> publishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123", r -> r.setName("order canceled").setTimeToLive(timeToLive));

    ENGINE_RULE.increaseTime(MessageObserver.MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL);

    // then
    final Record<MessageRecordValue> deletedEvent =
        RecordingExporter.messageRecords()
            .withIntent(MessageIntent.DELETED)
            .withRecordKey(publishedRecord.getKey())
            .getFirst();

    Assertions.assertThat(deletedEvent.getValue())
        .hasName("order canceled")
        .hasCorrelationKey("order-123")
        .hasTimeToLive(100L)
        .hasMessageId("");
  }

  @Test
  public void shouldDeleteMessageImmediatelyWithZeroTTL() {
    // given
    final long timeToLive = 0L;

    // when
    final Record<MessageRecordValue> publishedRecord =
        ENGINE_RULE.publishMessage(
            "order-123", r -> r.setName("order canceled").setTimeToLive(timeToLive));

    // then
    final Record<MessageRecordValue> deletedEvent =
        RecordingExporter.messageRecords()
            .withIntent(MessageIntent.DELETED)
            .withRecordKey(publishedRecord.getKey())
            .getFirst();

    Assertions.assertThat(deletedEvent.getValue())
        .hasName("order canceled")
        .hasCorrelationKey("order-123")
        .hasTimeToLive(0L)
        .hasMessageId("");
  }
}
