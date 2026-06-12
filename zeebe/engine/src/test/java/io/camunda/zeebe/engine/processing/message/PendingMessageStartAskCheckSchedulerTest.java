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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceAskState.PendingAskVisitor;
import io.camunda.zeebe.engine.state.message.MessageStartProcessInstanceAsk;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import java.time.Duration;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public final class PendingMessageStartAskCheckSchedulerTest {

  private static final Duration RETRY_INTERVAL = Duration.ofSeconds(10);
  private static final long BASE_MILLIS = RETRY_INTERVAL.toMillis();
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
            mockCommandSender, mockState, mockRoutingInfo, () -> RETRY_INTERVAL);
  }

  /**
   * Makes the mocked state visit each given ask with the supplied last-sent time, so the scheduler
   * applies its per-ask eligibility check ({@code lastSent + interval(rejectionCount) <= now}).
   */
  private void stubPendingAsks(
      final long lastSentTime, final MessageStartProcessInstanceAsk... asks) {
    doAnswer(
            invocation -> {
              final PendingAskVisitor visitor = invocation.getArgument(0);
              for (final var ask : asks) {
                visitor.visit(lastSentTime, ask);
              }
              return null;
            })
        .when(mockState)
        .forEachPendingAsk(any());
  }

  private MessageStartProcessInstanceAsk createAsk(
      final long messageKey, final long processDefinitionKey, final String businessId) {
    return createAsk(messageKey, processDefinitionKey, businessId, 0L);
  }

  private MessageStartProcessInstanceAsk createAsk(
      final long messageKey,
      final long processDefinitionKey,
      final String businessId,
      final long rejectionCount) {
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
    ask.setRejectionCount(rejectionCount);
    return ask;
  }

  private void verifyNoSend() {
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

  @Nested
  class Lifecycle {

    @Test
    void shouldScheduleAtFixedRateOnRecovered() {
      // when
      scheduler.onRecovered(mockContext);

      // then
      verify(mockScheduleService).runAtFixedRate(eq(RETRY_INTERVAL), eq(scheduler));
    }
  }

  @Nested
  class SendAsk {

    @Test
    void shouldSendDueAsk() {
      // given a fresh ask whose base interval has elapsed
      scheduler.onRecovered(mockContext);
      stubPendingAsks(NOW - BASE_MILLIS, createAsk(1L, 100L, "my-business-id"));
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
      stubPendingAsks(0L, createAsk(1L, 100L, "my-business-id"));
      when(mockRoutingInfo.partitionForCorrelationKey(any())).thenReturn(2);

      // when
      scheduler.run();

      // then
      verify(mockState).updateLastSentTime(1L, 100L, NOW);
    }

    @Test
    void shouldNotSendAskThatIsNotYetDue() {
      // given a fresh ask that was just sent (base interval has not elapsed)
      scheduler.onRecovered(mockContext);
      stubPendingAsks(NOW, createAsk(1L, 100L, "my-business-id"));

      // when
      scheduler.run();

      // then
      verifyNoSend();
    }

    @Test
    void shouldSendMultipleDueAsks() {
      // given
      scheduler.onRecovered(mockContext);
      stubPendingAsks(0L, createAsk(1L, 100L, "business-1"), createAsk(2L, 200L, "business-2"));
      when(mockRoutingInfo.partitionForCorrelationKey(any())).thenReturn(2);

      // when
      scheduler.run();

      // then
      final ArgumentCaptor<Long> messageKeyCaptor = ArgumentCaptor.forClass(Long.class);
      verify(mockCommandSender, times(2))
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
      stubPendingAsks(0L, createAsk(1L, 100L, "specific-business-id"));

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

      final var capturedBusinessId = businessIdCaptor.getValue();
      assertThat(capturedBusinessId.getStringWithoutLengthUtf8(0, capturedBusinessId.capacity()))
          .isEqualTo("specific-business-id");
    }
  }

  @Nested
  class BackOff {

    @Test
    void shouldRetryFreshAskAtBaseInterval() {
      // given a never-rejected ask whose base interval has just elapsed
      scheduler.onRecovered(mockContext);
      stubPendingAsks(NOW - BASE_MILLIS, createAsk(1L, 100L, "b", 0L));
      when(mockRoutingInfo.partitionForCorrelationKey(any())).thenReturn(2);

      // when
      scheduler.run();

      // then it is re-sent
      verify(mockState).updateLastSentTime(1L, 100L, NOW);
    }

    @Test
    void shouldNotRetryBackedOffAskBeforeItsInterval() {
      // given an ask rejected twice (interval = base * 2^2 = 4x) sent only 3x base ago
      scheduler.onRecovered(mockContext);
      stubPendingAsks(NOW - (3 * BASE_MILLIS), createAsk(1L, 100L, "b", 2L));

      // when
      scheduler.run();

      // then it is not yet due
      verifyNoSend();
    }

    @Test
    void shouldRetryBackedOffAskAfterItsInterval() {
      // given an ask rejected twice (interval = base * 4) whose interval has elapsed
      scheduler.onRecovered(mockContext);
      stubPendingAsks(NOW - (4 * BASE_MILLIS), createAsk(1L, 100L, "b", 2L));
      when(mockRoutingInfo.partitionForCorrelationKey(any())).thenReturn(2);

      // when
      scheduler.run();

      // then it is re-sent
      verify(mockState).updateLastSentTime(1L, 100L, NOW);
    }

    @Test
    void shouldCapBackoffInterval() {
      // given an ask rejected far beyond the exponent cap (6): the interval saturates at base * 64,
      // not base * 2^rejectionCount. Use a larger clock so the capped interval is representable.
      scheduler.onRecovered(mockContext);
      when(mockClock.millis()).thenReturn(1_000_000L);
      // sent exactly base*64 ago: due only because the interval is capped at 64x (uncapped 2^20x
      // would be nowhere near due)
      stubPendingAsks(1_000_000L - (64 * BASE_MILLIS), createAsk(1L, 100L, "b", 20L));
      when(mockRoutingInfo.partitionForCorrelationKey(any())).thenReturn(2);

      // when
      scheduler.run();

      // then it is re-sent, proving the interval was capped at base * 64
      verify(mockState).updateLastSentTime(1L, 100L, 1_000_000L);
    }

    @Test
    void shouldSaturateBackoffIntervalInsteadOfOverflowing() {
      // given a pathologically large base interval such that doubling it for the back-off would
      // overflow a long. A naive `interval *= 2` would wrap to a negative interval, making
      // `lastSentTime + interval <= now` always true and turning the back-off into a retry storm.
      final var hugeBaseScheduler =
          new PendingMessageStartAskCheckScheduler(
              mockCommandSender,
              mockState,
              mockRoutingInfo,
              () -> Duration.ofMillis(Long.MAX_VALUE / 3));
      hugeBaseScheduler.onRecovered(mockContext);
      // a rejected ask (count 6) last sent long ago, evaluated at a normal clock value
      stubPendingAsks(0L, createAsk(1L, 100L, "b", 6L));

      // when
      hugeBaseScheduler.run();

      // then the interval saturates at Long.MAX_VALUE (effectively infinite) rather than going
      // negative, so the ask is not yet due and is not re-sent — no storm
      verifyNoSend();
    }
  }
}
