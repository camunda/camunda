/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.commandapi;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.transport.backpressure.BackpressureMetrics;
import io.zeebe.broker.transport.backpressure.RequestLimiter;
import io.zeebe.broker.transport.commandapi.CommandTracer.NoopCommandTracer;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.encoding.ExecuteCommandRequest;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.zeebe.protocol.record.MessageHeaderDecoder;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.transport.RequestHandler;
import io.zeebe.transport.ServerOutput;
import java.util.EnumMap;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;
import org.slf4j.Logger;

final class CommandApiRequestHandler implements RequestHandler {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private final ExecuteCommandRequest requestWrapper = new ExecuteCommandRequest();
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final Queue<Runnable> cmdQueue = new ManyToOneConcurrentLinkedQueue<>();
  private final Consumer<Runnable> cmdConsumer = Runnable::run;

  private final Int2ObjectHashMap<LogStreamRecordWriter> leadingStreams = new Int2ObjectHashMap<>();
  private final Int2ObjectHashMap<RequestLimiter<Intent>> partitionLimiters =
      new Int2ObjectHashMap<>();
  private final RecordMetadata eventMetadata = new RecordMetadata();

  private final ErrorResponseWriter errorResponseWriter;

  private final Map<ValueType, UnpackedObject> recordsByType = new EnumMap<>(ValueType.class);
  private final BackpressureMetrics metrics;
  private final CommandTracer tracer;

  public CommandApiRequestHandler() {
    this(new NoopCommandTracer());
  }

  public CommandApiRequestHandler(final CommandTracer tracer) {
    this.metrics = new BackpressureMetrics();
    this.tracer = tracer;
    this.errorResponseWriter = new ErrorResponseWriter(tracer);
    initEventTypeMap();
  }

  private void initEventTypeMap() {
    recordsByType.put(ValueType.DEPLOYMENT, new DeploymentRecord());
    recordsByType.put(ValueType.JOB, new JobRecord());
    recordsByType.put(ValueType.WORKFLOW_INSTANCE, new WorkflowInstanceRecord());
    recordsByType.put(ValueType.MESSAGE, new MessageRecord());
    recordsByType.put(ValueType.JOB_BATCH, new JobBatchRecord());
    recordsByType.put(ValueType.INCIDENT, new IncidentRecord());
    recordsByType.put(ValueType.VARIABLE_DOCUMENT, new VariableDocumentRecord());
    recordsByType.put(ValueType.WORKFLOW_INSTANCE_CREATION, new WorkflowInstanceCreationRecord());
  }

  private void handleExecuteCommandRequest(
      final ServerOutput output,
      final int partitionId,
      final long requestId,
      final RecordMetadata eventMetadata,
      final DirectBuffer buffer,
      final int messageOffset,
      final int messageLength) {
    requestWrapper.wrap(buffer, messageOffset, messageLength);

    final long key = requestWrapper.getKey();
    final ValueType eventType = requestWrapper.getValueType();
    final Intent eventIntent = requestWrapper.getIntent();

    tracer.start(requestWrapper.getSpanContext(), partitionId, requestId);

    final LogStreamRecordWriter logStreamWriter = leadingStreams.get(partitionId);
    if (logStreamWriter == null) {
      errorResponseWriter
          .partitionLeaderMismatch(partitionId)
          .tryWriteResponseOrLogFailure(output, partitionId, requestId);
      return;
    }

    final UnpackedObject event = recordsByType.get(eventType);
    if (event == null) {
      errorResponseWriter
          .unsupportedMessage(eventType.name(), recordsByType.keySet().toArray())
          .tryWriteResponseOrLogFailure(output, partitionId, requestId);
      return;
    }

    event.reset();

    try {
      // verify that the event / command is valid
      event.wrap(requestWrapper.getValue(), 0, requestWrapper.getValue().capacity());
    } catch (final RuntimeException e) {
      LOG.error("Failed to deserialize message of type {} in client API", eventType.name(), e);

      errorResponseWriter
          .malformedRequest(e)
          .tryWriteResponseOrLogFailure(output, partitionId, requestId);
      return;
    }

    eventMetadata.recordType(RecordType.COMMAND);
    eventMetadata.intent(eventIntent);
    eventMetadata.valueType(eventType);

    metrics.receivedRequest(partitionId);
    final RequestLimiter<Intent> limiter = partitionLimiters.get(partitionId);
    if (!limiter.tryAcquire(partitionId, requestId, eventIntent)) {
      metrics.dropped(partitionId);
      LOG.trace(
          "Partition-{} receiving too many requests. Current limit {} inflight {}, dropping request {} from gateway",
          partitionId,
          limiter.getLimit(),
          limiter.getInflightCount(),
          requestId);
      errorResponseWriter.resourceExhausted().tryWriteResponse(output, partitionId, requestId);
      return;
    }

    boolean written = false;
    try {
      written = writeCommand(eventMetadata, key, logStreamWriter);
    } catch (final Exception ex) {
      LOG.error("Unexpected error on writing {} command", eventIntent, ex);
    } finally {
      if (!written) {
        tracer.finish(partitionId, requestId, true);
        limiter.onIgnore(partitionId, requestId);
      }
    }
  }

  private boolean writeCommand(
      final RecordMetadata eventMetadata,
      final long key,
      final LogStreamRecordWriter logStreamWriter) {
    logStreamWriter.reset();
    if (key != ExecuteCommandRequestDecoder.keyNullValue()) {
      logStreamWriter.key(key);
    } else {
      logStreamWriter.keyNull();
    }

    final long eventPosition =
        logStreamWriter
            .metadataWriter(eventMetadata)
            .value(requestWrapper.getValue(), 0, requestWrapper.getValue().capacity())
            .tryWrite();

    return eventPosition >= 0;
  }

  void addPartition(
      final int partitionId,
      final LogStreamRecordWriter logStreamWriter,
      final RequestLimiter<Intent> limiter) {
    cmdQueue.add(
        () -> {
          leadingStreams.put(partitionId, logStreamWriter);
          partitionLimiters.put(partitionId, limiter);
        });
  }

  void removePartition(final LogStream logStream) {
    cmdQueue.add(
        () -> {
          final var recordWriter = leadingStreams.remove(logStream.getPartitionId());
          if (recordWriter != null) {
            recordWriter.close();
          }

          partitionLimiters.remove(logStream.getPartitionId());
        });
  }

  @Override
  public void onRequest(
      final ServerOutput output,
      final int partitionId,
      final long requestId,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    drainCommandQueue();

    messageHeaderDecoder.wrap(buffer, offset);

    final int templateId = messageHeaderDecoder.templateId();
    final int clientVersion = messageHeaderDecoder.version();

    if (clientVersion > Protocol.PROTOCOL_VERSION) {
      errorResponseWriter
          .invalidClientVersion(Protocol.PROTOCOL_VERSION, clientVersion)
          .tryWriteResponse(output, partitionId, requestId);
      return;
    }

    eventMetadata.reset();
    eventMetadata.protocolVersion(clientVersion);
    eventMetadata.requestId(requestId);
    eventMetadata.requestStreamId(partitionId);

    if (templateId == ExecuteCommandRequestDecoder.TEMPLATE_ID) {
      handleExecuteCommandRequest(
          output, partitionId, requestId, eventMetadata, buffer, offset, length);
      return;
    }

    errorResponseWriter
        .invalidMessageTemplate(templateId, ExecuteCommandRequestDecoder.TEMPLATE_ID)
        .tryWriteResponse(output, partitionId, requestId);
  }

  private void drainCommandQueue() {
    while (!cmdQueue.isEmpty()) {
      final Runnable runnable = cmdQueue.poll();
      if (runnable != null) {
        cmdConsumer.accept(runnable);
      }
    }
  }
}
