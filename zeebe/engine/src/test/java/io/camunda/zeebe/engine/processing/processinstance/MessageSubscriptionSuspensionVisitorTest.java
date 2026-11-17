/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.SideEffectProducer;
import java.time.Instant;
import java.time.InstantSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class MessageSubscriptionSuspensionVisitorTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long ELEMENT_KEY = 101L;
  private static final int SUBSCRIPTION_PARTITION_ID = 2;

  private MutableProcessingState processingState;

  private MutableElementInstanceState elementInstanceState;
  private MutableProcessMessageSubscriptionState subscriptionState;
  private StateWriter stateWriter;
  private SubscriptionCommandSender subscriptionCommandSender;
  private TransientPendingSubscriptionState pendingSubscriptionState;
  private MessageSubscriptionSuspensionVisitor visitor;

  @BeforeEach
  void setUp() {
    elementInstanceState = processingState.getElementInstanceState();
    subscriptionState = processingState.getProcessMessageSubscriptionState();
    stateWriter = mock(StateWriter.class);
    subscriptionCommandSender = mock(SubscriptionCommandSender.class);
    pendingSubscriptionState = spy(new TransientPendingSubscriptionState());
    // run side effects right away, so the test can verify the close command
    final var sideEffectWriter = mock(SideEffectWriter.class);
    doAnswer(
            invocation -> {
              invocation.<SideEffectProducer>getArgument(0).flush();
              return null;
            })
        .when(sideEffectWriter)
        .appendSideEffect(any());
    visitor =
        new MessageSubscriptionSuspensionVisitor(
            stateWriter,
            sideEffectWriter,
            subscriptionState,
            subscriptionCommandSender,
            pendingSubscriptionState,
            InstantSource.fixed(Instant.EPOCH));
    elementInstance();
  }

  @Test
  void shouldCloseOpenedSubscriptionForSuspend() {
    // given
    openedSubscription(200L, "message", 300L);

    // when
    suspendElementInstance();

    // then
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(200L),
            eq(ProcessMessageSubscriptionIntent.DELETING),
            argThat(r -> ((ProcessMessageSubscriptionRecord) r).isClosedForSuspend()));
    verify(subscriptionCommandSender)
        .sendDirectCloseMessageSubscription(
            eq(SUBSCRIPTION_PARTITION_ID),
            eq(PROCESS_INSTANCE_KEY),
            eq(ELEMENT_KEY),
            anyLong(),
            eq(wrapString("message")),
            eq(TenantOwned.DEFAULT_TENANT_IDENTIFIER),
            eq(300L),
            anyInt());
    // the close is retried until it is acked
    verify(pendingSubscriptionState)
        .update(
            eq(
                new PendingSubscription(
                    ELEMENT_KEY, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER)),
            anyLong());
  }

  @Test
  void shouldCloseEveryOpenedSubscriptionOfElement() {
    // given - e.g. an event-based gateway or several message boundary events
    openedSubscription(200L, "first", 300L);
    openedSubscription(201L, "second", 301L);

    // when
    suspendElementInstance();

    // then
    verify(stateWriter)
        .appendFollowUpEvent(eq(200L), eq(ProcessMessageSubscriptionIntent.DELETING), any());
    verify(stateWriter)
        .appendFollowUpEvent(eq(201L), eq(ProcessMessageSubscriptionIntent.DELETING), any());
  }

  @Test
  void shouldCloseOpenedSubscriptionNextToOpeningOne() {
    // given - one subscription of the element is still opening, which must not hide the opened one
    openingSubscription(200L, "a-opening");
    openedSubscription(201L, "b-opened", 301L);

    // when
    suspendElementInstance();

    // then
    verify(stateWriter, never())
        .appendFollowUpEvent(eq(200L), eq(ProcessMessageSubscriptionIntent.DELETING), any());
    verify(stateWriter)
        .appendFollowUpEvent(eq(201L), eq(ProcessMessageSubscriptionIntent.DELETING), any());
  }

  @Test
  void shouldNotCloseOpeningSubscription() {
    // given - mid-handshake; the create ack closes it once it arrives
    openingSubscription(200L, "message");

    // when
    suspendElementInstance();

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(subscriptionCommandSender, never())
        .sendDirectCloseMessageSubscription(
            anyInt(), anyLong(), anyLong(), anyLong(), any(), any(), anyLong(), anyInt());
  }

  @Test
  void shouldNotCloseClosingSubscription() {
    // given - a restarted or overlapping walk reaches a subscription it already closed
    final var record = openedSubscription(200L, "message", 300L);
    subscriptionState.updateToClosingState(record.setClosedForSuspend(true));

    // when
    suspendElementInstance();

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(subscriptionCommandSender, never())
        .sendDirectCloseMessageSubscription(
            anyInt(), anyLong(), anyLong(), anyLong(), any(), any(), anyLong(), anyInt());
  }

  @Test
  void shouldNotCloseSubscriptionOfTerminatingElement() {
    // given - the termination removes the subscription, so it must not become a resume manifest
    openedSubscription(200L, "message", 300L);
    elementInstanceState.updateInstance(
        ELEMENT_KEY, ei -> ei.setState(ProcessInstanceIntent.ELEMENT_TERMINATING));

    // when
    suspendElementInstance();

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(subscriptionCommandSender, never())
        .sendDirectCloseMessageSubscription(
            anyInt(), anyLong(), anyLong(), anyLong(), any(), any(), anyLong(), anyInt());
  }

  @Test
  void shouldNotCloseAnythingForElementWithoutSubscription() {
    // when
    suspendElementInstance();

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
  }

  private void suspendElementInstance() {
    final var elementInstance = elementInstanceState.getInstance(ELEMENT_KEY);
    if (visitor.shouldVisit(elementInstance)) {
      visitor.visit(elementInstance);
    }
  }

  private void elementInstance() {
    elementInstanceState.newInstance(
        PROCESS_INSTANCE_KEY,
        processInstanceRecord(BpmnElementType.PROCESS),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        elementInstanceState.getInstance(PROCESS_INSTANCE_KEY),
        ELEMENT_KEY,
        processInstanceRecord(BpmnElementType.RECEIVE_TASK),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  private ProcessMessageSubscriptionRecord openingSubscription(
      final long key, final String messageName) {
    final var record =
        new ProcessMessageSubscriptionRecord()
            .setSubscriptionPartitionId(SUBSCRIPTION_PARTITION_ID)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setElementInstanceKey(ELEMENT_KEY)
            .setProcessDefinitionKey(1L)
            .setBpmnProcessId(wrapString("process"))
            .setElementId(wrapString("receive"))
            .setMessageName(wrapString(messageName))
            .setCorrelationKey(wrapString("key"))
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    subscriptionState.put(key, record);
    return record;
  }

  private ProcessMessageSubscriptionRecord openedSubscription(
      final long key, final String messageName, final long subscriptionKey) {
    final var record = openingSubscription(key, messageName).setSubscriptionKey(subscriptionKey);
    subscriptionState.updateToOpenedState(record);
    return record;
  }

  private static ProcessInstanceRecord processInstanceRecord(final BpmnElementType elementType) {
    return new ProcessInstanceRecord()
        .setElementId("process")
        .setBpmnProcessId("process")
        .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
        .setFlowScopeKey(-1L)
        .setVersion(1)
        .setProcessDefinitionKey(1L)
        .setBpmnElementType(elementType);
  }
}
