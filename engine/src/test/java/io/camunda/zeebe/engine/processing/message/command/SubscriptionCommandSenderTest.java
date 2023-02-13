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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SubscriptionCommandSenderTest {

  private static final long DIFFERENT_RECEIVER_PARTITION_KEY = Protocol.encodePartitionId(2, 1);
  private static final long SAME_RECEIVER_PARTITION_KEY = Protocol.encodePartitionId(1, 1);
  private static final int SENDER_PARTITION = 1;

  private static final long DEFAULT_ELEMENT_INSTANCE_KEY = 111;
  private static final DirectBuffer DEFAULT_MESSAGE_NAME = BufferUtil.wrapString("msg");

  private InterPartitionCommandSender mockInterPartitionCommandSender;
  private SubscriptionCommandSender subscriptionCommandSender;
  private ProcessingResultBuilder mockProcessingResultBuilder;

  @BeforeEach
  public void setup() {
    mockInterPartitionCommandSender = mock(InterPartitionCommandSender.class);
    subscriptionCommandSender =
        new SubscriptionCommandSender(SENDER_PARTITION, mockInterPartitionCommandSender);
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
}
