/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.Protocol;
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
  private InterPartitionCommandSender mockInterPartitionCommandSender;
  private SubscriptionCommandSender subscriptionCommandSender;
  private ProcessingResultBuilder mockProcessingResultBuilder;

  @BeforeEach
  public void setup() {
    mockInterPartitionCommandSender = mock(InterPartitionCommandSender.class);
    subscriptionCommandSender =
        new SubscriptionCommandSender(SAME_PARTITION, mockInterPartitionCommandSender);
    mockProcessingResultBuilder = mock(ProcessingResultBuilder.class);
    final var writers =
        new Writers(() -> mockProcessingResultBuilder, (key, intent, recordValue) -> {});
    subscriptionCommandSender.setWriters(writers);
  }

  @Test
  public void shouldSentFollowUpCommandForCloseProcessMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.closeProcessMessageSubscription(
        DIFFERENT_RECEIVER_PARTITION_KEY, DEFAULT_ELEMENT_INSTANCE_KEY, DEFAULT_MESSAGE_NAME);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never())
        .appendRecord(anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  public void shouldWriteFollowUpCommandForCloseProcessMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.closeProcessMessageSubscription(
        SAME_RECEIVER_PARTITION_KEY, DEFAULT_ELEMENT_INSTANCE_KEY, DEFAULT_MESSAGE_NAME);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any(), any(), any(), any());
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
        DEFAULT_CORRELATION_KEY);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never())
        .appendRecord(anyLong(), any(), any(), any(), any(), any());
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
        DEFAULT_CORRELATION_KEY);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  public void shouldSentFollowUpCommandForCloseMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.closeMessageSubscription(
        DIFFERENT_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_MESSAGE_NAME);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never())
        .appendRecord(anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  public void shouldWriteFollowUpCommandForCloseMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.closeMessageSubscription(
        SAME_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_MESSAGE_NAME);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  public void shouldSendDirectCloseMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.sendDirectCloseMessageSubscription(
        DIFFERENT_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_MESSAGE_NAME);

    // then
    verify(mockInterPartitionCommandSender)
        .sendCommand(
            eq(DIFFERENT_PARTITION),
            eq(ValueType.MESSAGE_SUBSCRIPTION),
            eq(MessageSubscriptionIntent.DELETE),
            any());
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never())
        .appendRecord(anyLong(), any(), any(), any(), any(), any());
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
        true);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never())
        .appendRecord(anyLong(), any(), any(), any(), any(), any());
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
        true);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  public void shouldSentFollowUpCommandForOpenProcessMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.openProcessMessageSubscription(
        DIFFERENT_PARTITION, DIFFERENT_RECEIVER_PARTITION_KEY, DEFAULT_MESSAGE_NAME, true);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never())
        .appendRecord(anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  public void shouldWriteFollowUpCommandForOpenProcessMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.openProcessMessageSubscription(
        SAME_RECEIVER_PARTITION_KEY, DIFFERENT_RECEIVER_PARTITION_KEY, DEFAULT_MESSAGE_NAME, true);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  public void shouldSentFollowUpCommandForRejectCorrelateMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.rejectCorrelateMessageSubscription(
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_KEY,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_CORRELATION_KEY);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never())
        .appendRecord(anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  public void shouldWriteFollowUpCommandForRejectCorrelateMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.rejectCorrelateMessageSubscription(
        SAME_RECEIVER_PARTITION_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_KEY,
        DEFAULT_MESSAGE_NAME,
        DEFAULT_CORRELATION_KEY);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  public void shouldSentFollowUpCommandForCorrelateMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.correlateMessageSubscription(
        DIFFERENT_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_NAME);

    // then
    verify(mockProcessingResultBuilder).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder, never())
        .appendRecord(anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  public void shouldWriteFollowUpCommandForCorrelateMessageSubscription() {
    // given

    // when
    subscriptionCommandSender.correlateMessageSubscription(
        SAME_PARTITION,
        DIFFERENT_RECEIVER_PARTITION_KEY,
        DEFAULT_ELEMENT_INSTANCE_KEY,
        DEFAULT_PROCESS_ID,
        DEFAULT_MESSAGE_NAME);

    // then
    verify(mockProcessingResultBuilder, never()).appendPostCommitTask(any());
    verify(mockProcessingResultBuilder).appendRecord(anyLong(), any(), any(), any(), any(), any());
  }
}
