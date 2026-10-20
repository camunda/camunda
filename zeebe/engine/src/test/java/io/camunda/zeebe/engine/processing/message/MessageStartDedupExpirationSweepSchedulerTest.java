/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceDedupState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Asserts the trigger command the expired-dedup sweep scheduler writes on {@code P_B}. The command
 * belongs to no process instance, so it must carry the not-ordinal-controlled marker rather than
 * the record's default ordinal.
 */
public final class MessageStartDedupExpirationSweepSchedulerTest {

  private MessageStartProcessInstanceDedupState mockDedupState;
  private MessageStartDedupExpirationSweepScheduler scheduler;

  @BeforeEach
  void setUp() {
    mockDedupState = mock(MessageStartProcessInstanceDedupState.class);
    final var mockContext = mock(ReadonlyStreamProcessorContext.class);
    when(mockContext.getScheduleService()).thenReturn(mock(ProcessingScheduleService.class));
    when(mockContext.getClock()).thenReturn(mock(StreamClock.class));

    scheduler =
        new MessageStartDedupExpirationSweepScheduler(Duration.ofSeconds(1), mockDedupState);
    scheduler.onRecovered(mockContext);
  }

  @Test
  void shouldMarkSweepTriggerCommandAsNotOrdinalControlled() {
    // given an expired dedup row on this partition
    when(mockDedupState.hasExpiredEntry(anyLong())).thenReturn(true);
    final var taskResultBuilder = mock(TaskResultBuilder.class);

    // when
    scheduler.execute(taskResultBuilder);

    // then the trigger command belongs to no instance
    final var commandCaptor =
        ArgumentCaptor.forClass(MessageStartProcessInstanceRequestRecord.class);
    verify(taskResultBuilder)
        .appendCommandRecord(
            eq(MessageStartProcessInstanceRequestIntent.SWEEP_EXPIRED_DEDUPS),
            commandCaptor.capture());
    assertThat(commandCaptor.getValue().getStorageOrdinal())
        .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
  }
}
