/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.Decision;
import io.camunda.zeebe.engine.processing.scheduled.runtime.FakeTaskContext;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;
import org.junit.jupiter.api.Test;

final class MessageTimeToLiveCheckSchedulerTest {

  @Test
  void shouldNotEmitCommandWhenNoExpiredMessages() {
    // given
    final MessageState state = mock(MessageState.class);
    when(state.visitMessagesWithDeadlineBeforeTimestamp(anyLong(), any(), any())).thenReturn(false);
    final var scheduler = new MessageTimeToLiveCheckScheduler(state);
    final var ctx = FakeTaskContext.create().withClockMillis(1_000L);

    // when
    final Result result = scheduler.run(ctx);

    // then
    assertThat(result.decision()).isEqualTo(Decision.Idle.INSTANCE);
    assertThat(result.appendedCommands()).isEmpty();
  }

  @Test
  void shouldEmitExpireCommandWhenAtLeastOneMessageExpired() {
    // given
    final MessageState state = mock(MessageState.class);
    when(state.visitMessagesWithDeadlineBeforeTimestamp(anyLong(), any(), any())).thenReturn(true);
    final var scheduler = new MessageTimeToLiveCheckScheduler(state);
    final var ctx = FakeTaskContext.create().withClockMillis(1_000L);

    // when
    final Result result = scheduler.run(ctx);

    // then
    assertThat(result.decision()).isEqualTo(Decision.Idle.INSTANCE);
    assertThat(result.appendedCommands()).hasSize(1);
    assertThat(result.appendedCommands().get(0).intent()).isEqualTo(MessageBatchIntent.EXPIRE);
  }
}
