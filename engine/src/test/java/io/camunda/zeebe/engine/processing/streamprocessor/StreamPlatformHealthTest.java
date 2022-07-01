/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors.processors;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.RecordsBuilder;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.StreamProcessorRule;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.util.health.HealthStatus;
import io.camunda.zeebe.util.sched.Actor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class StreamPlatformHealthTest {

  private static final ProcessInstanceRecord PROCESS_INSTANCE_RECORD = Records.processInstance(1);

  @Rule public final StreamProcessorRule streamProcessorRule = new StreamProcessorRule();

  private StreamPlatform streamPlatform;
  private RecordsBuilder mockedLogStreamWriter;
  private AtomicBoolean shouldFlushThrowException;
  private AtomicInteger invocation;
  private AtomicBoolean shouldFailErrorHandlingInTransaction;
  private AtomicBoolean shouldProcessingThrowException;

  @Before
  public void before() {
    invocation = new AtomicInteger();
    shouldFlushThrowException = new AtomicBoolean();
    shouldFailErrorHandlingInTransaction = new AtomicBoolean();
    shouldProcessingThrowException = new AtomicBoolean();
  }

  @After
  public void tearDown() {
    shouldFlushThrowException.set(false);
    shouldFailErrorHandlingInTransaction.set(false);
    shouldProcessingThrowException.set(false);
  }

  @Test
  public void shouldBeHealthyOnStart() {
    // when
    streamPlatform =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, context) ->
                processors.onCommand(
                    ValueType.PROCESS_INSTANCE,
                    ACTIVATE_ELEMENT,
                    mock(TypedRecordProcessor.class)));

    // then
    waitUntil(() -> streamPlatform.getHealthReport().isHealthy());
  }

  @Test
  public void shouldMarkUnhealthyWhenOnErrorHandlingWriteEventFails() {
    // given
    streamPlatform = getErrorProneStreamProcessor();
    final var healthStatusCheck = HealthStatusCheck.of(streamPlatform);
    streamProcessorRule.getActorSchedulerRule().submitActor(healthStatusCheck);

    waitUntil(() -> healthStatusCheck.hasHealthStatus(HealthStatus.HEALTHY));

    // when
    shouldFlushThrowException.set(true);
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    waitUntil(() -> healthStatusCheck.hasHealthStatus(HealthStatus.UNHEALTHY));
  }

  @Test
  public void shouldMarkUnhealthyWhenProcessingOnWriteEventFails() {
    // given
    streamPlatform = getErrorProneStreamProcessor();
    waitUntil(() -> streamPlatform.getHealthReport().isHealthy());

    // when
    shouldProcessingThrowException.set(false);
    shouldFlushThrowException.set(true);
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    waitUntil(() -> streamPlatform.getHealthReport().isUnhealthy());
  }

  @Test
  public void shouldMarkUnhealthyWhenExceptionErrorHandlingInTransaction() {
    // given
    shouldProcessingThrowException.set(true);
    streamPlatform = getErrorProneStreamProcessor();
    final var healthStatusCheck = HealthStatusCheck.of(streamPlatform);
    streamProcessorRule.getActorSchedulerRule().submitActor(healthStatusCheck);
    waitUntil(() -> healthStatusCheck.hasHealthStatus(HealthStatus.HEALTHY));

    // when
    // since processing fails we will write error event
    // we want to fail error even transaction
    shouldFailErrorHandlingInTransaction.set(true);
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    waitUntil(() -> healthStatusCheck.hasHealthStatus(HealthStatus.UNHEALTHY));
  }

  @Test
  public void shouldBecomeHealthyWhenErrorIsResolved() {
    // given
    shouldFlushThrowException.set(true);
    streamPlatform = getErrorProneStreamProcessor();
    waitUntil(() -> streamPlatform.getHealthReport().isHealthy());

    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);
    waitUntil(() -> streamPlatform.getHealthReport().isUnhealthy());

    // when
    shouldFlushThrowException.set(false);

    // then
    waitUntil(() -> streamPlatform.getHealthReport().isHealthy());
  }

  private StreamPlatform getErrorProneStreamProcessor() {
    streamPlatform =
        streamProcessorRule.startTypedStreamProcessorNotAwaitOpening(
            processingContext -> {
              final MutableZeebeState zeebeState = processingContext.getZeebeState();
              return processors(zeebeState.getKeyGenerator(), processingContext.getWriters())
                  .onCommand(
                      ValueType.PROCESS_INSTANCE,
                      ACTIVATE_ELEMENT,
                      new TypedRecordProcessor<>() {
                        @Override
                        public void processRecord(final TypedRecord<UnifiedRecordValue> record) {

                          invocation.getAndIncrement();
                          if (shouldProcessingThrowException.get()) {
                            throw new RuntimeException("Expected failure on processing");
                          }
                        }
                      });
            },
            batchWriter -> new WrappedRecordsBuilder());

    return streamPlatform;
  }

  private static final class HealthStatusCheck extends Actor {
    private final StreamPlatform streamPlatform;

    private HealthStatusCheck(final StreamPlatform streamPlatform) {
      this.streamPlatform = streamPlatform;
    }

    public static HealthStatusCheck of(final StreamPlatform streamPlatform) {
      return new HealthStatusCheck(streamPlatform);
    }

    public boolean hasHealthStatus(final HealthStatus healthStatus) {
      return actor
          .call(() -> streamPlatform.getHealthReport().getStatus() == healthStatus)
          .join(5, TimeUnit.SECONDS);
    }
  }

  private final class WrappedRecordsBuilder implements RecordsBuilder {

    @Override
    public void appendRejection(
        final TypedRecord<? extends RecordValue> command,
        final RejectionType type,
        final String reason) {}

    @Override
    public void configureSourceContext(final long sourceRecordPosition) {}

    @Override
    public void appendFollowUpEvent(final long key, final Intent intent, final RecordValue value) {
      if (shouldFailErrorHandlingInTransaction.get()) {
        throw new RuntimeException("Expected failure on append followup event");
      }
    }

    @Override
    public int getMaxEventLength() {
      return Integer.MAX_VALUE;
    }

    @Override
    public void appendNewCommand(final Intent intent, final RecordValue value) {}

    @Override
    public void appendFollowUpCommand(
        final long key, final Intent intent, final RecordValue value) {}

    @Override
    public void reset() {}
  }
}
