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
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
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
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;
import java.util.EnumMap;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;
import org.slf4j.Logger;

public class CommandApiMessageHandler implements ServerMessageHandler, ServerRequestHandler {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ExecuteCommandRequestDecoder executeCommandRequestDecoder =
      new ExecuteCommandRequestDecoder();
  private final ManyToOneConcurrentLinkedQueue<Runnable> cmdQueue =
      new ManyToOneConcurrentLinkedQueue<>();
  private final Consumer<Runnable> cmdConsumer = Runnable::run;

  private final Int2ObjectHashMap<LogStreamRecordWriter> leadingStreams = new Int2ObjectHashMap<>();
  private final Int2ObjectHashMap<RequestLimiter<Intent>> partitionLimiters =
      new Int2ObjectHashMap<>();
  private final RecordMetadata eventMetadata = new RecordMetadata();

  private final ErrorResponseWriter errorResponseWriter = new ErrorResponseWriter();

  private final EnumMap<ValueType, UnpackedObject> recordsByType = new EnumMap<>(ValueType.class);
  private final BackpressureMetrics metrics;

  public CommandApiMessageHandler() {
    this.metrics = new BackpressureMetrics();
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

  private boolean handleExecuteCommandRequest(
      final ServerOutput output,
      final RemoteAddress requestAddress,
      final long requestId,
      final RecordMetadata eventMetadata,
      final DirectBuffer buffer,
      final int messageOffset,
      final int messageLength) {
    executeCommandRequestDecoder.wrap(
        buffer,
        messageOffset + messageHeaderDecoder.encodedLength(),
        messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    final int partitionId = executeCommandRequestDecoder.partitionId();
    final long key = executeCommandRequestDecoder.key();

    final LogStreamRecordWriter logStreamWriter = leadingStreams.get(partitionId);

    if (logStreamWriter == null) {
      return errorResponseWriter
          .partitionLeaderMismatch(partitionId)
          .tryWriteResponseOrLogFailure(output, requestAddress.getStreamId(), requestId);
    }

    final ValueType eventType = executeCommandRequestDecoder.valueType();
    final short intent = executeCommandRequestDecoder.intent();
    final UnpackedObject event = recordsByType.get(eventType);

    if (event == null) {
      return errorResponseWriter
          .unsupportedMessage(eventType.name(), recordsByType.keySet().toArray())
          .tryWriteResponseOrLogFailure(output, requestAddress.getStreamId(), requestId);
    }

    final int eventOffset =
        executeCommandRequestDecoder.limit() + ExecuteCommandRequestDecoder.valueHeaderLength();
    final int eventLength = executeCommandRequestDecoder.valueLength();

    event.reset();

    try {
      // verify that the event / command is valid
      event.wrap(buffer, eventOffset, eventLength);
    } catch (RuntimeException e) {
      LOG.error("Failed to deserialize message of type {} in client API", eventType.name(), e);

      return errorResponseWriter
          .malformedRequest(e)
          .tryWriteResponseOrLogFailure(output, requestAddress.getStreamId(), requestId);
    }

    eventMetadata.recordType(RecordType.COMMAND);
    final Intent eventIntent = Intent.fromProtocolValue(eventType, intent);
    eventMetadata.intent(eventIntent);
    eventMetadata.valueType(eventType);

    metrics.receivedRequest(partitionId);
    final RequestLimiter<Intent> limiter = partitionLimiters.get(partitionId);
    if (!limiter.tryAcquire(requestAddress.getStreamId(), requestId, eventIntent)) {
      metrics.dropped(partitionId);
      LOG.trace(
          "Partition-{} receiving too many requests. Current limit {} inflight {}, dropping request {} from gateway {}",
          partitionId,
          limiter.getLimit(),
          limiter.getInflightCount(),
          requestId,
          requestAddress.getAddress());
      return errorResponseWriter
          .resourceExhausted()
          .tryWriteResponse(output, requestAddress.getStreamId(), requestId);
    }

    boolean written = false;
    try {
      written = writeCommand(eventMetadata, buffer, key, logStreamWriter, eventOffset, eventLength);
    } catch (Exception ex) {
      LOG.error("Unexpected error on writing {} command", eventIntent, ex);
    } finally {
      if (!written) {
        limiter.onIgnore(requestAddress.getStreamId(), requestId);
      }
    }
    return written;
  }

  private boolean writeCommand(
      RecordMetadata eventMetadata,
      DirectBuffer buffer,
      long key,
      LogStreamRecordWriter logStreamWriter,
      int eventOffset,
      int eventLength) {
    logStreamWriter.reset();

    if (key != ExecuteCommandRequestDecoder.keyNullValue()) {
      logStreamWriter.key(key);
    } else {
      logStreamWriter.keyNull();
    }

    final long eventPosition =
        logStreamWriter
            .metadataWriter(eventMetadata)
            .value(buffer, eventOffset, eventLength)
            .tryWrite();

    return eventPosition >= 0;
  }

  public void addPartition(
      int partitionId, LogStreamRecordWriter logStreamWriter, RequestLimiter<Intent> limiter) {
    cmdQueue.add(
        () -> {
          leadingStreams.put(partitionId, logStreamWriter);
          partitionLimiters.put(partitionId, limiter);
        });
  }

  public void removePartition(LogStream logStream) {
    cmdQueue.add(
        () -> {
          leadingStreams.remove(logStream.getPartitionId());
          partitionLimiters.remove(logStream.getPartitionId());
        });
  }

  @Override
  public boolean onRequest(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final long requestId) {
    drainCommandQueue();

    messageHeaderDecoder.wrap(buffer, offset);

    final int templateId = messageHeaderDecoder.templateId();
    final int clientVersion = messageHeaderDecoder.version();

    if (clientVersion > Protocol.PROTOCOL_VERSION) {
      return errorResponseWriter
          .invalidClientVersion(Protocol.PROTOCOL_VERSION, clientVersion)
          .tryWriteResponse(output, remoteAddress.getStreamId(), requestId);
    }

    eventMetadata.reset();
    eventMetadata.protocolVersion(clientVersion);
    eventMetadata.requestId(requestId);
    eventMetadata.requestStreamId(remoteAddress.getStreamId());

    if (templateId == ExecuteCommandRequestDecoder.TEMPLATE_ID) {
      return handleExecuteCommandRequest(
          output, remoteAddress, requestId, eventMetadata, buffer, offset, length);
    }

    return errorResponseWriter
        .invalidMessageTemplate(templateId, ExecuteCommandRequestDecoder.TEMPLATE_ID)
        .tryWriteResponse(output, remoteAddress.getStreamId(), requestId);
  }

  @Override
  public boolean onMessage(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    // ignore; currently no incoming single-message client interactions
    return true;
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
