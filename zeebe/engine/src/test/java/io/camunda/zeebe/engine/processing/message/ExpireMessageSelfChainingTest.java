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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests that the batch expiry processor self-chains when there are more expired messages than the
 * batch limit allows in a single processing cycle.
 */
public final class ExpireMessageSelfChainingTest {

  private static final int BATCH_LIMIT = 3;

  @ClassRule
  public static final EngineRule ENGINE_RULE =
      EngineRule.singlePartition()
          .withEngineConfig(cfg -> cfg.setMessagesTtlCheckerBatchLimit(BATCH_LIMIT));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldSelfChainWhenMoreMessagesThanBatchLimit() {
    // given — publish more messages than the batch limit
    final int messageCount = BATCH_LIMIT * 2 + 1; // 7 messages, limit is 3
    final long timeToLive = 100;

    final List<Long> publishedKeys =
        LongStream.range(0, messageCount)
            .mapToObj(
                i ->
                    ENGINE_RULE
                        .message()
                        .withCorrelationKey("key-" + i)
                        .withName("msg-" + i)
                        .withTimeToLive(timeToLive)
                        .publish()
                        .getKey())
            .collect(Collectors.toList());

    // when
    ENGINE_RULE.increaseTime(EngineConfiguration.DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL);

    // then — all messages should eventually be expired
    final List<Long> expiredKeys =
        RecordingExporter.messageRecords()
            .withIntent(MessageIntent.EXPIRED)
            .limit(messageCount)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(expiredKeys).containsExactlyInAnyOrderElementsOf(publishedKeys);

    // and — there should be at least 3 EXPIRE commands (self-chaining):
    // with 7 messages and batch limit 3, the processor needs 3 cycles (3+3+1)
    final long expireCommandCount =
        RecordingExporter.messageBatchRecords()
            .withIntent(MessageBatchIntent.EXPIRE)
            .limit(3)
            .count();

    assertThat(expireCommandCount).isEqualTo(3);
  }
}
