/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import static io.zeebe.engine.processing.streamprocessor.StreamProcessor.HEALTH_CHECK_TICK_DURATION;
import static io.zeebe.engine.processing.streamprocessor.TypedRecordProcessors.processors;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATING;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.mockito.Mockito.mock;

import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.util.health.HealthStatus;
import io.zeebe.util.sched.Actor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class StreamProcessorHealthTest {

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
                processors.onEvent(
                    ValueType.WORKFLOW_INSTANCE,
                    ELEMENT_ACTIVATING,
                    mock(TypedRecordProcessor.class)));

    // then
    waitUntil(() -> streamProcessor.getHealthStatus() == HealthStatus.HEALTHY);
  }

  @Test
  public void shouldMarkUnhealthyWhenReprocessingRetryLoop() {
    // given
    shouldProcessingThrowException.set(true);
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

    streamProcessor = getErrorProneStreamProcessor();
    final var healthStatusCheck = HealthStatusCheck.of(streamProcessor);
    streamProcessorRule.getActorSchedulerRule().submitActor(healthStatusCheck);

    waitUntil(() -> healthStatusCheck.hasHealthStatus(HealthStatus.HEALTHY));
    waitUntil(() -> invocation.get() > 1);

    // when
    streamProcessorRule.getClock().addTime(HEALTH_CHECK_TICK_DURATION.multipliedBy(1));
    // give some time for scheduled timers to get executed
    final int retried = invocation.get();
    waitUntil(() -> retried < invocation.get());
    streamProcessorRule.getClock().addTime(HEALTH_CHECK_TICK_DURATION.multipliedBy(1));

    // then
    waitUntil(() -> healthStatusCheck.hasHealthStatus(HealthStatus.UNHEALTHY));
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
    streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING, 1);

    // then
    waitUntil(() -> healthStatusCheck.hasHealthStatus(HealthStatus.UNHEALTHY));
  }

  @Test
  public void shouldMarkUnhealthyWhenProcessingOnWriteEventFails() {
    // given
    streamProcessor = getErrorProneStreamProcessor();
    waitUntil(() -> streamProcessor.getHealthStatus() == HealthStatus.HEALTHY);

    // when
    shouldProcessingThrowException.set(false);
    shouldFlushThrowException.set(true);
    streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING, 1);

    // then
    waitUntil(() -> streamProcessor.getHealthStatus() == HealthStatus.UNHEALTHY);
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
    streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING, 1);

    // then
    waitUntil(() -> healthStatusCheck.hasHealthStatus(HealthStatus.UNHEALTHY));
  }

  @Test
  public void shouldBecomeHealthyWhenErrorIsResolved() {
    // given
    shouldFlushThrowException.set(true);
    streamProcessor = getErrorProneStreamProcessor();
    waitUntil(() -> streamProcessor.getHealthStatus() == HealthStatus.HEALTHY);
    streamProcessorRule.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING, 1);
    waitUntil(() -> streamProcessor.getHealthStatus() == HealthStatus.UNHEALTHY);

    // when
    shouldFlushThrowException.set(false);

    // then
    waitUntil(() -> streamProcessor.getHealthStatus() == HealthStatus.HEALTHY);
  }

  private StreamProcessor getErrorProneStreamProcessor() {
    streamProcessor =
        streamProcessorRule.startTypedStreamProcessor(
            processingContext -> {
              final ZeebeState zeebeState = processingContext.getZeebeState();
              mockedLogStreamWriter =
                  new WrappedStreamWriter(processingContext.getLogStreamWriter());
              processingContext.logStreamWriter(mockedLogStreamWriter);
              return processors(zeebeState.getKeyGenerator())
                  .onEvent(
                      ValueType.WORKFLOW_INSTANCE,
                      ELEMENT_ACTIVATING,
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
          .call(() -> streamProcessor.getHealthStatus() == healthStatus)
          .join(5, TimeUnit.SECONDS);
    }
  }

  private final class WrappedStreamWriter implements TypedStreamWriter {

    private final TypedStreamWriter wrappedWriter;

    private WrappedStreamWriter(final TypedStreamWriter wrappedWriter) {
      this.wrappedWriter = wrappedWriter;
    }

    @Override
    public void appendRejection(
        final TypedRecord<? extends RecordValue> command,
        final RejectionType type,
        final String reason) {
      wrappedWriter.appendRejection(command, type, reason);
    }

    @Override
    public void appendRejection(
        final TypedRecord<? extends RecordValue> command,
        final RejectionType type,
        final String reason,
        final UnaryOperator<RecordMetadata> modifier) {
      wrappedWriter.appendRejection(command, type, reason, modifier);
    }

    @Override
    public void configureSourceContext(final long sourceRecordPosition) {
      wrappedWriter.configureSourceContext(sourceRecordPosition);
    }

    @Override
    public void appendNewEvent(final long key, final Intent intent, final RecordValue value) {
      wrappedWriter.appendNewEvent(key, intent, value);
    }

    @Override
    public void appendFollowUpEvent(final long key, final Intent intent, final RecordValue value) {
      if (shouldFailErrorHandlingInTransaction.get()) {
        throw new RuntimeException("Expected failure on append followup event");
      }
      wrappedWriter.appendFollowUpEvent(key, intent, value);
    }

    @Override
    public void appendFollowUpEvent(
        final long key,
        final Intent intent,
        final RecordValue value,
        final UnaryOperator<RecordMetadata> modifier) {
      if (shouldFailErrorHandlingInTransaction.get()) {
        throw new RuntimeException("Expected failure on append followup event");
      }
      wrappedWriter.appendFollowUpEvent(key, intent, value, modifier);
    }

    @Override
    public void appendNewCommand(final Intent intent, final RecordValue value) {
      wrappedWriter.appendNewCommand(intent, value);
    }

    @Override
    public void appendFollowUpCommand(
        final long key, final Intent intent, final RecordValue value) {
      wrappedWriter.appendFollowUpCommand(key, intent, value);
    }

    @Override
    public void appendFollowUpCommand(
        final long key,
        final Intent intent,
        final RecordValue value,
        final UnaryOperator<RecordMetadata> modifier) {
      wrappedWriter.appendFollowUpCommand(key, intent, value, modifier);
    }

    @Override
    public void reset() {
      wrappedWriter.reset();
    }

    @Override
    public long flush() {
      if (shouldFlushThrowException.get()) {
        throw new RuntimeException("Expected failure on flush");
      }
      return wrappedWriter.flush();
    }
  }
}
