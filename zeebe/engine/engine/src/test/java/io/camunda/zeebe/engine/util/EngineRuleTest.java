/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;

public class EngineRuleTest {
  @Rule public EngineRule engineRule = EngineRule.singlePartition();

  /**
   * Previously, commands were written with timestamps that weren't controlled by the actor clock in
   * {@link EngineRule}.
   *
   * @see <a href="https://github.com/camunda/camunda/issues/8891">Issue 8891</a>
   */
  @Test
  public void commandTimestampIsControllable() {
    // given
    final var expectedTimestamp = Instant.now().minus(Duration.ofDays(1));
    engineRule.getClock().setCurrentTime(expectedTimestamp);

    // when
    final var message = engineRule.message().withName("test").withCorrelationKey("test").publish();
    engineRule.awaitProcessingOf(message);

    // then
    final var records =
        RecordingExporter.messageRecords().limit(r -> r.getIntent() == MessageIntent.PUBLISHED);
    assertThat(records)
        .allSatisfy(
            record ->
                assertThat(record.getTimestamp()).isEqualTo(expectedTimestamp.toEpochMilli()));
  }
}
