/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.engine.util.client.PublishMessageClient;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordAssert;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.value.MessageBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ExpireMessageTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private PublishMessageClient messageClient;

  @Before
  public void init() {
    messageClient =
        ENGINE_RULE.message().withCorrelationKey("order-123").withName("order canceled");
  }

  @Test
  public void shouldExpireMessageAfterTTL() {
    // given
    final long timeToLive = 100;

    // when
    final Record<MessageRecordValue> publishedRecord =
        messageClient.withTimeToLive(timeToLive).publish();

    final Record<MessageRecordValue> secondPublishedRecord =
        messageClient.withTimeToLive(timeToLive).withName("order shipped").publish();

    final var publishedMessageKeys =
        List.of(publishedRecord.getKey(), secondPublishedRecord.getKey());

    ENGINE_RULE.increaseTime(EngineConfiguration.DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL);

    // then
    final Record<MessageBatchRecordValue> expireBatchMessageCommand =
        RecordingExporter.messageBatchRecords().withIntent(MessageBatchIntent.EXPIRE).getFirst();

    Assertions.assertThat(expireBatchMessageCommand.getValue())
        .hasMessageKeys(publishedMessageKeys);

    final List<Long> listOfExpiredMessageKeys =
        RecordingExporter.messageRecords()
            .withIntent(MessageIntent.EXPIRED)
            .flatMapToLong(v -> LongStream.of(v.getKey()))
            .boxed()
            .collect(Collectors.toList());

    assertThat(listOfExpiredMessageKeys).isEqualTo(publishedMessageKeys);
  }

  @Test
  public void shouldExpireMessageImmediatelyWithZeroTTL() {
    // given
    final long timeToLive = 0L;

    // when
    final Record<MessageRecordValue> publishedRecord =
        messageClient.withTimeToLive(timeToLive).publish();

    // then
    final Record<MessageRecordValue> deletedEvent =
        RecordingExporter.messageRecords()
            .withIntent(MessageIntent.EXPIRED)
            .withRecordKey(publishedRecord.getKey())
            .getFirst();

    Assertions.assertThat(deletedEvent.getValue())
        .hasName("order canceled")
        .hasCorrelationKey("order-123")
        .hasTimeToLive(0L)
        .hasMessageId("");
  }

  // regression test for https://github.com/camunda/camunda/issues/5420
  @Test
  public void shouldHaveNoSourceRecordPositionOnExpire() {
    // given
    final long timeToLive = 50L;

    // when
    final Record<MessageRecordValue> publishedRecord =
        messageClient.withTimeToLive(timeToLive).publish();
    ENGINE_RULE.increaseTime(EngineConfiguration.DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL);

    // then
    final Record<MessageBatchRecordValue> deleteCommand =
        RecordingExporter.messageBatchRecords()
            .withIntent(MessageBatchIntent.EXPIRE)
            .hasMessageKey(publishedRecord.getKey())
            .getFirst();

    // then
    assertThat(deleteCommand.getSourceRecordPosition()).isLessThan(0);
  }

  @Test
  public void shouldExpireMessageAfterTTLWithMessageBody() {
    // given
    final Record<MessageRecordValue> publishedRecord =
        ENGINE_RULE
            .message()
            .withCorrelationKey("correlation1")
            .withName("message1")
            .withTimeToLive(Duration.ofMinutes(1))
            .publish();

    final Record<MessageRecordValue> secondPublishedRecord =
        ENGINE_RULE
            .message()
            .withCorrelationKey("correlation2")
            .withName("message2")
            .withTimeToLive(Duration.ofMinutes(1))
            .publish();

    // when
    ENGINE_RULE.increaseTime(EngineConfiguration.DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL);

    // then
    final Record<MessageBatchRecordValue> expireBatchMessageCommand =
        RecordingExporter.messageBatchRecords().withIntent(MessageBatchIntent.EXPIRE).getFirst();

    Assertions.assertThat(expireBatchMessageCommand.getValue())
        .hasMessageKeys(List.of(publishedRecord.getKey(), secondPublishedRecord.getKey()));

    final MessageRecordValue firstMessage = publishedRecord.getValue();
    final MessageRecordValue secondMessage = secondPublishedRecord.getValue();

    assertThat(RecordingExporter.messageRecords().withIntent(MessageIntent.EXPIRED).limit(2))
        .extracting(Record::getValue)
        .extracting(
            MessageRecordValue::getName,
            MessageRecordValue::getCorrelationKey,
            MessageRecordValue::getTimeToLive)
        .containsExactly(
            tuple(
                firstMessage.getName(),
                firstMessage.getCorrelationKey(),
                firstMessage.getTimeToLive()),
            tuple(
                secondMessage.getName(),
                secondMessage.getCorrelationKey(),
                secondMessage.getTimeToLive()));
  }

  @Test
  public void shouldSkipAlreadyCorrelatedMessage() {
    // given
    final Record<MessageRecordValue> firstMessage =
        ENGINE_RULE
            .message()
            .withCorrelationKey("correlation1")
            .withName("message1")
            .withTimeToLive(Duration.ofMinutes(1))
            .publish();
    final long firstMessageKey = firstMessage.getKey();

    final long secondMessageKey =
        ENGINE_RULE
            .message()
            .withCorrelationKey("correlation2")
            .withName("message2")
            .withTimeToLive(Duration.ofMinutes(1))
            .publish()
            .getKey();

    // when
    // assume the message already correlated and expired in the meantime
    ENGINE_RULE.writeRecords(
        RecordToWrite.command()
            .key(firstMessageKey)
            .message(MessageIntent.EXPIRE, firstMessage.getValue()));

    // write the batch expire command that will be processed after the correlation
    final MessageBatchRecord messageBatchRecord = new MessageBatchRecord();
    messageBatchRecord.addMessageKey(firstMessageKey).addMessageKey(secondMessageKey);
    ENGINE_RULE.writeRecords(RecordToWrite.command().messageBatch(messageBatchRecord));

    // then
    final Record<MessageBatchRecordValue> expireBatchMessageCommand =
        RecordingExporter.messageBatchRecords().withIntent(MessageBatchIntent.EXPIRE).getFirst();

    Assertions.assertThat(expireBatchMessageCommand.getValue())
        .hasMessageKeys(List.of(firstMessageKey, secondMessageKey));

    final List<Long> listOfExpiredMessageKeys =
        RecordingExporter.messageRecords()
            .withIntent(MessageIntent.EXPIRED)
            .withSourceRecordPosition(
                expireBatchMessageCommand.getPosition()) // only filter by the batch
            .flatMapToLong(v -> LongStream.of(v.getKey()))
            .boxed()
            .collect(Collectors.toList());

    assertThat(listOfExpiredMessageKeys).isEqualTo(List.of(secondMessageKey));
  }

  @Test
  public void shouldRejectIfNoMessageIsExpired() {
    // given
    final var firstMessage =
        ENGINE_RULE
            .message()
            .withCorrelationKey("correlation1")
            .withName("message1")
            .withTimeToLive(Duration.ofMinutes(1))
            .publish();
    final long firstMessageKey = firstMessage.getKey();

    final var secondMessage =
        ENGINE_RULE
            .message()
            .withCorrelationKey("correlation2")
            .withName("message2")
            .withTimeToLive(Duration.ofMinutes(1))
            .publish();
    final long secondMessageKey = secondMessage.getKey();

    // when
    // assume the message already correlated and expired in the meantime
    ENGINE_RULE.writeRecords(
        RecordToWrite.command()
            .key(firstMessageKey)
            .message(MessageIntent.EXPIRE, firstMessage.getValue()));
    ENGINE_RULE.writeRecords(
        RecordToWrite.command()
            .key(secondMessageKey)
            .message(MessageIntent.EXPIRE, secondMessage.getValue()));

    // write the batch expire command that will be processed after the correlation
    final MessageBatchRecord messageBatchRecord = new MessageBatchRecord();
    messageBatchRecord.addMessageKey(firstMessageKey).addMessageKey(secondMessageKey);
    ENGINE_RULE.writeRecords(RecordToWrite.command().messageBatch(messageBatchRecord));

    // then
    final Record<MessageBatchRecordValue> expireBatchMessageCommand =
        RecordingExporter.messageBatchRecords()
            .withIntent(MessageBatchIntent.EXPIRE)
            .onlyCommandRejections()
            .getFirst();

    Assertions.assertThat(expireBatchMessageCommand.getValue())
        .hasMessageKeys(List.of(firstMessageKey, secondMessageKey));
    RecordAssert.assertThat(expireBatchMessageCommand)
        .hasRejectionReason(
            "Expected to expire 2 messages in a batch, but none of the messages were found in the state.")
        .hasRejectionType(RejectionType.NOT_FOUND);
  }
}
