/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.StreamProcessorRule;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.verification.VerificationWithTimeout;

public final class StreamProcessorReprocessingTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  private static final ProcessInstanceRecord PROCESS_INSTANCE_RECORD = Records.processInstance(1);

  @Rule public final StreamProcessorRule streamProcessorRule = new StreamProcessorRule();

  @Before
  public void setup() {
    final var mockEventApplier = mock(EventApplier.class);
    streamProcessorRule.withEventApplierFactory(state -> mockEventApplier);
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
    streamProcessorRule.startTypedStreamProcessor(
        (processors, context) ->
            processors.onCommand(
                ValueType.PROCESS_INSTANCE,
                ACTIVATE_ELEMENT,
                new TypedRecordProcessor<>() {
                  @Override
                  public void processRecord(final TypedRecord<UnifiedRecordValue> record) {}
                }));

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
