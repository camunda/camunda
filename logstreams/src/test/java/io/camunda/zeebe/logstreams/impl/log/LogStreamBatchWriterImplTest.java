/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.dispatcher.ClaimedFragmentBatch;
import io.camunda.zeebe.dispatcher.Dispatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

final class LogStreamBatchWriterImplTest {
  private final Dispatcher dispatcher = mock(Dispatcher.class);
  private final LogStreamBatchWriterImpl writer = new LogStreamBatchWriterImpl(1, dispatcher);

  /**
   * This test asserts that {@link LogStreamBatchWriterImpl#canWriteAdditionalEvent(int)} computes
   * the correct batch length before passing it on to the dispatcher to check if it's acceptable. It
   * also verifies that the same length is passed to the dispatcher when actually writing, ensuring
   * both methods remain consistent with each other.
   */
  @Test
  void canWriteAdditionalEvent() {
    // given
    final DirectBuffer value = BufferUtil.wrapString("foo");
    final DirectBuffer metadata = BufferUtil.wrapString("bar");
    final var expectedFragmentCount = 2;
    final var expectedFramingLength =
        expectedFragmentCount * LogEntryDescriptor.HEADER_BLOCK_LENGTH;
    final var expectedEventsLength =
        expectedFragmentCount * (value.capacity() + metadata.capacity());
    final var expectedBatchLength = expectedEventsLength + expectedFramingLength;

    // when
    when(dispatcher.canClaimFragmentBatch(anyInt(), anyInt())).thenReturn(false);
    when(dispatcher.canClaimFragmentBatch(expectedFragmentCount, expectedBatchLength))
        .thenReturn(true);
    when(dispatcher.claimFragmentBatch(any(), anyInt(), anyInt()))
        .then(this::mockClaimFragmentBatch);
    writer.event().value(value).metadata(metadata).done();
    final boolean canWriteAdditionalEvent =
        writer.canWriteAdditionalEvent(value.capacity() + metadata.capacity());
    writer.event().value(value).metadata(metadata).done().tryWrite();

    // then
    verify(dispatcher, times(1)).canClaimFragmentBatch(expectedFragmentCount, expectedBatchLength);
    verify(dispatcher, times(1))
        .claimFragmentBatch(
            any(ClaimedFragmentBatch.class), eq(expectedFragmentCount), eq(expectedBatchLength));
    assertThat(canWriteAdditionalEvent).isTrue();
    verifyNoMoreInteractions(dispatcher);
  }

  /**
   * This test asserts that {@link LogStreamBatchWriterImpl#canWriteAdditionalEvent(int, int)}
   * computes the correct batch length before passing it on to the dispatcher to check if it's
   * acceptable. It also verifies that the same length is passed to the dispatcher when actually
   * writing, ensuring both methods remain consistent with each other.
   */
  @Test
  void canWriteAdditionalEventWithEventCount() {
    // given
    final DirectBuffer value = BufferUtil.wrapString("foo");
    final DirectBuffer metadata = BufferUtil.wrapString("bar");

    final var expectedFragmentCount = 4;
    final var expectedFramingLength =
        expectedFragmentCount * LogEntryDescriptor.HEADER_BLOCK_LENGTH;
    final var expectedEventsLength =
        expectedFragmentCount * (value.capacity() + metadata.capacity());
    final var expectedBatchLength = expectedEventsLength + expectedFramingLength;

    // when
    when(dispatcher.canClaimFragmentBatch(anyInt(), anyInt())).thenReturn(false);
    when(dispatcher.canClaimFragmentBatch(expectedFragmentCount, expectedBatchLength))
        .thenReturn(true);
    when(dispatcher.claimFragmentBatch(any(), anyInt(), anyInt()))
        .then(this::mockClaimFragmentBatch);

    final boolean canWriteAdditionalEvent =
        writer.canWriteAdditionalEvent(expectedFragmentCount, expectedEventsLength);

    for (int i = 0; i < expectedFragmentCount; i++) {
      writer.event().value(value).metadata(metadata).done();
    }
    writer.tryWrite();

    // then
    verify(dispatcher, times(1)).canClaimFragmentBatch(expectedFragmentCount, expectedBatchLength);
    verify(dispatcher, times(1))
        .claimFragmentBatch(
            any(ClaimedFragmentBatch.class), eq(expectedFragmentCount), eq(expectedBatchLength));
    assertThat(canWriteAdditionalEvent).isTrue();
    verifyNoMoreInteractions(dispatcher);
  }

  private long mockClaimFragmentBatch(final InvocationOnMock i) {
    final var batch = i.getArgument(0, ClaimedFragmentBatch.class);
    final var writeBuffer = new UnsafeBuffer(new ExpandableArrayBuffer(1024));
    batch.wrap(writeBuffer, 1, 0, writeBuffer.capacity(), () -> {});
    return 1L;
  }
}
