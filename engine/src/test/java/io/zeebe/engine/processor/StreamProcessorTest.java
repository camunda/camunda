/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import static io.zeebe.engine.processor.TypedRecordProcessors.processors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;

import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.exception.RecoverableException;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.verification.VerificationWithTimeout;

public final class StreamProcessorTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  @Rule public final StreamProcessorRule streamProcessorRule = new StreamProcessorRule();
  private ActorControl processingContextActor;

  @Test
  public void shouldCallStreamProcessorLifecycle() throws Exception {
    // given
    final StreamProcessorLifecycleAware lifecycleAware = mock(StreamProcessorLifecycleAware.class);
    final CountDownLatch recoveredLatch = new CountDownLatch(1);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors
                .withListener(lifecycleAware)
                .withListener(
                    new StreamProcessorLifecycleAware() {
                      @Override
                      public void onRecovered(final ReadonlyProcessingContext context) {
                        recoveredLatch.countDown();
                      }
                    }));

    // when
    recoveredLatch.await();
    streamProcessorRule.closeStreamProcessor();

    // then
    final InOrder inOrder = inOrder(lifecycleAware);
    inOrder.verify(lifecycleAware, times(1)).onRecovered(any());
    inOrder.verify(lifecycleAware, times(1)).onClose();

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldCallStreamProcessorLifecycleOnFail() throws InterruptedException {
    // given
    final CountDownLatch failedLatch = new CountDownLatch(1);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.withListener(
                new StreamProcessorLifecycleAware() {
                  @Override
                  public void onRecovered(final ReadonlyProcessingContext context) {
                    throw new RuntimeException("force fail");
                  }

                  @Override
                  public void onFailed() {
                    failedLatch.countDown();
                  }
                }));

    // then
    assertThat(failedLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue();
  }

  @Test
  public void shouldCallRecordProcessorLifecycle() throws Exception {
    // given
    final var typedRecordProcessor = mock(TypedRecordProcessor.class);
    final var recoveredLatch = new CountDownLatch(1);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors
                .onEvent(ValueType.DEPLOYMENT, DeploymentIntent.CREATE, typedRecordProcessor)
                .withListener(
                    new StreamProcessorLifecycleAware() {
                      @Override
                      public void onRecovered(final ReadonlyProcessingContext context) {
                        recoveredLatch.countDown();
                      }
                    }));

    // when
    recoveredLatch.await();
    streamProcessorRule.closeStreamProcessor();

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor);
    inOrder.verify(typedRecordProcessor, times(1)).onRecovered(any());
    inOrder.verify(typedRecordProcessor, times(1)).onClose();

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldProcessRecord() {
    // given
    final TypedRecordProcessor<?> typedRecordProcessor = mock(TypedRecordProcessor.class);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                typedRecordProcessor));

    // when
    final long position =
        streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor);
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(1)).onRecovered(any());
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(1))
        .processRecord(eq(position), any(), any(), any(), any());

    inOrder.verifyNoMoreInteractions();

    Assertions.assertThat(
            streamProcessorRule.getZeebeState().getLastSuccessfulProcessedRecordPosition())
        .isEqualTo(position);
  }

  @Test
  public void shouldRetryProcessingRecordOnRecoverableException() {
    // given
    final TypedRecordProcessor<?> typedRecordProcessor = mock(TypedRecordProcessor.class);
    final AtomicInteger count = new AtomicInteger(0);
    doAnswer(
            (invocationOnMock -> {
              if (count.getAndIncrement() == 0) {
                throw new RecoverableException("recoverable");
              }
              return null;
            }))
        .when(typedRecordProcessor)
        .processRecord(anyLong(), any(), any(), any(), any());
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                typedRecordProcessor));

    // when
    final long position =
        streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor);
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(1)).onRecovered(any());
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(2))
        .processRecord(eq(position), any(), any(), any(), any());

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldIgnoreRecordWhenNoProcessorExistForThisType() {
    // given
    final TypedRecordProcessor<?> typedRecordProcessor = mock(TypedRecordProcessor.class);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                typedRecordProcessor));

    // when
    final long firstPosition =
        streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    final long secondPosition =
        streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // then
    final InOrder inOrder = inOrder(typedRecordProcessor);
    inOrder.verify(typedRecordProcessor, TIMEOUT.times(1)).onRecovered(any());
    inOrder
        .verify(typedRecordProcessor, TIMEOUT.times(1))
        .processRecord(eq(firstPosition), any(), any(), any(), any());
    inOrder
        .verify(typedRecordProcessor, never())
        .processRecord(eq(secondPosition), any(), any(), any(), any());

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldWriteFollowUpEvent() {
    // given
    final StreamProcessor streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, state) ->
                processors.onEvent(
                    ValueType.WORKFLOW_INSTANCE,
                    WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                    new TypedRecordProcessor<UnifiedRecordValue>() {
                      @Override
                      public void processRecord(
                          final long position,
                          final TypedRecord<UnifiedRecordValue> record,
                          final TypedResponseWriter responseWriter,
                          final TypedStreamWriter streamWriter,
                          final Consumer<SideEffectProducer> sideEffect) {
                        streamWriter.appendFollowUpEvent(
                            record.getKey(),
                            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
                            record.getValue());
                      }
                    }));

    // when
    final long position =
        streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // then
    final Record<WorkflowInstanceRecord> activatedEvent =
        TestUtil.doRepeatedly(
                () ->
                    streamProcessorRule
                        .events()
                        .onlyWorkflowInstanceRecords()
                        .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
                        .findAny())
            .until(Optional::isPresent)
            .get();
    assertThat(activatedEvent).isNotNull();
    assertThat((activatedEvent).getSourceRecordPosition()).isEqualTo(position);

    assertThat(streamProcessor.getLastWrittenPositionAsync().join())
        .isEqualTo((activatedEvent).getPosition());
    assertThat(streamProcessor.getLastProcessedPositionAsync().join()).isEqualTo(position);
  }

  @Test
  public void shouldExecuteSideEffects() throws Exception {
    // given
    final CountDownLatch processLatch = new CountDownLatch(1);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                new TypedRecordProcessor<UnifiedRecordValue>() {
                  @Override
                  public void processRecord(
                      final long position,
                      final TypedRecord<UnifiedRecordValue> record,
                      final TypedResponseWriter responseWriter,
                      final TypedStreamWriter streamWriter,
                      final Consumer<SideEffectProducer> sideEffect) {
                    sideEffect.accept(
                        () -> {
                          processLatch.countDown();
                          return true;
                        });
                  }
                }));

    // when
    streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // then
    assertThat(processLatch.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void shouldRepeatExecuteSideEffects() throws Exception {
    // given
    final CountDownLatch processLatch = new CountDownLatch(2);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                new TypedRecordProcessor<UnifiedRecordValue>() {
                  @Override
                  public void processRecord(
                      final long position,
                      final TypedRecord<UnifiedRecordValue> record,
                      final TypedResponseWriter responseWriter,
                      final TypedStreamWriter streamWriter,
                      final Consumer<SideEffectProducer> sideEffect) {
                    sideEffect.accept(
                        () -> {
                          processLatch.countDown();
                          return processLatch.getCount() < 1;
                        });
                  }
                }));

    // when
    streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // then
    assertThat(processLatch.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void shouldSkipSideEffectsOnException() throws Exception {
    // given
    final CountDownLatch processLatch = new CountDownLatch(2);
    streamProcessorRule.startTypedStreamProcessor(
        (processors, state) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                new TypedRecordProcessor<UnifiedRecordValue>() {
                  @Override
                  public void processRecord(
                      final long position,
                      final TypedRecord<UnifiedRecordValue> record,
                      final TypedResponseWriter responseWriter,
                      final TypedStreamWriter streamWriter,
                      final Consumer<SideEffectProducer> sideEffect) {
                    sideEffect.accept(
                        () -> {
                          throw new RuntimeException("expected");
                        });
                    processLatch.countDown();
                  }
                }));

    // when
    streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // then
    assertThat(processLatch.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void shouldNotUpdateStateOnExceptionInProcessing() throws Exception {
    // given
    final AtomicLong generatedKey = new AtomicLong(-1L);
    final CountDownLatch processLatch = new CountDownLatch(2);
    streamProcessorRule.startTypedStreamProcessor(
        processingContext -> {
          processingContextActor = processingContext.getActor();
          final ZeebeState state = processingContext.getZeebeState();
          return processors(state.getKeyGenerator())
              .onEvent(
                  ValueType.WORKFLOW_INSTANCE,
                  WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                  new TypedRecordProcessor<>() {
                    @Override
                    public void processRecord(
                        final long position,
                        final TypedRecord<UnifiedRecordValue> record,
                        final TypedResponseWriter responseWriter,
                        final TypedStreamWriter streamWriter,
                        final Consumer<SideEffectProducer> sideEffect) {
                      generatedKey.set(state.getKeyGenerator().nextKey());
                      processLatch.countDown();
                      throw new RuntimeException("expected");
                    }
                  })
              .onEvent(
                  ValueType.WORKFLOW_INSTANCE,
                  WorkflowInstanceIntent.ELEMENT_ACTIVATED,
                  new TypedRecordProcessor<>() {
                    @Override
                    public void processRecord(
                        final TypedRecord<UnifiedRecordValue> record,
                        final TypedResponseWriter responseWriter,
                        final TypedStreamWriter streamWriter,
                        final Consumer<SideEffectProducer> sideEffect) {
                      processLatch.countDown();
                    }
                  });
        });

    // when
    streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATED, 2);

    // then
    assertThat(processLatch.await(5, TimeUnit.SECONDS)).isTrue();

    processingContextActor
        .call(
            () -> {
              final long newGenerated =
                  streamProcessorRule.getZeebeState().getKeyGenerator().nextKey();
              assertThat(generatedKey.get()).isEqualTo(newGenerated);
            })
        .join();
  }

  @Test
  public void shouldUpdateStateAfterProcessing() throws Exception {
    // given
    final AtomicLong generatedKey = new AtomicLong(-1L);

    final CountDownLatch processingLatch = new CountDownLatch(1);
    streamProcessorRule.startTypedStreamProcessor(
        processingContext -> {
          processingContextActor = processingContext.getActor();
          final ZeebeState state = processingContext.getZeebeState();
          return processors(state.getKeyGenerator())
              .onEvent(
                  ValueType.WORKFLOW_INSTANCE,
                  WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                  new TypedRecordProcessor<>() {
                    @Override
                    public void processRecord(
                        final long position,
                        final TypedRecord<UnifiedRecordValue> record,
                        final TypedResponseWriter responseWriter,
                        final TypedStreamWriter streamWriter,
                        final Consumer<SideEffectProducer> sideEffect) {
                      generatedKey.set(state.getKeyGenerator().nextKey());
                    }
                  })
              .onEvent(
                  ValueType.WORKFLOW_INSTANCE,
                  WorkflowInstanceIntent.ELEMENT_ACTIVATED,
                  new TypedRecordProcessor<>() {
                    @Override
                    public void processRecord(
                        final TypedRecord<UnifiedRecordValue> record,
                        final TypedResponseWriter responseWriter,
                        final TypedStreamWriter streamWriter,
                        final Consumer<SideEffectProducer> sideEffect) {
                      processingLatch.countDown();
                    }
                  });
        });

    // when
    streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATED, 2);

    // then
    assertThat(processingLatch.await(5, TimeUnit.SECONDS)).isTrue();
    processingContextActor
        .call(
            () -> {
              final long newGenerated =
                  streamProcessorRule.getZeebeState().getKeyGenerator().nextKey();
              assertThat(generatedKey.get()).isGreaterThan(0L);
              assertThat(generatedKey.get()).isLessThan(newGenerated);
            })
        .join();
  }

  @Test
  public void shouldWriteResponse() {
    // given
    streamProcessorRule.startTypedStreamProcessor(
        (processors, context) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                new TypedRecordProcessor<UnifiedRecordValue>() {
                  @Override
                  public void processRecord(
                      final long position,
                      final TypedRecord<UnifiedRecordValue> record,
                      final TypedResponseWriter responseWriter,
                      final TypedStreamWriter streamWriter,
                      final Consumer<SideEffectProducer> sideEffect) {
                    responseWriter.writeEventOnCommand(
                        3, WorkflowInstanceIntent.ELEMENT_COMPLETING, record.getValue(), record);
                  }
                }));

    // when
    streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // then
    final CommandResponseWriter commandResponseWriter =
        streamProcessorRule.getCommandResponseWriter();

    final InOrder inOrder = inOrder(commandResponseWriter);

    inOrder.verify(commandResponseWriter, TIMEOUT.times(1)).key(3);
    inOrder
        .verify(commandResponseWriter, TIMEOUT.times(1))
        .intent(WorkflowInstanceIntent.ELEMENT_COMPLETING);
    inOrder.verify(commandResponseWriter, TIMEOUT.times(1)).recordType(RecordType.EVENT);
    inOrder.verify(commandResponseWriter, TIMEOUT.times(1)).valueType(ValueType.WORKFLOW_INSTANCE);
    inOrder.verify(commandResponseWriter, TIMEOUT.times(1)).tryWriteResponse(anyInt(), anyLong());
  }

  @Test
  public void shouldNotWriteResponseOnFailedEventProcessing() {
    // given
    streamProcessorRule.startTypedStreamProcessor(
        (processors, context) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                new TypedRecordProcessor<UnifiedRecordValue>() {
                  @Override
                  public void processRecord(
                      final long position,
                      final TypedRecord<UnifiedRecordValue> record,
                      final TypedResponseWriter responseWriter,
                      final TypedStreamWriter streamWriter,
                      final Consumer<SideEffectProducer> sideEffect) {
                    responseWriter.writeEventOnCommand(
                        3, WorkflowInstanceIntent.ELEMENT_COMPLETING, record.getValue(), record);
                    throw new RuntimeException("expected");
                  }
                }));

    // when
    streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // then
    final CommandResponseWriter commandResponseWriter =
        streamProcessorRule.getCommandResponseWriter();

    final InOrder inOrder = inOrder(commandResponseWriter);

    inOrder.verify(commandResponseWriter, TIMEOUT.times(1)).key(3);
    inOrder
        .verify(commandResponseWriter, TIMEOUT.times(1))
        .intent(WorkflowInstanceIntent.ELEMENT_COMPLETING);
    inOrder.verify(commandResponseWriter, TIMEOUT.times(1)).recordType(RecordType.EVENT);
    inOrder.verify(commandResponseWriter, TIMEOUT.times(1)).valueType(ValueType.WORKFLOW_INSTANCE);
    inOrder.verify(commandResponseWriter, never()).tryWriteResponse(anyInt(), anyLong());
  }

  @Test
  public void shouldInvokeOnProcessedListener() throws InterruptedException, TimeoutException {
    // given
    final var onProcessedListener = new AwaitableProcessedListener();
    streamProcessorRule.startTypedStreamProcessor(
        (processors, context) ->
            processors.onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                mock(TypedRecordProcessor.class)),
        onProcessedListener.expect(1));

    // when
    final var position =
        streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // then
    assertThat(onProcessedListener.await()).isTrue();
    assertThat(onProcessedListener.lastProcessedRecord.getPosition()).isEqualTo(position);
  }

  @Test
  public void shouldNotifyLifecycleListenersOnPauseAndResume() throws InterruptedException {
    // given
    final CountDownLatch pauseLatch = new CountDownLatch(1);
    final CountDownLatch resumeLatch = new CountDownLatch(1);
    final StreamProcessor streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, state) ->
                processors.withListener(
                    new StreamProcessorLifecycleAware() {
                      @Override
                      public void onPaused() {
                        pauseLatch.countDown();
                      }

                      @Override
                      public void onResumed() {
                        resumeLatch.countDown();
                      }
                    }));

    // when
    streamProcessor.pauseProcessing();
    streamProcessor.resumeProcessing();

    // then
    pauseLatch.await();
    resumeLatch.await();

    assertThat(pauseLatch.getCount()).isEqualTo(0);
    assertThat(resumeLatch.getCount()).isEqualTo(0);
  }

  @Test
  public void shouldResumeProcessMoreRecordsAfterPause() throws InterruptedException {
    // given
    final var onProcessedListener = new AwaitableProcessedListener();
    final CountDownLatch pauseLatch = new CountDownLatch(1);
    final CountDownLatch resumeLatch = new CountDownLatch(1);
    final TypedRecordProcessor<?> typedRecordProcessor = mock(TypedRecordProcessor.class);
    final StreamProcessor streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, state) ->
                processors
                    .onEvent(
                        ValueType.WORKFLOW_INSTANCE,
                        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                        typedRecordProcessor)
                    .withListener(
                        new StreamProcessorLifecycleAware() {
                          @Override
                          public void onPaused() {
                            pauseLatch.countDown();
                          }

                          @Override
                          public void onResumed() {
                            resumeLatch.countDown();
                          }
                        }),
            onProcessedListener.expect(2));

    streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // when
    streamProcessor.pauseProcessing();
    pauseLatch.await();

    final long positionProcessedAfterResume =
        streamProcessorRule.writeWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    streamProcessor.resumeProcessing();
    resumeLatch.await();

    // then
    assertThat(onProcessedListener.await()).isTrue();
    Assertions.assertThat(
            streamProcessorRule.getZeebeState().getLastSuccessfulProcessedRecordPosition())
        .isEqualTo(positionProcessedAfterResume);
  }

  /**
   * A simple listener which allows you to wait for specific amount of records to be processed.
   *
   * <p>It is necessary to always call {@link #expect(int)} before {@link #accept(TypedRecord)}}
   */
  @SuppressWarnings("rawtypes")
  private static final class AwaitableProcessedListener implements Consumer<TypedRecord> {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private CountDownLatch latch;
    private TypedRecord lastProcessedRecord;

    @Override
    public void accept(final TypedRecord typedRecord) {
      getLatch().countDown();
      lastProcessedRecord = typedRecord;
    }

    private AwaitableProcessedListener expect(final int expectedCount) {
      latch = new CountDownLatch(expectedCount);
      return this;
    }

    private boolean await() throws InterruptedException {
      return getLatch().await(TIMEOUT.toMillis(), TimeUnit.SECONDS);
    }

    private CountDownLatch getLatch() {
      if (latch == null) {
        throw new IllegalStateException(
            "Expected #expect to have been called prior to this method, but it was not");
      }

      return latch;
    }
  }
}
