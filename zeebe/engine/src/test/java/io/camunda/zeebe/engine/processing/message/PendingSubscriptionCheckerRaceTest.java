/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.message.DbMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.DbProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

/**
 * Replays checker/processing interleavings on one thread: one state plays processing, a second one
 * (own context, shared transient state) plays the checker running on the async actor.
 */
@ExtendWith(ProcessingStateExtension.class)
final class PendingSubscriptionCheckerRaceTest {

  private static final long SUBSCRIPTION_TIMEOUT_MS = 10_000L;
  private static final long ELEMENT_INSTANCE_KEY = 1L;
  private static final String MESSAGE_NAME = "message";

  private ZeebeDb<ZbColumnFamilies> zeebeDb;

  private final AtomicLong now = new AtomicLong(1_000_000L);
  private final InstantSource clock = () -> Instant.ofEpochMilli(now.get());
  private final TransientPendingSubscriptionState transientState =
      new TransientPendingSubscriptionState();
  private final SubscriptionCommandSender commandSender = mock(SubscriptionCommandSender.class);
  private final ProcessingScheduleService scheduleService = mock(ProcessingScheduleService.class);
  private final ReadonlyStreamProcessorContext context = mock(ReadonlyStreamProcessorContext.class);

  @BeforeEach
  void setUp() {
    when(context.getScheduleService()).thenReturn(scheduleService);
    when(context.getClock()).thenReturn(StreamClock.uncontrolled(clock));
  }

  @Test
  void shouldNotCloseProcessSubscriptionThatIsAlreadyOpened() {
    // given
    final var processingState = processSubscriptionState();
    final var checker =
        new PendingProcessMessageSubscriptionCheckScheduler(
            commandSender, processSubscriptionState(), clock);
    final var record = processSubscriptionRecord();
    processingState.put(1L, record);
    processingState.updateToOpeningState(record);
    now.addAndGet(SUBSCRIPTION_TIMEOUT_MS + 1);

    // the open is acknowledged, but the side effect removing the entry hasn't run yet
    processingState.updateToOpenedState(record);

    // when
    checker.onRecovered(context);
    runProcessCheckerTick();

    // then
    verifyNoCloseSent();
  }

  @Test
  void shouldKeepSkippedEntryForTheNextTick() {
    // given
    final var processingState = processSubscriptionState();
    final var checker =
        new PendingProcessMessageSubscriptionCheckScheduler(
            commandSender, processSubscriptionState(), clock);
    final var record = processSubscriptionRecord();
    processingState.put(1L, record);
    processingState.updateToOpeningState(record);
    processingState.updateToOpenedState(record);
    now.addAndGet(SUBSCRIPTION_TIMEOUT_MS + 1);
    checker.onRecovered(context);
    runProcessCheckerTick();

    // when
    processingState.updateToClosingState(record);
    now.addAndGet(SUBSCRIPTION_TIMEOUT_MS + 1);
    runProcessCheckerTick();

    // then
    verify(commandSender, times(1))
        .sendDirectCloseMessageSubscription(
            anyInt(), anyLong(), anyLong(), anyLong(), any(), any(), anyLong(), anyInt());
  }

  @Test
  void shouldNotReAddProcessEntryRemovedWhileCheckerWasSending() {
    // given
    final var processingState = processSubscriptionState();
    final var checkerState = processSubscriptionState();
    final var checker =
        new PendingProcessMessageSubscriptionCheckScheduler(commandSender, checkerState, clock);
    final var record = processSubscriptionRecord();
    processingState.put(1L, record);
    processingState.updateToOpeningState(record);
    now.addAndGet(SUBSCRIPTION_TIMEOUT_MS + 1);
    doAnswer(
            invocation -> {
              processingState.updateToOpenedState(record);
              transientState.remove(pendingSubscription());
              return null;
            })
        .when(commandSender)
        .sendDirectOpenMessageSubscription(
            anyInt(),
            anyLong(),
            anyLong(),
            anyLong(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any(),
            any(),
            anyLong(),
            anyInt(),
            any());

    // when
    checker.onRecovered(context);
    runProcessCheckerTick();

    // then
    assertThat(pendingElementInstanceKeys(checkerState)).isEmpty();
  }

  @Test
  void shouldNotCloseAgainSubscriptionResumedWhileCheckerWasSending() {
    // given
    final var processingState = processSubscriptionState();
    final var checkerState = processSubscriptionState();
    final var checker =
        new PendingProcessMessageSubscriptionCheckScheduler(commandSender, checkerState, clock);
    final var record = processSubscriptionRecord();
    processingState.put(1L, record);
    processingState.updateToOpenedState(record);

    // closed for suspend, close pending
    processingState.updateToClosingState(record.setClosedForSuspend(true));
    transientState.update(pendingSubscription(), now.get());
    now.addAndGet(SUBSCRIPTION_TIMEOUT_MS + 1);

    // the close is acknowledged during the re-send and the row goes back to OPENED (resume)
    doAnswer(
            invocation -> {
              processingState.updateToOpenedState(record);
              transientState.remove(pendingSubscription());
              return null;
            })
        .when(commandSender)
        .sendDirectCloseMessageSubscription(
            anyInt(), anyLong(), anyLong(), anyLong(), any(), any(), anyLong(), anyInt());

    // when
    checker.onRecovered(context);
    runProcessCheckerTick();
    now.addAndGet(SUBSCRIPTION_TIMEOUT_MS + 1);
    runProcessCheckerTick();

    // then
    verify(commandSender, times(1))
        .sendDirectCloseMessageSubscription(
            anyInt(), anyLong(), anyLong(), anyLong(), any(), any(), anyLong(), anyInt());
    assertThat(pendingElementInstanceKeys(checkerState)).isEmpty();
  }

  @Test
  void shouldNotResendCorrelateForMessageSubscriptionCorrelatedWhileCheckerWasSending() {
    // given
    final var processingState = messageSubscriptionState();
    final var checker =
        new PendingMessageSubscriptionCheckScheduler(commandSender, messageSubscriptionState());
    final var record = messageSubscriptionRecord();
    processingState.put(1L, record);
    processingState.updateToCorrelatingState(record);
    now.addAndGet(SUBSCRIPTION_TIMEOUT_MS + 1);

    // the correlation is acknowledged while the checker re-sends the correlate, before onSent
    doAnswer(
            invocation -> {
              processingState.updateToCorrelatedState(
                  processingState.get(ELEMENT_INSTANCE_KEY, wrapString(MESSAGE_NAME)));
              return null;
            })
        .when(commandSender)
        .sendDirectCorrelateProcessMessageSubscription(
            anyLong(), anyLong(), anyLong(), any(), any(), anyLong(), any(), any(), any(),
            anyLong(), anyInt());

    // when
    checker.onRecovered(context);
    checker.run();
    now.addAndGet(SUBSCRIPTION_TIMEOUT_MS + 1);
    checker.run();

    // then
    verify(commandSender, times(1))
        .sendDirectCorrelateProcessMessageSubscription(
            anyLong(), anyLong(), anyLong(), any(), any(), anyLong(), any(), any(), any(),
            anyLong(), anyInt());
  }

  @Test
  void shouldSkipAndKeepEntryForNonCorrelatingMessageSubscription() {
    // given
    final var processingState = messageSubscriptionState();
    final var checkerState = messageSubscriptionState();
    final var checker = new PendingMessageSubscriptionCheckScheduler(commandSender, checkerState);
    processingState.put(1L, messageSubscriptionRecord());

    // a due entry for a row that isn't correlating (e.g. after a rollback)
    transientState.add(pendingSubscription(), now.get());
    now.addAndGet(SUBSCRIPTION_TIMEOUT_MS + 1);

    // when
    checker.onRecovered(context);
    checker.run();

    // then
    verify(commandSender, never())
        .sendDirectCorrelateProcessMessageSubscription(
            anyLong(), anyLong(), anyLong(), any(), any(), anyLong(), any(), any(), any(),
            anyLong(), anyInt());
    assertThat(pendingElementInstanceKeys(checkerState)).containsExactly(ELEMENT_INSTANCE_KEY);
  }

  @Test
  void shouldNotReAddMessageEntryRemovedWhileCheckerWasSending() {
    // given
    final var processingState = messageSubscriptionState();
    final var checkerState = messageSubscriptionState();
    final var checker = new PendingMessageSubscriptionCheckScheduler(commandSender, checkerState);
    final var record = messageSubscriptionRecord();
    processingState.put(1L, record);
    processingState.updateToCorrelatingState(record);
    now.addAndGet(SUBSCRIPTION_TIMEOUT_MS + 1);
    doAnswer(
            invocation -> {
              processingState.updateToCorrelatedState(
                  processingState.get(ELEMENT_INSTANCE_KEY, wrapString(MESSAGE_NAME)));
              return null;
            })
        .when(commandSender)
        .sendDirectCorrelateProcessMessageSubscription(
            anyLong(), anyLong(), anyLong(), any(), any(), anyLong(), any(), any(), any(),
            anyLong(), anyInt());

    // when
    checker.onRecovered(context);
    checker.run();

    // then
    assertThat(pendingElementInstanceKeys(checkerState)).isEmpty();
  }

  private DbProcessMessageSubscriptionState processSubscriptionState() {
    return new DbProcessMessageSubscriptionState(
        zeebeDb, zeebeDb.createContext(), transientState, clock);
  }

  private DbMessageSubscriptionState messageSubscriptionState() {
    return new DbMessageSubscriptionState(zeebeDb, zeebeDb.createContext(), transientState, clock);
  }

  private void runProcessCheckerTick() {
    final var captor = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduleService, atLeastOnce()).runAt(anyLong(), captor.capture());
    captor.getValue().run();
  }

  private void verifyNoCloseSent() {
    verify(commandSender, never())
        .sendDirectCloseMessageSubscription(
            anyInt(), anyLong(), anyLong(), anyLong(), any(), any(), anyLong(), anyInt());
  }

  private static List<Long> pendingElementInstanceKeys(
      final DbProcessMessageSubscriptionState state) {
    final List<Long> keys = new ArrayList<>();
    state.visitPending(
        Long.MAX_VALUE, subscription -> keys.add(subscription.getRecord().getElementInstanceKey()));
    return keys;
  }

  private static List<Long> pendingElementInstanceKeys(final DbMessageSubscriptionState state) {
    final List<Long> keys = new ArrayList<>();
    state.visitPending(
        Long.MAX_VALUE, subscription -> keys.add(subscription.getRecord().getElementInstanceKey()));
    return keys;
  }

  private static PendingSubscription pendingSubscription() {
    return new PendingSubscription(
        ELEMENT_INSTANCE_KEY, MESSAGE_NAME, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private static ProcessMessageSubscriptionRecord processSubscriptionRecord() {
    return new ProcessMessageSubscriptionRecord()
        .setProcessInstanceKey(2L)
        .setElementInstanceKey(ELEMENT_INSTANCE_KEY)
        .setProcessDefinitionKey(3L)
        .setBpmnProcessId(wrapString("process"))
        .setElementId(wrapString("catch"))
        .setMessageName(wrapString(MESSAGE_NAME))
        .setCorrelationKey(wrapString("correlation"))
        .setInterrupting(true)
        .setSubscriptionPartitionId(1);
  }

  private static MessageSubscriptionRecord messageSubscriptionRecord() {
    return new MessageSubscriptionRecord()
        .setProcessInstanceKey(2L)
        .setElementInstanceKey(ELEMENT_INSTANCE_KEY)
        .setProcessDefinitionKey(3L)
        .setBpmnProcessId(wrapString("process"))
        .setMessageName(wrapString(MESSAGE_NAME))
        .setCorrelationKey(wrapString("correlation"))
        .setMessageKey(4L)
        .setInterrupting(false);
  }
}
