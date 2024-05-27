/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.PublishMessageClient;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.value.MessageBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
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
        ENGINE_RULE
            .message()
            .withCorrelationKey("order-123")
            .withName("order canceled")
            .withTimeToLive(1_000L);
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
}
