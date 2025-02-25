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
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
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
  private static final DirectBuffer DEFAULT_MESSAGE_NAME = BufferUtil.wrapString("msg");
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
    subscriptionCommandSender.setWriters(writers);
  }

  @Test
  public void shouldSentFollowUpCommandForCloseProcessMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.closeProcessMessageSubscription(
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
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
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_CORRELATION_KEY,
        true,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

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
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_CORRELATION_KEY,
        true,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

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
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_CORRELATION_KEY,
        true,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

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
        DEFAULT_MESSAGE_NAME,
        true,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

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
        DEFAULT_MESSAGE_NAME,
        true,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

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
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_NAME,
        TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any());
  }
}
