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

import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
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
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class StreamProcessorHealthTest {

  private static final ProcessInstanceRecord PROCESS_INSTANCE_RECORD = Records.processInstance(1);

  @Rule public final StreamProcessorRule streamProcessorRule = new StreamProcessorRule();

  private StreamProcessor streamProcessor;
  private TypedStreamWriter mockedLogStreamWriter;
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
    streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            (processors, context) ->
                processors.onCommand(
                    ValueType.PROCESS_INSTANCE,
                    ACTIVATE_ELEMENT,
                    mock(TypedRecordProcessor.class)));

    // then
    waitUntil(() -> streamProcessor.getHealthReport().isHealthy());
  }

  @Test
  public void shouldMarkUnhealthyWhenOnErrorHandlingWriteEventFails() {
    // given
    streamProcessor = getErrorProneStreamProcessor();
    final var healthStatusCheck = HealthStatusCheck.of(streamProcessor);
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
    streamProcessor = getErrorProneStreamProcessor();
    waitUntil(() -> streamProcessor.getHealthReport().isHealthy());

    // when
    shouldProcessingThrowException.set(false);
    shouldFlushThrowException.set(true);
    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);

    // then
    waitUntil(() -> streamProcessor.getHealthReport().isUnhealthy());
  }

  @Test
  public void shouldMarkUnhealthyWhenExceptionErrorHandlingInTransaction() {
    // given
    shouldProcessingThrowException.set(true);
    streamProcessor = getErrorProneStreamProcessor();
    final var healthStatusCheck = HealthStatusCheck.of(streamProcessor);
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
    streamProcessor = getErrorProneStreamProcessor();
    waitUntil(() -> streamProcessor.getHealthReport().isHealthy());

    streamProcessorRule.writeCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);
    waitUntil(() -> streamProcessor.getHealthReport().isUnhealthy());

    // when
    shouldFlushThrowException.set(false);

    // then
    waitUntil(() -> streamProcessor.getHealthReport().isHealthy());
  }

  private StreamProcessor getErrorProneStreamProcessor() {
    streamProcessor =
        streamProcessorRule.startTypedStreamProcessorNotAwaitOpening(
            processingContext -> {
              final MutableZeebeState zeebeState = processingContext.getZeebeState();
              mockedLogStreamWriter = new WrappedStreamWriter();
              processingContext.logStreamWriter(mockedLogStreamWriter);
              return processors(zeebeState.getKeyGenerator(), processingContext.getWriters())
                  .onCommand(
                      ValueType.PROCESS_INSTANCE,
                      ACTIVATE_ELEMENT,
                      new TypedRecordProcessor<>() {
                        @Override
                        public void processRecord(
                            final long position,
                            final TypedRecord<UnifiedRecordValue> record,
                            final TypedResponseWriter responseWriter,
                            final TypedStreamWriter streamWriter,
                            final Consumer<SideEffectProducer> sideEffect) {

                          invocation.getAndIncrement();
                          if (shouldProcessingThrowException.get()) {
                            throw new RuntimeException("Expected failure on processing");
                          }
                        }
                      });
            });

    return streamProcessor;
  }

  private static final class HealthStatusCheck extends Actor {
    private final StreamProcessor streamProcessor;

    private HealthStatusCheck(final StreamProcessor streamProcessor) {
      this.streamProcessor = streamProcessor;
    }

    public static HealthStatusCheck of(final StreamProcessor streamProcessor) {
      return new HealthStatusCheck(streamProcessor);
    }

    public boolean hasHealthStatus(final HealthStatus healthStatus) {
      return actor
          .call(() -> streamProcessor.getHealthReport().getStatus() == healthStatus)
          .join(5, TimeUnit.SECONDS);
    }
  }

  private final class WrappedStreamWriter implements TypedStreamWriter {

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
    public void appendNewCommand(final Intent intent, final RecordValue value) {}

    @Override
    public void appendFollowUpCommand(
        final long key, final Intent intent, final RecordValue value) {}

    @Override
    public void reset() {}

    @Override
    public long flush() {
      if (shouldFlushThrowException.get()) {
        throw new RuntimeException("Expected failure on flush");
      }
      return 1L;
    }
  }
}
