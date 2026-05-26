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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.message.MessageStartProcessInstanceAsk;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import java.time.Duration;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public final class PendingMessageStartAskCheckSchedulerTest {

  private static final Duration RETRY_INTERVAL = Duration.ofSeconds(10);
  private static final Duration CHECK_INTERVAL = Duration.ofSeconds(10);
  private static final long NOW = 100_000L;

  private SubscriptionCommandSender mockCommandSender;
  private MessageStartProcessInstanceAskState mockState;
  private RoutingInfo mockRoutingInfo;
  private ProcessingScheduleService mockScheduleService;
  private ReadonlyStreamProcessorContext mockContext;
  private StreamClock mockClock;
  private PendingMessageStartAskCheckScheduler scheduler;

  @BeforeEach
  void setUp() {
    mockCommandSender = mock(SubscriptionCommandSender.class);
    mockState = mock(MessageStartProcessInstanceAskState.class);
    mockRoutingInfo = mock(RoutingInfo.class);
    mockScheduleService = mock(ProcessingScheduleService.class);
    mockContext = mock(ReadonlyStreamProcessorContext.class);
    mockClock = mock(StreamClock.class);

    when(mockContext.getScheduleService()).thenReturn(mockScheduleService);
    when(mockContext.getClock()).thenReturn(mockClock);
    when(mockClock.millis()).thenReturn(NOW);

    scheduler =
        new PendingMessageStartAskCheckScheduler(
            mockCommandSender, mockState, mockRoutingInfo, () -> RETRY_INTERVAL, CHECK_INTERVAL);
  }

  @Nested
  class Lifecycle {

    @Test
    void shouldScheduleAtFixedRateOnRecovered() {
      // when
      scheduler.onRecovered(mockContext);

      // then
      verify(mockScheduleService).runAtFixedRate(eq(CHECK_INTERVAL), eq(scheduler));
    }
  }

  @Nested
  class SendAsk {

    @Test
    void shouldSendAskForEntryPastDeadline() {
      // given
      scheduler.onRecovered(mockContext);
      final var ask = createAsk(1L, 100L, "my-business-id");
      when(mockState.getPendingAsksPastDeadline(NOW - RETRY_INTERVAL.toMillis()))
          .thenReturn(List.of(ask));
      when(mockRoutingInfo.partitionForCorrelationKey(any())).thenReturn(2);

      // when
      scheduler.run();

      // then
      verify(mockCommandSender)
          .sendDirectStartProcessInstanceRequest(
              eq(2),
              eq(1L),
              any(),
              any(),
              any(),
              eq(100L),
              any(),
              any(),
              anyLong(),
              any(),
              anyLong(),
              anyString());
    }

    @Test
    void shouldUpdateLastSentTimeAfterSending() {
      // given
      scheduler.onRecovered(mockContext);
      final var ask = createAsk(1L, 100L, "my-business-id");
      when(mockState.getPendingAsksPastDeadline(anyLong())).thenReturn(List.of(ask));
      when(mockRoutingInfo.partitionForCorrelationKey(any())).thenReturn(2);

      // when
      scheduler.run();

      // then
      verify(mockState).updateLastSentTime(1L, 100L, NOW);
    }

    @Test
    void shouldNotSendAskWhenNoneArePastDeadline() {
      // given
      scheduler.onRecovered(mockContext);
      when(mockState.getPendingAsksPastDeadline(anyLong())).thenReturn(List.of());

      // when
      scheduler.run();

      // then
      verify(mockCommandSender, never())
          .sendDirectStartProcessInstanceRequest(
              anyInt(),
              anyLong(),
              any(),
              any(),
              any(),
              anyLong(),
              any(),
              any(),
              anyLong(),
              any(),
              anyLong(),
              anyString());
    }

    @Test
    void shouldSendMultipleAsks() {
      // given
      scheduler.onRecovered(mockContext);
      final var ask1 = createAsk(1L, 100L, "business-1");
      final var ask2 = createAsk(2L, 200L, "business-2");
      when(mockState.getPendingAsksPastDeadline(anyLong())).thenReturn(List.of(ask1, ask2));
      when(mockRoutingInfo.partitionForCorrelationKey(any())).thenReturn(2);

      // when
      scheduler.run();

      // then
      final ArgumentCaptor<Long> messageKeyCaptor = ArgumentCaptor.forClass(Long.class);
      verify(mockCommandSender, org.mockito.Mockito.times(2))
          .sendDirectStartProcessInstanceRequest(
              anyInt(),
              messageKeyCaptor.capture(),
              any(),
              any(),
              any(),
              anyLong(),
              any(),
              any(),
              anyLong(),
              any(),
              anyLong(),
              anyString());

      assertThat(messageKeyCaptor.getAllValues()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void shouldCalculateTargetPartitionFromBusinessId() {
      // given
      scheduler.onRecovered(mockContext);
      final var ask = createAsk(1L, 100L, "specific-business-id");
      when(mockState.getPendingAsksPastDeadline(anyLong())).thenReturn(List.of(ask));

      final ArgumentCaptor<org.agrona.DirectBuffer> businessIdCaptor =
          ArgumentCaptor.forClass(org.agrona.DirectBuffer.class);
      when(mockRoutingInfo.partitionForCorrelationKey(businessIdCaptor.capture())).thenReturn(3);

      // when
      scheduler.run();

      // then
      verify(mockCommandSender)
          .sendDirectStartProcessInstanceRequest(
              eq(3),
              anyLong(),
              any(),
              any(),
              any(),
              anyLong(),
              any(),
              any(),
              anyLong(),
              any(),
              anyLong(),
              anyString());

      // Verify businessId was used for routing
      final var capturedBusinessId = businessIdCaptor.getValue();
      assertThat(capturedBusinessId.getStringWithoutLengthUtf8(0, capturedBusinessId.capacity()))
          .isEqualTo("specific-business-id");
    }

    private MessageStartProcessInstanceAsk createAsk(
        final long messageKey, final long processDefinitionKey, final String businessId) {
      final var ask = new MessageStartProcessInstanceAsk();
      ask.setMessageKey(messageKey);
      ask.setProcessDefinitionKey(processDefinitionKey);
      ask.setBusinessId(new UnsafeBuffer(businessId.getBytes()));
      ask.setMessageName(new UnsafeBuffer("message-name".getBytes()));
      ask.setCorrelationKey(new UnsafeBuffer("correlation".getBytes()));
      ask.setBpmnProcessId(new UnsafeBuffer("process-id".getBytes()));
      ask.setStartEventId(new UnsafeBuffer("start-event".getBytes()));
      ask.setMessageStartEventSubscriptionKey(999L);
      ask.setVariables(new UnsafeBuffer(new byte[0]));
      ask.setTenantId("<default>");
      return ask;
    }
  }
}
