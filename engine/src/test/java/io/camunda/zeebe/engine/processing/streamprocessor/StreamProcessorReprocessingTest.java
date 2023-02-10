/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.StreamProcessingComposite.StreamProcessorTestFactory;
import io.camunda.zeebe.engine.util.StreamProcessorRule;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.stream.StreamWrapper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

public final class StreamProcessorReprocessingTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  private static final ProcessInstanceRecord PROCESS_INSTANCE_RECORD = Records.processInstance(1);

  @Rule public final StreamProcessorRule streamProcessorRule = new StreamProcessorRule();
  private StreamProcessorTestFactory processorTestFactory;

  @Before
  public void setup() {
    final var mockEventApplier = mock(EventApplier.class);
    streamProcessorRule.withEventApplierFactory(state -> mockEventApplier);

    processorTestFactory =
        (processors, context) ->
            processors.onCommand(
                ValueType.PROCESS_INSTANCE,
                ACTIVATE_ELEMENT,
                new TypedRecordProcessor<>() {
                  @Override
                  public void processRecord(final TypedRecord<UnifiedRecordValue> record) {
                    // we need to produce a result otherwise the command will marked as skipped
                    context.getWriters().sideEffect().appendSideEffect(() -> true);
                  }
                });
  }

  @Test
  public void shouldStopProcessingWhenPaused() throws Exception {
    // given - bunch of records
    IntStream.range(0, 5000)
        .forEach(i -> streamProcessorRule.writeProcessInstanceEvent(ELEMENT_ACTIVATING, i));

    streamProcessorRule.writeBatch(
        RecordToWrite.event().processInstance(ELEMENT_ACTIVATING, PROCESS_INSTANCE_RECORD),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATED, PROCESS_INSTANCE_RECORD)
            .causedBy(0));

    Awaitility.await()
        .until(
            () ->
                streamProcessorRule
                    .events()
                    .onlyProcessInstanceRecords()
                    .withIntent(ELEMENT_ACTIVATED),
            StreamWrapper::exists);

    final var onRecoveredLatch = new CountDownLatch(1);
    final var typedRecordProcessor = mock(TypedRecordProcessor.class);
    final var streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, context) ->
                processors
                    .onCommand(ValueType.PROCESS_INSTANCE, ACTIVATE_ELEMENT, typedRecordProcessor)
                    .withListener(
                        new StreamProcessorLifecycleAware() {
                          @Override
                          public void onRecovered(final ReadonlyStreamProcessorContext context) {
                            onRecoveredLatch.countDown();
                          }
                        }));

    // when
    streamProcessor.pauseProcessing().join();
    final var success = onRecoveredLatch.await(15, TimeUnit.SECONDS);

    // then
    assertThat(success).isTrue();
    Mockito.clearInvocations(typedRecordProcessor);

    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, Records.processInstance(0xcafe));

    verify(typedRecordProcessor, TIMEOUT.times(0)).processRecord(any());
    verify(typedRecordProcessor, TIMEOUT.times(0)).processRecord(any());
    verify(typedRecordProcessor, TIMEOUT.times(0)).processRecord(any());
  }

  @Test
  public void shouldContinueProcessingWhenResumed() throws Exception {
    // given - bunch of records
    IntStream.range(0, 5000)
        .forEach(i -> streamProcessorRule.writeProcessInstanceEvent(ELEMENT_ACTIVATING, i));

    streamProcessorRule.writeBatch(
        RecordToWrite.event().processInstance(ELEMENT_ACTIVATING, PROCESS_INSTANCE_RECORD),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATED, PROCESS_INSTANCE_RECORD)
            .causedBy(0));

    Awaitility.await()
        .until(
            () ->
                streamProcessorRule
                    .events()
                    .onlyProcessInstanceRecords()
                    .withIntent(ELEMENT_ACTIVATED),
            StreamWrapper::exists);

    // when
    final var countDownLatch = new CountDownLatch(1);
    final var typedRecordProcessor = mock(TypedRecordProcessor.class);
    final var streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, context) ->
                processors
                    .onCommand(ValueType.PROCESS_INSTANCE, ACTIVATE_ELEMENT, typedRecordProcessor)
                    .withListener(
                        new StreamProcessorLifecycleAware() {
                          @Override
                          public void onRecovered(final ReadonlyStreamProcessorContext context) {
                            countDownLatch.countDown();
                          }
                        }));
    streamProcessor.pauseProcessing();
    streamProcessor.resumeProcessing();
    final var success = countDownLatch.await(15, TimeUnit.SECONDS);

    // then
    assertThat(success).isTrue();
    Mockito.clearInvocations(typedRecordProcessor);

    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, Records.processInstance(0xcafe));

    verify(typedRecordProcessor, TIMEOUT.times(1)).processRecord(any());
  }

  @Test
  public void shouldCallOnPausedAfterOnRecovered() {
    // given - bunch of records
    IntStream.range(0, 5000)
        .forEach(i -> streamProcessorRule.writeProcessInstanceEvent(ELEMENT_ACTIVATING, i));

    streamProcessorRule.writeBatch(
        RecordToWrite.event().processInstance(ELEMENT_ACTIVATING, PROCESS_INSTANCE_RECORD),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATED, PROCESS_INSTANCE_RECORD)
            .causedBy(0));

    Awaitility.await()
        .until(
            () ->
                streamProcessorRule
                    .events()
                    .onlyProcessInstanceRecords()
                    .withIntent(ELEMENT_ACTIVATED),
            StreamWrapper::exists);

    // when
    final var lifecycleAware = mock(StreamProcessorLifecycleAware.class);
    final var streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, context) -> processors.withListener(lifecycleAware));
    streamProcessor.pauseProcessing();
    streamProcessor.resumeProcessing();

    // then
    final InOrder inOrder = inOrder(lifecycleAware);
    // reprocessing
    inOrder.verify(lifecycleAware, TIMEOUT.times(1)).onRecovered(any());
    inOrder.verify(lifecycleAware, TIMEOUT.times(1)).onPaused();
    inOrder.verify(lifecycleAware, TIMEOUT.times(1)).onResumed();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldCallOnPausedBeforeOnResumedNoMatterWhenResumedWasCalled() {
    // given - bunch of records
    IntStream.range(0, 5000)
        .forEach(i -> streamProcessorRule.writeProcessInstanceEvent(ELEMENT_ACTIVATING, i));

    streamProcessorRule.writeBatch(
        RecordToWrite.event().processInstance(ELEMENT_ACTIVATING, PROCESS_INSTANCE_RECORD),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATED, PROCESS_INSTANCE_RECORD)
            .causedBy(0));

    Awaitility.await()
        .until(
            () ->
                streamProcessorRule
                    .events()
                    .onlyProcessInstanceRecords()
                    .withIntent(ELEMENT_ACTIVATED),
            StreamWrapper::exists);

    // when
    final var lifecycleAware = mock(StreamProcessorLifecycleAware.class);
    final var streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, context) -> processors.withListener(lifecycleAware));
    streamProcessor.resumeProcessing();
    streamProcessor.pauseProcessing();
    streamProcessor.resumeProcessing();

    // then
    final InOrder inOrder = inOrder(lifecycleAware);
    // reprocessing
    inOrder.verify(lifecycleAware, TIMEOUT.times(1)).onRecovered(any());
    inOrder.verify(lifecycleAware, TIMEOUT.times(1)).onPaused();
    inOrder.verify(lifecycleAware, TIMEOUT.times(1)).onResumed();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldStartAfterLastProcessedEventInSnapshot() {
    // given
    streamProcessorRule.startTypedStreamProcessor(processorTestFactory);

    streamProcessorRule.writeCommand(ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);
    streamProcessorRule.writeCommand(ACTIVATE_ELEMENT, Records.processInstance(2));

    verify(streamProcessorRule.getMockStreamProcessorListener(), TIMEOUT.times(2))
        .onProcessed(any());

    streamProcessorRule.snapshot();
    streamProcessorRule.closeStreamProcessor();

    Mockito.clearInvocations(streamProcessorRule.getMockStreamProcessorListener());

    // when
    // The processor restarts with a snapshot that was the state of the processor before it
    // was closed.
    streamProcessorRule.startTypedStreamProcessor(processorTestFactory);

    final long position =
        streamProcessorRule.writeCommand(ACTIVATE_ELEMENT, Records.processInstance(3));

    // then
    final var processedCommandCaptor = ArgumentCaptor.forClass(TypedRecord.class);
    verify(streamProcessorRule.getMockStreamProcessorListener(), TIMEOUT)
        .onProcessed(processedCommandCaptor.capture());

    assertThat(processedCommandCaptor.getAllValues())
        .extracting(TypedRecord::getPosition)
        .containsExactly(position);
  }

  @Test
  public void shouldUpdateLastProcessedPositionAfterReplay() throws Exception {
    // given
    final long recordKey = 1L;
    final var record = PROCESS_INSTANCE_RECORD;

    final long firstPosition =
        streamProcessorRule.writeCommand(recordKey, ACTIVATE_ELEMENT, record);

    streamProcessorRule.writeEvent(
        ELEMENT_ACTIVATING,
        record,
        event -> event.key(recordKey).sourceRecordPosition(firstPosition));

    waitUntil(
        () ->
            streamProcessorRule
                .events()
                .onlyProcessInstanceRecords()
                .withIntent(ELEMENT_ACTIVATING)
                .exists());

    // when
    final CountDownLatch recoveredLatch = new CountDownLatch(1);
    final var streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, context) ->
                processors.withListener(
                    new StreamProcessorLifecycleAware() {
                      @Override
                      public void onRecovered(final ReadonlyStreamProcessorContext context) {
                        recoveredLatch.countDown();
                      }
                    }));

    // then
    recoveredLatch.await();

    assertThat(streamProcessor.getLastProcessedPositionAsync().get()).isEqualTo(firstPosition);
  }

  @Test
  public void shouldUpdateLastWrittenPositionAfterReplay() throws Exception {
    // given
    final long recordKey = 1L;
    final var record = PROCESS_INSTANCE_RECORD;

    final long firstPosition =
        streamProcessorRule.writeCommand(recordKey, ACTIVATE_ELEMENT, record);

    final var secondPosition =
        streamProcessorRule.writeEvent(
            ELEMENT_ACTIVATING,
            record,
            event -> event.key(recordKey).sourceRecordPosition(firstPosition));

    waitUntil(
        () ->
            streamProcessorRule
                .events()
                .onlyProcessInstanceRecords()
                .withIntent(ELEMENT_ACTIVATING)
                .exists());

    // when
    final CountDownLatch recoveredLatch = new CountDownLatch(1);
    final var streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, context) ->
                processors.withListener(
                    new StreamProcessorLifecycleAware() {
                      @Override
                      public void onRecovered(final ReadonlyStreamProcessorContext context) {
                        recoveredLatch.countDown();
                      }
                    }));

    // then
    recoveredLatch.await();

    assertThat(streamProcessor.getLastWrittenPositionAsync().get()).isEqualTo(secondPosition);
  }

  @Test
  public void shouldUpdateLastProcessedEventWhenSnapshot() throws Exception {
    // given
    streamProcessorRule.startTypedStreamProcessor(processorTestFactory);

    streamProcessorRule.writeCommand(ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);
    // should be processed and included in the snapshot
    final var snapshotPosition =
        streamProcessorRule.writeCommand(ACTIVATE_ELEMENT, Records.processInstance(2));

    verify(streamProcessorRule.getMockStreamProcessorListener(), TIMEOUT.times(2))
        .onProcessed(any());

    streamProcessorRule.snapshot();
    streamProcessorRule.closeStreamProcessor();

    // when
    // The processor restarts with a snapshot that was the state of the processor before it
    // was closed.
    final var recoveredLatch = new CountDownLatch(1);
    final var streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, context) ->
                processors.withListener(
                    new StreamProcessorLifecycleAware() {
                      @Override
                      public void onRecovered(final ReadonlyStreamProcessorContext context) {
                        recoveredLatch.countDown();
                      }
                    }));

    // then
    recoveredLatch.await();

    assertThat(streamProcessor.getLastProcessedPositionAsync().get()).isEqualTo(snapshotPosition);
  }
}
