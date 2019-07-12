/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATING;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.verification.VerificationWithTimeout;

public class StreamProcessorReprocessingTest {
  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  @Rule public StreamProcessorRule streamProcessorRule = new StreamProcessorRule();

  @Test
  public void shouldCallRecordProcessorLifecycle() {
    // given
    final long position = streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING, 1);
    final long secondPosition =
        streamProcessorRule.writeWorkflowInstanceEventWithSource(
            WorkflowInstanceIntent.ELEMENT_ACTIVATED, 1, position);
    waitUntil(
        () ->
            streamProcessorRule
                .events()
                .onlyWorkflowInstanceRecords()
                .withIntent(ELEMENT_ACTIVATED)
                .exists());

    // when
    final TypedRecordProcessor typedRecordProcessor = mock(TypedRecordProcessor.class);
    final StreamProcessor streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, state) ->
                processors
                    .onEvent(ValueType.WORKFLOW_INSTANCE, ELEMENT_ACTIVATING, typedRecordProcessor)
                    .onEvent(ValueType.WORKFLOW_INSTANCE, ELEMENT_ACTIVATED, typedRecordProcessor));

    verify(typedRecordProcessor, TIMEOUT.times(1))
        .processRecord(eq(secondPosition), any(), any(), any(), any());
    streamProcessor.closeAsync().join();

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor);
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(2)).onOpen(any());
    // reprocessing
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(1))
        .processRecord(eq(position), any(), any(), any(), any());
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(2)).onRecovered(any());
    // normal processing
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(1))
        .processRecord(eq(secondPosition), any(), any(), any(), any());
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(2)).onClose();

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldReprocessUntilLastSource() {
    // given
    final long firstEvent = streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING, 1);
    final long secondEvent = streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING, 1);
    final long lastSourceEvent =
        streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING, 1);
    final long normalProcessingPosition =
        streamProcessorRule.writeWorkflowInstanceEventWithSource(
            WorkflowInstanceIntent.ELEMENT_ACTIVATED, 1, lastSourceEvent);
    waitUntil(
        () ->
            streamProcessorRule
                .events()
                .onlyWorkflowInstanceRecords()
                .withIntent(ELEMENT_ACTIVATED)
                .exists());

    // when
    final TypedRecordProcessor typedRecordProcessor = mock(TypedRecordProcessor.class);
    final StreamProcessor streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, state) ->
                processors
                    .onEvent(ValueType.WORKFLOW_INSTANCE, ELEMENT_ACTIVATING, typedRecordProcessor)
                    .onEvent(ValueType.WORKFLOW_INSTANCE, ELEMENT_ACTIVATED, typedRecordProcessor));

    verify(typedRecordProcessor, TIMEOUT.times(1))
        .processRecord(eq(normalProcessingPosition), any(), any(), any(), any());
    streamProcessor.closeAsync().join();

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor);
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(2)).onOpen(any());
    // reprocessing
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(1))
        .processRecord(eq(firstEvent), any(), any(), any(), any());
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(1))
        .processRecord(eq(secondEvent), any(), any(), any(), any());
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(1))
        .processRecord(eq(lastSourceEvent), any(), any(), any(), any());
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(2)).onRecovered(any());
    // normal processing
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(1))
        .processRecord(eq(normalProcessingPosition), any(), any(), any(), any());
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(2)).onClose();

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldNotReprocessWithoutSourcePosition() {
    // given
    final long position = streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING, 1);
    final long secondPosition =
        streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATED, 1);
    waitUntil(
        () ->
            streamProcessorRule
                .events()
                .onlyWorkflowInstanceRecords()
                .withIntent(ELEMENT_ACTIVATED)
                .exists());

    // when
    final TypedRecordProcessor typedRecordProcessor = mock(TypedRecordProcessor.class);
    final StreamProcessor streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, state) ->
                processors
                    .onEvent(ValueType.WORKFLOW_INSTANCE, ELEMENT_ACTIVATING, typedRecordProcessor)
                    .onEvent(ValueType.WORKFLOW_INSTANCE, ELEMENT_ACTIVATED, typedRecordProcessor));

    verify(typedRecordProcessor, TIMEOUT.times(1))
        .processRecord(eq(secondPosition), any(), any(), any(), any());
    streamProcessor.closeAsync().join();

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor);
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(2)).onOpen(any());
    // no reprocessing
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(2)).onRecovered(any());
    // normal processing
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(1))
        .processRecord(eq(position), any(), any(), any(), any());
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(1))
        .processRecord(eq(secondPosition), any(), any(), any(), any());
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(2)).onClose();

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldRetryProcessingRecordOnException() {
    // given
    final long firstPosition =
        streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING, 1);
    streamProcessorRule.writeWorkflowInstanceEventWithSource(ELEMENT_ACTIVATED, 1, firstPosition);

    waitUntil(
        () ->
            streamProcessorRule
                .events()
                .onlyWorkflowInstanceRecords()
                .withIntent(ELEMENT_ACTIVATED)
                .exists());

    // when
    final TypedRecordProcessor<?> typedRecordProcessor = mock(TypedRecordProcessor.class);
    final AtomicInteger count = new AtomicInteger(0);
    doAnswer(
            (invocationOnMock -> {
              if (count.getAndIncrement() == 0) {
                throw new RuntimeException("recoverable");
              }
              return null;
            }))
        .when(typedRecordProcessor)
        .processRecord(anyLong(), any(), any(), any(), any());
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE, ELEMENT_ACTIVATING, typedRecordProcessor));

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor);
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(1)).onOpen(any());
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(2))
        .processRecord(eq(firstPosition), any(), any(), any(), any());
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(1)).onRecovered(any());

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldIgnoreRecordWhenNoProcessorExistForThisType() {
    // given
    final long firstPosition = streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATED, 1);
    final long secondPosition =
        streamProcessorRule.writeWorkflowInstanceEventWithSource(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING, 1, firstPosition);

    // when
    final TypedRecordProcessor<?> typedRecordProcessor = mock(TypedRecordProcessor.class);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE, ELEMENT_ACTIVATING, typedRecordProcessor));

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor);
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(1)).onOpen(any());
    inOrder
        .verify(typedRecordProcessor, never())
        .processRecord(eq(firstPosition), any(), any(), any(), any());
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(1)).onRecovered(any());
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(1))
        .processRecord(eq(secondPosition), any(), any(), any(), any());

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldNotWriteFollowUpEvent() throws Exception {
    // given
    final long firstPosition =
        streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING, 1);
    streamProcessorRule.writeWorkflowInstanceEventWithSource(ELEMENT_ACTIVATED, 1, firstPosition);

    waitUntil(
        () ->
            streamProcessorRule
                .events()
                .onlyWorkflowInstanceRecords()
                .withIntent(ELEMENT_ACTIVATED)
                .exists());

    // when
    final CountDownLatch processLatch = new CountDownLatch(1);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors
                .onEvent(
                    ValueType.WORKFLOW_INSTANCE,
                    ELEMENT_ACTIVATING,
                    new TypedRecordProcessor<UnifiedRecordValue>() {
                      @Override
                      public void processRecord(
                          long position,
                          TypedRecord<UnifiedRecordValue> record,
                          TypedResponseWriter responseWriter,
                          TypedStreamWriter streamWriter,
                          Consumer<SideEffectProducer> sideEffect) {
                        streamWriter.appendFollowUpEvent(
                            record.getKey(),
                            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
                            record.getValue());
                      }
                    })
                .onEvent(
                    ValueType.WORKFLOW_INSTANCE,
                    ELEMENT_ACTIVATED,
                    new TypedRecordProcessor<UnifiedRecordValue>() {
                      @Override
                      public void processRecord(
                          long position,
                          TypedRecord<UnifiedRecordValue> record,
                          TypedResponseWriter responseWriter,
                          TypedStreamWriter streamWriter,
                          Consumer<SideEffectProducer> sideEffect) {
                        processLatch.countDown();
                      }
                    }));

    // then
    processLatch.await();

    final long eventCount =
        streamProcessorRule
            .events()
            .onlyWorkflowInstanceRecords()
            .withIntent(ELEMENT_ACTIVATED)
            .count();
    assertThat(eventCount).isEqualTo(1);
  }

  @Test
  public void shouldStartFromLastSnapshotPosition() throws Exception {
    // given
    final CountDownLatch processingLatch = new CountDownLatch(2);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE,
                ELEMENT_ACTIVATING,
                new TypedRecordProcessor<UnifiedRecordValue>() {
                  @Override
                  public void processRecord(
                      long position,
                      TypedRecord<UnifiedRecordValue> record,
                      TypedResponseWriter responseWriter,
                      TypedStreamWriter streamWriter,
                      Consumer<SideEffectProducer> sideEffect) {
                    processingLatch.countDown();
                  }
                }));

    streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING);
    streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING);
    processingLatch.await();
    streamProcessorRule.closeStreamProcessor();

    // when
    final List<Long> processedPositions = new ArrayList<>();
    final CountDownLatch newProcessLatch = new CountDownLatch(1);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE,
                ELEMENT_ACTIVATING,
                new TypedRecordProcessor<UnifiedRecordValue>() {
                  @Override
                  public void processRecord(
                      long position,
                      TypedRecord<UnifiedRecordValue> record,
                      TypedResponseWriter responseWriter,
                      TypedStreamWriter streamWriter,
                      Consumer<SideEffectProducer> sideEffect) {
                    processedPositions.add(position);
                    newProcessLatch.countDown();
                  }
                }));
    final long position = streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING);

    // then
    newProcessLatch.await();

    assertThat(processedPositions).containsExactly(position);
  }

  @Test
  public void shouldNotReprocessEventAtSnapshotPosition() throws Exception {
    // given
    final CountDownLatch processingLatch = new CountDownLatch(2);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE,
                ELEMENT_ACTIVATING,
                new TypedRecordProcessor<UnifiedRecordValue>() {
                  @Override
                  public void processRecord(
                      long position,
                      TypedRecord<UnifiedRecordValue> record,
                      TypedResponseWriter responseWriter,
                      TypedStreamWriter streamWriter,
                      Consumer<SideEffectProducer> sideEffect) {
                    processingLatch.countDown();
                  }
                }));

    streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING, 1);
    final long snapshotPosition =
        streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING, 1);
    processingLatch.await();
    streamProcessorRule.closeStreamProcessor(); // enforce snapshot
    final long lastSourceEvent =
        streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING, 1);
    final long lastEvent =
        streamProcessorRule.writeWorkflowInstanceEventWithSource(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING, 1, lastSourceEvent);

    // when
    final List<Long> processedPositions = new ArrayList<>();
    final CountDownLatch newProcessLatch = new CountDownLatch(2);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE,
                ELEMENT_ACTIVATING,
                new TypedRecordProcessor<UnifiedRecordValue>() {
                  @Override
                  public void processRecord(
                      long position,
                      TypedRecord<UnifiedRecordValue> record,
                      TypedResponseWriter responseWriter,
                      TypedStreamWriter streamWriter,
                      Consumer<SideEffectProducer> sideEffect) {
                    processedPositions.add(position);
                    newProcessLatch.countDown();
                  }
                }));

    // then
    newProcessLatch.await();

    assertThat(processedPositions).doesNotContain(snapshotPosition);
    assertThat(processedPositions).containsExactly(lastSourceEvent, lastEvent);
  }
}
