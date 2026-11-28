/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.COMPLETE_ELEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.EmptyProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.util.RecordToWrite;
import io.camunda.zeebe.stream.util.Records;
import io.camunda.zeebe.util.exception.RecoverableException;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

@ExtendWith(StreamPlatformExtension.class)
final class ProcessingStateMachineTracingTest {

  @RegisterExtension static final OpenTelemetryExtension OTEL = OpenTelemetryExtension.create();

  private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
  private static final String PARENT_SPAN_ID = "b7ad6b7169203331";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-" + PARENT_SPAN_ID + "-01";
  private static final String COMMAND_SPAN_NAME =
      "zeebe.stream_processor PROCESS_INSTANCE ACTIVATE_ELEMENT";

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @BeforeEach
  void setUp() {
    OTEL.clearSpans();
  }

  private void startTracedStreamProcessor() {
    streamPlatform.buildStreamProcessor(
        streamPlatform.getLogStream(), true, cfg -> cfg.openTelemetry(OTEL.getOpenTelemetry()));
  }

  private void writeCommand(final String traceparent) {
    final var record =
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1));
    if (traceparent != null) {
      record.traceContext(traceparent);
    }
    streamPlatform.writeBatch(record);
  }

  private int partitionId() {
    return streamPlatform.getLogStream().getPartitionId();
  }

  private SpanData spanNamed(final String name) {
    return OTEL.getSpans().stream()
        .filter(span -> span.getName().equals(name))
        .findFirst()
        .orElse(null);
  }

  private SpanData awaitServerSpan() {
    await("the broker server span ended")
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                OTEL.getSpans().stream()
                    .anyMatch(
                        s ->
                            s.getKind() == SpanKind.SERVER
                                && COMMAND_SPAN_NAME.equals(s.getName())));
    return OTEL.getSpans().stream()
        .filter(s -> s.getKind() == SpanKind.SERVER && COMMAND_SPAN_NAME.equals(s.getName()))
        .findFirst()
        .orElseThrow();
  }

  private List<SpanData> childrenOf(final SpanData parent) {
    return OTEL.getSpans().stream()
        .filter(span -> parent.getSpanId().equals(span.getParentSpanId()))
        .toList();
  }

  private SpanData child(final SpanData parent, final String name) {
    return childrenOf(parent).stream()
        .filter(span -> span.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no child " + name + " in " + OTEL.getSpans()));
  }

  private static RecordMetadata record(final RecordType type, final ProcessInstanceIntent intent) {
    return new RecordMetadata()
        .recordType(type)
        .valueType(ValueType.PROCESS_INSTANCE)
        .intent(intent)
        .rejectionType(RejectionType.NULL_VAL)
        .rejectionReason("");
  }

  @Test
  void shouldTraceServerSpanWithProcessWriteUpdateStateAndAwaitCommit() {
    // given
    final var processor = streamPlatform.getDefaultMockedRecordProcessor();
    when(processor.process(any(), any()))
        .thenAnswer(
            inv -> {
              final ProcessingResultBuilder builder = inv.getArgument(1);
              builder.appendRecord(
                  1, Records.processInstance(1), record(RecordType.EVENT, ACTIVATE_ELEMENT));
              builder.appendRecord(
                  2, Records.processInstance(1), record(RecordType.COMMAND, COMPLETE_ELEMENT));
              builder.withResponse(
                  RecordType.EVENT,
                  1,
                  ACTIVATE_ELEMENT,
                  Records.processInstance(1),
                  ValueType.PROCESS_INSTANCE,
                  RejectionType.NULL_VAL,
                  "",
                  42L,
                  partitionId());
              return builder.build();
            })
        .thenAnswer(
            inv -> {
              final ProcessingResultBuilder builder = inv.getArgument(1);
              builder.appendRecord(
                  3, Records.processInstance(1), record(RecordType.EVENT, COMPLETE_ELEMENT));
              return builder.build();
            });
    startTracedStreamProcessor();

    // when
    writeCommand(TRACEPARENT);

    // then
    final var root = awaitServerSpan();
    assertThat(root.getTraceId()).isEqualTo(TRACE_ID);
    assertThat(root.getParentSpanId()).isEqualTo(PARENT_SPAN_ID);
    assertThat(root.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
    assertThat(root.getEvents()).isEmpty();
    assertThat(root.getAttributes().get(ProcessingTrace.PHYSICAL_TENANT_ID))
        .isEqualTo(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(root.getAttributes().get(ProcessingTrace.PARTITION_ID))
        .isEqualTo((long) partitionId());
    assertThat(root.getAttributes().get(ProcessingTrace.VALUE_TYPE)).isEqualTo("PROCESS_INSTANCE");
    assertThat(root.getAttributes().get(ProcessingTrace.INTENT)).isEqualTo("ACTIVATE_ELEMENT");
    assertThat(root.getAttributes().get(ProcessingTrace.LOG_POSITION)).isPositive();
    assertThat(root.getAttributes().get(ProcessingTrace.QUEUE_WAIT_MS)).isNotNegative();

    assertThat(childrenOf(root))
        .extracting(SpanData::getName)
        .containsExactlyInAnyOrder(
            ProcessingTrace.STAGE_PROCESS,
            ProcessingTrace.STAGE_WRITE,
            ProcessingTrace.STAGE_UPDATE_STATE,
            ProcessingTrace.STAGE_AWAIT_COMMIT);
    final var process = child(root, ProcessingTrace.STAGE_PROCESS);
    assertThat(process.getAttributes().get(ProcessingTrace.BATCH_COMMANDS)).isEqualTo(2L);
    assertThat(process.getAttributes().get(ProcessingTrace.BATCH_ATTEMPT)).isNull();
    assertThat(
            child(root, ProcessingTrace.STAGE_WRITE)
                .getAttributes()
                .get(ProcessingTrace.WRITE_RECORDS))
        .isEqualTo(3L);
    assertThat(child(root, ProcessingTrace.STAGE_AWAIT_COMMIT).getEndEpochNanos())
        .isLessThanOrEqualTo(root.getEndEpochNanos());
  }

  @Test
  void shouldRecordConfiguredPhysicalTenantIdOnBrokerSpan() {
    // given
    streamPlatform.buildStreamProcessor(
        streamPlatform.getLogStream(),
        true,
        cfg ->
            cfg.partitionId(new PartitionId("tenant-a", partitionId()))
                .openTelemetry(OTEL.getOpenTelemetry()));

    // when
    writeCommand(TRACEPARENT);

    // then
    final var root = awaitServerSpan();
    assertThat(root.getAttributes().get(ProcessingTrace.PHYSICAL_TENANT_ID)).isEqualTo("tenant-a");
    assertThat(root.getAttributes().get(ProcessingTrace.PARTITION_ID))
        .isEqualTo((long) partitionId());
  }

  @Test
  void shouldNotCreateSpansForMissingUnsampledOrMalformedTraceContext() {
    // given
    startTracedStreamProcessor();

    // when
    writeCommand(null);
    writeCommand("00-" + TRACE_ID + "-" + PARENT_SPAN_ID + "-00");
    writeCommand("not-a-valid-traceparent");

    // then
    verify(streamPlatform.getMockStreamProcessorListener(), timeout(10_000).times(3))
        .onProcessed(anyLong());
    assertThat(OTEL.getSpans()).isEmpty();
  }

  @Test
  void shouldMarkResponseDeferredWhenBatchWritesEventsWithoutResponse() {
    // given - like CreateProcessInstanceWithResult: writes events, response sent later
    when(streamPlatform.getDefaultMockedRecordProcessor().process(any(), any()))
        .thenAnswer(
            inv -> {
              final ProcessingResultBuilder builder = inv.getArgument(1);
              builder.appendRecord(
                  1, Records.processInstance(1), record(RecordType.EVENT, ACTIVATE_ELEMENT));
              return builder.build();
            });
    startTracedStreamProcessor();

    // when
    writeCommand(TRACEPARENT);

    // then
    final var root = awaitServerSpan();
    assertThat(root.getEvents())
        .extracting(EventData::getName)
        .containsExactly(ProcessingTrace.EVENT_RESPONSE_DEFERRED);
  }

  @Test
  void shouldRecordRejectionResponseAsAttributesWithoutErrorStatus() {
    // given
    when(streamPlatform.getDefaultMockedRecordProcessor().process(any(), any()))
        .thenAnswer(
            inv -> {
              final ProcessingResultBuilder builder = inv.getArgument(1);
              builder.withResponse(
                  RecordType.COMMAND_REJECTION,
                  1,
                  ACTIVATE_ELEMENT,
                  Records.processInstance(1),
                  ValueType.PROCESS_INSTANCE,
                  RejectionType.NOT_FOUND,
                  "nothing here",
                  42L,
                  partitionId());
              return builder.build();
            });
    startTracedStreamProcessor();

    // when
    writeCommand(TRACEPARENT);

    // then
    final var root = awaitServerSpan();
    assertThat(root.getAttributes().get(ProcessingTrace.REJECTION_TYPE)).isEqualTo("NOT_FOUND");
    assertThat(root.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
  }

  @Test
  void shouldMarkSpansAsFailedWhenProcessingThrows() {
    // given
    when(streamPlatform.getDefaultMockedRecordProcessor().process(any(), any()))
        .thenThrow(new IllegalStateException("boom"));
    startTracedStreamProcessor();

    // when
    writeCommand(TRACEPARENT);

    // then
    final var root = awaitServerSpan();
    assertThat(child(root, ProcessingTrace.STAGE_PROCESS).getStatus().getStatusCode())
        .isEqualTo(StatusCode.ERROR);
    assertThat(root.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
  }

  @Test
  void shouldKeepServerSpanOpenAcrossRetries() {
    // given
    when(streamPlatform.getDefaultMockedRecordProcessor().process(any(), any()))
        .thenThrow(new RecoverableException("try again"))
        .thenReturn(EmptyProcessingResult.INSTANCE);
    startTracedStreamProcessor();

    // when
    writeCommand(TRACEPARENT);

    // then
    final var root = awaitServerSpan();
    assertThat(root.getEvents())
        .extracting(EventData::getName)
        .contains(ProcessingTrace.EVENT_RETRY);
    assertThat(root.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
    final var processSpans =
        childrenOf(root).stream()
            .filter(span -> span.getName().equals(ProcessingTrace.STAGE_PROCESS))
            .toList();
    assertThat(processSpans).hasSize(2);
    assertThat(processSpans)
        .extracting(span -> span.getAttributes().get(ProcessingTrace.BATCH_ATTEMPT))
        .containsExactlyInAnyOrder(null, 2L);
    assertThat(OTEL.getSpans()).allMatch(SpanData::hasEnded);
  }

  @Test
  void shouldRecordErrorSpanWhenTracedCommandIsSkippedByFilter() {
    // given
    streamPlatform.buildStreamProcessor(
        streamPlatform.getLogStream(),
        true,
        cfg -> cfg.processingFilter(record -> false).openTelemetry(OTEL.getOpenTelemetry()));

    // when
    writeCommand(TRACEPARENT);

    // then
    final var root = awaitServerSpan();
    assertThat(root.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(root.getEvents())
        .extracting(EventData::getName)
        .containsExactly(ProcessingTrace.EVENT_COMMAND_SKIPPED);
  }

  @Test
  void shouldAbortInFlightTraceWhenStreamProcessorClosesBeforeCommit() {
    // given - defer commits so the processed command stays queued in SideEffectRunner
    final var logStorage = new ListLogStorage();
    streamPlatform.setLogContext(streamPlatform.createLogContext(logStorage, partitionId()));
    logStorage.deferCommits();
    when(streamPlatform.getDefaultMockedRecordProcessor().process(any(), any()))
        .thenAnswer(
            inv -> {
              final ProcessingResultBuilder builder = inv.getArgument(1);
              builder.appendRecord(
                  1, Records.processInstance(1), record(RecordType.EVENT, ACTIVATE_ELEMENT));
              return builder.build();
            });
    final var processor =
        streamPlatform.buildStreamProcessor(
            streamPlatform.getLogStream(), true, cfg -> cfg.openTelemetry(OTEL.getOpenTelemetry()));
    writeCommand(TRACEPARENT);
    await("command was processed and queued for commit")
        .atMost(Duration.ofSeconds(10))
        .until(() -> spanNamed(ProcessingTrace.STAGE_UPDATE_STATE) != null);

    // when - stream processor closes (leader step-down) before Raft commit arrives
    processor.closeAsync().join();

    // then - both await commit and server spans end with error (no leak)
    final var root = awaitServerSpan();
    assertThat(root.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(root.getEvents())
        .extracting(EventData::getName)
        .containsExactly(ProcessingTrace.EVENT_LEADER_CHANGED);
    assertThat(child(root, ProcessingTrace.STAGE_AWAIT_COMMIT).getStatus().getStatusCode())
        .isEqualTo(StatusCode.ERROR);
  }
}
