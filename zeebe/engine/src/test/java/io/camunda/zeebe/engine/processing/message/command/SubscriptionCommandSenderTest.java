/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.AtomicKeyGenerator;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SubscriptionCommandSenderTest {

  public static final DirectBuffer DEFAULT_PROCESS_ID = BufferUtil.wrapString("process");
  public static final int DEFAULT_MESSAGE_KEY = 123;
  public static final long DEFAULT_MESSAGE_DEADLINE = 4567L;
  public static final UnsafeBuffer DEFAULT_VARIABLES = new UnsafeBuffer();
  public static final DirectBuffer DEFAULT_CORRELATION_KEY =
      BufferUtil.wrapString("correlationKey");
  private static final int SAME_PARTITION = 1;
  private static final int DIFFERENT_PARTITION = 2;
  private static final long DIFFERENT_RECEIVER_PARTITION_KEY =
      Protocol.encodePartitionId(DIFFERENT_PARTITION, 1);
  private static final long SAME_RECEIVER_PARTITION_KEY =
      Protocol.encodePartitionId(SAME_PARTITION, 1);
  private static final long DEFAULT_ELEMENT_INSTANCE_KEY = 111;
  private static final long DEFAULT_PROCESS_DEFINITION_KEY = 222;
  private static final DirectBuffer DEFAULT_MESSAGE_NAME = BufferUtil.wrapString("msg");
  private static final DirectBuffer DEFAULT_BUSINESS_ID = BufferUtil.wrapString("");
  private static final DirectBuffer DEFAULT_ELEMENT_ID = BufferUtil.wrapString("catch-1");
  private static final long DEFAULT_ROOT_PROCESS_INSTANCE_KEY = 100L;
  private static final BpmnElementType DEFAULT_ELEMENT_TYPE = BpmnElementType.RECEIVE_TASK;
  private static final DirectBuffer DEFAULT_START_EVENT_ID = BufferUtil.wrapString("start");
  private static final long DEFAULT_MESSAGE_START_SUBSCRIPTION_KEY = 333;
  private static final String DEFAULT_TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  private InterPartitionCommandSender mockInterPartitionCommandSender;
  private SubscriptionCommandSender subscriptionCommandSender;
  private ProcessingResultBuilder mockProcessingResultBuilder;

  @BeforeEach
  public void setup() {
    mockInterPartitionCommandSender = mock(InterPartitionCommandSender.class);
    subscriptionCommandSender =
        new SubscriptionCommandSender(SAME_PARTITION, mockInterPartitionCommandSender);
    mockProcessingResultBuilder = mock(ProcessingResultBuilder.class);
    final var mockEventAppliers = mock(EventAppliers.class);
    final var writers = new Writers(() -> mockProcessingResultBuilder, mockEventAppliers);
    writers.setKeyValidator(new AtomicKeyGenerator(SAME_PARTITION));
    subscriptionCommandSender.setWriters(writers);
  }

  @Test
  public void shouldSentFollowUpCommandForCloseProcessMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.closeProcessMessageSubscription(
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_MESSAGE_NAME,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldWriteFollowUpCommandForCloseProcessMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.closeProcessMessageSubscription(
        SAME_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_MESSAGE_NAME,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldSentFollowUpCommandForCorrelateProcessMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.correlateProcessMessageSubscription(
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_MESSAGE_KEY,
        DEFAULT_VARIABLES,
        DEFAULT_CORRELATION_KEY,
        DEFAULT_TENANT);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldWriteFollowUpCommandForCorrelateProcessMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.correlateProcessMessageSubscription(
        SAME_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_MESSAGE_KEY,
        DEFAULT_VARIABLES,
        DEFAULT_CORRELATION_KEY,
        DEFAULT_TENANT);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldSendDirectCorrelateProcessMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.sendDirectCorrelateProcessMessageSubscription(
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_MESSAGE_KEY,
        DEFAULT_VARIABLES,
        DEFAULT_CORRELATION_KEY,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    verify(mockInterPartitionCommandSender)
        .sendCommand(
            eq(DIFFERENT_PARTITION),
            eq(ValueType.PROCESS_MESSAGE_SUBSCRIPTION),
            eq(ProcessMessageSubscriptionIntent.CORRELATE),
            any());
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldSentFollowUpCommandForCloseMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.closeMessageSubscription(
        DIFFERENT_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_MESSAGE_NAME,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldWriteFollowUpCommandForCloseMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.closeMessageSubscription(
        SAME_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_MESSAGE_NAME,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldSendDirectCloseMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.sendDirectCloseMessageSubscription(
        DIFFERENT_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_MESSAGE_NAME,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    verify(mockInterPartitionCommandSender)
        .sendCommand(
            eq(DIFFERENT_PARTITION),
            eq(ValueType.MESSAGE_SUBSCRIPTION),
            eq(MessageSubscriptionIntent.DELETE),
            any());
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldSentFollowUpCommandForOpenMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.openMessageSubscription(
        DIFFERENT_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_CORRELATION_KEY,
        true,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        DEFAULT_BUSINESS_ID,
        DEFAULT_ELEMENT_ID,
        DEFAULT_ROOT_PROCESS_INSTANCE_KEY,
        DEFAULT_ELEMENT_TYPE);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldWriteFollowUpCommandForOpenMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.openMessageSubscription(
        SAME_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_CORRELATION_KEY,
        true,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        DEFAULT_BUSINESS_ID,
        DEFAULT_ELEMENT_ID,
        DEFAULT_ROOT_PROCESS_INSTANCE_KEY,
        DEFAULT_ELEMENT_TYPE);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldSendDirectOpenMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.sendDirectOpenMessageSubscription(
        DIFFERENT_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_CORRELATION_KEY,
        true,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        DEFAULT_BUSINESS_ID,
        DEFAULT_ELEMENT_ID,
        DEFAULT_ROOT_PROCESS_INSTANCE_KEY,
        DEFAULT_ELEMENT_TYPE);

    // then
    verify(mockInterPartitionCommandSender)
        .sendCommand(
            eq(DIFFERENT_PARTITION),
            eq(ValueType.MESSAGE_SUBSCRIPTION),
            eq(MessageSubscriptionIntent.CREATE),
            any());
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldSentFollowUpCommandForOpenProcessMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.openProcessMessageSubscription(
        DIFFERENT_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_MESSAGE_NAME,
        true,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        DEFAULT_BUSINESS_ID);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldWriteFollowUpCommandForOpenProcessMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.openProcessMessageSubscription(
        SAME_RECEIVER_PARTITION_KEY,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_MESSAGE_NAME,
        true,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        DEFAULT_BUSINESS_ID);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldSentFollowUpCommandForRejectCorrelateMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.rejectCorrelateMessageSubscription(
        DIFFERENT_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_KEY,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_CORRELATION_KEY,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldWriteFollowUpCommandForRejectCorrelateMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.rejectCorrelateMessageSubscription(
        (int) SAME_RECEIVER_PARTITION_KEY,
        SAME_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_KEY,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_CORRELATION_KEY,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldSentFollowUpCommandForCorrelateMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.correlateMessageSubscription(
        -1,
        DIFFERENT_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_NAME,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldWriteFollowUpCommandForCorrelateMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.correlateMessageSubscription(
        -1,
        SAME_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_NAME,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldSentFollowUpCommandForStartProcessInstanceRequest() {
    // given

    // when
    subscriptionCommandSender.sendStartProcessInstanceRequest(
        DIFFERENT_PARTITION,
        DEFAULT_MESSAGE_KEY,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_CORRELATION_KEY,
        DEFAULT_BUSINESS_ID,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_START_EVENT_ID,
        DEFAULT_MESSAGE_START_SUBSCRIPTION_KEY,
        DEFAULT_VARIABLES,
        DEFAULT_MESSAGE_DEADLINE,
        DEFAULT_TENANT);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldWriteFollowUpCommandForStartProcessInstanceRequest() {
    // given

    // when
    subscriptionCommandSender.sendStartProcessInstanceRequest(
        SAME_PARTITION,
        DEFAULT_MESSAGE_KEY,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_CORRELATION_KEY,
        DEFAULT_BUSINESS_ID,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_START_EVENT_ID,
        DEFAULT_MESSAGE_START_SUBSCRIPTION_KEY,
        DEFAULT_VARIABLES,
        DEFAULT_MESSAGE_DEADLINE,
        DEFAULT_TENANT);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldSendDirectStartProcessInstanceRequest() {
    // given

    // when
    subscriptionCommandSender.sendDirectStartProcessInstanceRequest(
        DIFFERENT_PARTITION,
        DEFAULT_MESSAGE_KEY,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_CORRELATION_KEY,
        DEFAULT_BUSINESS_ID,
        DEFAULT_PROCESS_DEFINITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_START_EVENT_ID,
        DEFAULT_MESSAGE_START_SUBSCRIPTION_KEY,
        DEFAULT_VARIABLES,
        DEFAULT_MESSAGE_DEADLINE,
        DEFAULT_TENANT);

    // then
    verify(mockInterPartitionCommandSender)
        .sendCommand(
            eq(DIFFERENT_PARTITION),
            eq(ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST),
            eq(MessageStartProcessInstanceRequestIntent.REQUEST),
            any());
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldSentFollowUpCommandForStartProcessInstanceStartedReply() {
    // given a request whose messageKey encodes a different source partition than the sender
    final var request = requestFromSourcePartition(DIFFERENT_PARTITION);
    final long createdPiKey = 999L;

    // when the P_B side replies STARTED
    subscriptionCommandSender.sendStartProcessInstanceStarted(request, createdPiKey);

    // then it is dispatched cross-partition back to P_K (the source partition)
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldWriteFollowUpCommandForStartProcessInstanceStartedReply() {
    // given a request whose messageKey encodes the sender's own partition
    final var request = requestFromSourcePartition(SAME_PARTITION);

    // when the (degenerate) reply targets the same partition
    subscriptionCommandSender.sendStartProcessInstanceStarted(request, 999L);

    // then it is written as a local follow-up command instead of being sent
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldSentFollowUpCommandForStartProcessInstanceUniquenessRejectedReply() {
    // given
    final var request = requestFromSourcePartition(DIFFERENT_PARTITION);

    // when
    subscriptionCommandSender.sendStartProcessInstanceUniquenessRejected(request);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  @Test
  public void shouldSentFollowUpCommandForStartProcessInstanceNoSubscriptionRejectedReply() {
    // given
    final var request = requestFromSourcePartition(DIFFERENT_PARTITION);

    // when
    subscriptionCommandSender.sendStartProcessInstanceNoSubscriptionRejected(request);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never()).appendRecord(anyLong(), any(), any());
  }

  private static MessageStartProcessInstanceRequestRecord requestFromSourcePartition(
      final int sourcePartition) {
    return new MessageStartProcessInstanceRequestRecord()
        .setMessageKey(Protocol.encodePartitionId(sourcePartition, 7))
        .setMessageName(DEFAULT_MESSAGE_NAME)
        .setCorrelationKey(DEFAULT_CORRELATION_KEY)
        .setBusinessId(DEFAULT_BUSINESS_ID)
        .setProcessDefinitionKey(DEFAULT_PROCESS_DEFINITION_KEY)
        .setBpmnProcessId(DEFAULT_PROCESS_ID)
        .setStartEventId(DEFAULT_START_EVENT_ID)
        .setMessageStartEventSubscriptionKey(DEFAULT_MESSAGE_START_SUBSCRIPTION_KEY)
        .setVariables(DEFAULT_VARIABLES)
        .setTenantId(DEFAULT_TENANT);
  }
}
