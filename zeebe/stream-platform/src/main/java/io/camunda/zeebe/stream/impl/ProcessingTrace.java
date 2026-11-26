/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.stream.api.ProcessingResponse;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collection;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Broker-side trace of one command whose {@link RecordMetadata} carries a sampled W3C {@code
 * traceparent}. Starts the broker {@code SERVER} span and its sequential stage children ({@code
 * zeebe.process}, {@code zeebe.write}, {@code zeebe.update_state}, {@code zeebe.await_commit}),
 * ending the request span right after responses are handed to the transport or aborting it on
 * leader step-down.
 *
 * <p>Created only for traced commands; used exclusively on the stream processor actor thread.
 */
@NullMarked
final class ProcessingTrace {
  static final String SPAN_NAME_PREFIX = "zeebe.stream_processor ";
  static final String STAGE_PROCESS = "zeebe.process";
  static final String STAGE_WRITE = "zeebe.write";
  static final String STAGE_UPDATE_STATE = "zeebe.update_state";
  static final String STAGE_AWAIT_COMMIT = "zeebe.await_commit";

  static final AttributeKey<String> PHYSICAL_TENANT_ID =
      AttributeKey.stringKey("zeebe.physical_tenant.id");
  static final AttributeKey<Long> PARTITION_ID = AttributeKey.longKey("zeebe.partition.id");
  static final AttributeKey<String> VALUE_TYPE = AttributeKey.stringKey("zeebe.value_type");
  static final AttributeKey<String> INTENT = AttributeKey.stringKey("zeebe.intent");
  static final AttributeKey<Long> LOG_POSITION = AttributeKey.longKey("zeebe.log.position");
  static final AttributeKey<Long> RECORD_KEY = AttributeKey.longKey("zeebe.record.key");
  static final AttributeKey<Long> QUEUE_WAIT_MS = AttributeKey.longKey("zeebe.queue.wait_ms");
  static final AttributeKey<Long> BATCH_COMMANDS = AttributeKey.longKey("zeebe.batch.commands");
  static final AttributeKey<Long> BATCH_ATTEMPT = AttributeKey.longKey("zeebe.batch.attempt");
  static final AttributeKey<Long> WRITE_RECORDS = AttributeKey.longKey("zeebe.write.records");
  static final AttributeKey<Long> WRITE_ATTEMPTS = AttributeKey.longKey("zeebe.write.attempts");
  static final AttributeKey<String> WRITE_FAILURE = AttributeKey.stringKey("zeebe.write.failure");
  static final AttributeKey<String> REJECTION_TYPE = AttributeKey.stringKey("zeebe.rejection.type");
  static final AttributeKey<String> RETRY_REASON = AttributeKey.stringKey("zeebe.retry.reason");

  static final String EVENT_RETRY = "zeebe.retry";
  static final String EVENT_RESPONSE_DEFERRED = "zeebe.response_deferred";
  static final String EVENT_LEADER_CHANGED = "zeebe.leader_changed";
  static final String EVENT_COMMAND_SKIPPED = "zeebe.command_skipped";
  static final String EVENT_PROCESSING_ABORTED = "zeebe.processing_aborted";

  private static final TextMapGetter<String> TRACEPARENT_GETTER =
      (traceparent, key) -> "traceparent".equals(key) ? traceparent : null;

  private final Tracer tracer;
  private final Span requestSpan;
  private final Context requestContext;
  private @Nullable Span currentStage;
  private long attempt = 1;
  private long writeAttempts;
  private @Nullable WriteFailure lastWriteFailure;
  private boolean ended;

  private ProcessingTrace(final Tracer tracer, final Span requestSpan) {
    this.tracer = tracer;
    this.requestSpan = requestSpan;
    requestContext = Context.root().with(requestSpan);
  }

  /**
   * Starts a broker trace if {@code metadata} carries a valid, sampled W3C {@code traceparent};
   * returns {@code null} otherwise. The broker never starts root traces on its own.
   */
  static @Nullable ProcessingTrace tryStart(
      final Tracer tracer,
      final PartitionId partitionId,
      final RecordMetadata metadata,
      final long position,
      final long key,
      final long queueWaitMs) {
    final var requestSpan =
        startServerSpan(tracer, partitionId, metadata, position, key, queueWaitMs);
    return requestSpan == null ? null : new ProcessingTrace(tracer, requestSpan);
  }

  /** Emits a failed broker span if a traced command was skipped by the processing filter. */
  static void recordSkipped(
      final Tracer tracer,
      final PartitionId partitionId,
      final RecordMetadata metadata,
      final long position,
      final long key,
      final long queueWaitMs) {
    final var span = startServerSpan(tracer, partitionId, metadata, position, key, queueWaitMs);
    if (span != null) {
      span.addEvent(EVENT_COMMAND_SKIPPED);
      span.setStatus(StatusCode.ERROR, "command was skipped by processing filter");
      span.end();
    }
  }

  private static @Nullable Span startServerSpan(
      final Tracer tracer,
      final PartitionId partitionId,
      final RecordMetadata metadata,
      final long position,
      final long key,
      final long queueWaitMs) {
    if (!metadata.hasTraceContext()) {
      return null;
    }
    final var parent =
        W3CTraceContextPropagator.getInstance()
            .extract(Context.root(), metadata.getTraceContext(), TRACEPARENT_GETTER);
    final var parentContext = Span.fromContext(parent).getSpanContext();
    if (!parentContext.isValid() || !parentContext.isSampled()) {
      return null;
    }

    final var valueType = metadata.getValueType();
    final var intent = metadata.getIntent();
    final var intentName = intent != null ? intent.name() : "UNKNOWN";
    final var builder =
        tracer
            .spanBuilder(SPAN_NAME_PREFIX + valueType.name() + " " + intentName)
            .setSpanKind(SpanKind.SERVER)
            .setParent(parent)
            .setAttribute(PHYSICAL_TENANT_ID, partitionId.group())
            .setAttribute(PARTITION_ID, (long) partitionId.number())
            .setAttribute(VALUE_TYPE, valueType.name())
            .setAttribute(INTENT, intentName)
            .setAttribute(LOG_POSITION, position)
            .setAttribute(QUEUE_WAIT_MS, Math.max(0L, queueWaitMs));
    if (key >= 0) {
      builder.setAttribute(RECORD_KEY, key);
    }
    final var span = builder.startSpan();
    if (!span.getSpanContext().isSampled()) {
      span.end();
      return null;
    }
    return span;
  }

  void startBatch() {
    final var span = startStage(STAGE_PROCESS);
    if (attempt > 1) {
      span.setAttribute(BATCH_ATTEMPT, attempt);
    }
  }

  void batchProcessed(final int processedCommands) {
    if (currentStage != null) {
      currentStage.setAttribute(BATCH_COMMANDS, (long) processedCommands);
    }
    endStage(null);
  }

  void retry(final String reason, final Throwable cause) {
    endStage(cause);
    attempt++;
    requestSpan.addEvent(
        EVENT_RETRY,
        Attributes.of(
            RETRY_REASON, reason, AttributeKey.stringKey("exception.message"), message(cause)));
  }

  void processingFailed(final Throwable error) {
    endStage(error);
    requestSpan.recordException(error);
    requestSpan.setStatus(StatusCode.ERROR, message(error));
  }

  void startWrite() {
    startStage(STAGE_WRITE);
  }

  void writeRetried(final WriteFailure failure) {
    writeAttempts++;
    lastWriteFailure = failure;
  }

  void endWrite(final int writtenRecords, final @Nullable Throwable error) {
    if (currentStage != null) {
      currentStage.setAttribute(WRITE_RECORDS, (long) writtenRecords);
      if (writeAttempts > 0) {
        currentStage.setAttribute(WRITE_ATTEMPTS, writeAttempts + 1);
      }
      if (lastWriteFailure != null) {
        currentStage.setAttribute(WRITE_FAILURE, lastWriteFailure.name());
      }
      writeAttempts = 0;
      lastWriteFailure = null;
      endStage(error);
    }
  }

  void startUpdateState() {
    startStage(STAGE_UPDATE_STATE);
  }

  void endUpdateState(final @Nullable Throwable error) {
    endStage(error);
  }

  void awaitCommit() {
    startStage(STAGE_AWAIT_COMMIT);
  }

  void committed() {
    endStage(null);
  }

  /**
   * Ends the broker request span right after responses are handed to the transport. Business
   * rejections are recorded as {@link #REJECTION_TYPE} without an error status; when the batch
   * produces follow-up records without an immediate response (e.g. {@code
   * CreateProcessInstanceWithResult}), {@link #EVENT_RESPONSE_DEFERRED} marks the deferred reply.
   */
  void finishAfterResponses(final Collection<ProcessingResponse> responses) {
    if (ended) {
      return;
    }
    ended = true;
    endStage(null);
    if (responses.isEmpty()) {
      requestSpan.addEvent(EVENT_RESPONSE_DEFERRED);
    } else {
      for (final var response : responses) {
        final var metadata = response.responseValue().recordMetadata();
        if (metadata.getRecordType() == RecordType.COMMAND_REJECTION) {
          requestSpan.setAttribute(REJECTION_TYPE, metadata.getRejectionType().name());
          break;
        }
      }
    }
    requestSpan.end();
  }

  /** Ends the request span when processing produced an empty result and was skipped. */
  void finishSkipped() {
    if (ended) {
      return;
    }
    ended = true;
    endStage(null);
    requestSpan.addEvent(EVENT_COMMAND_SKIPPED);
    requestSpan.end();
  }

  /** Ends any active stage span and the request span as failed, e.g. on leader step-down. */
  void abort(final String eventName, final String description) {
    if (ended) {
      return;
    }
    ended = true;
    final var stage = currentStage;
    if (stage != null) {
      currentStage = null;
      stage.setStatus(StatusCode.ERROR, description);
      stage.end();
    }
    requestSpan.addEvent(eventName);
    requestSpan.setStatus(StatusCode.ERROR, description);
    requestSpan.end();
  }

  private Span startStage(final String name) {
    endStage(null);
    final var span =
        tracer
            .spanBuilder(name)
            .setSpanKind(SpanKind.INTERNAL)
            .setParent(requestContext)
            .startSpan();
    currentStage = span;
    return span;
  }

  private void endStage(final @Nullable Throwable error) {
    final var span = currentStage;
    if (span != null) {
      currentStage = null;
      if (error != null) {
        span.recordException(error);
        span.setStatus(StatusCode.ERROR, message(error));
      }
      span.end();
    }
  }

  private static String message(final Throwable cause) {
    final var message = cause.getMessage();
    return message == null ? cause.getClass().getName() : message;
  }
}
