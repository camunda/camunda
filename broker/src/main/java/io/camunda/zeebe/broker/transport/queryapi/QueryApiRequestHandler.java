/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.queryapi;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.transport.backpressure.BackpressureMetrics;
import io.camunda.zeebe.broker.transport.backpressure.RequestLimiter;
import io.camunda.zeebe.broker.transport.commandapi.CommandResponseWriterImpl;
import io.camunda.zeebe.broker.transport.commandapi.ErrorResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.camunda.zeebe.protocol.record.ExecuteQueryRequestDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.transport.RequestHandler;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.ServerTransport;
import java.util.EnumMap;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;
import org.slf4j.Logger;

public final class QueryApiRequestHandler implements RequestHandler {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ExecuteQueryRequestDecoder executeQueryRequestDecoder =
      new ExecuteQueryRequestDecoder();
  private final Queue<Runnable> cmdQueue = new ManyToOneConcurrentLinkedQueue<>();
  private final Consumer<Runnable> cmdConsumer = Runnable::run;

  private final Int2ObjectHashMap<QueryService> leadingStreams = new Int2ObjectHashMap<>();
  private final Int2ObjectHashMap<RequestLimiter<Intent>> partitionLimiters =
      new Int2ObjectHashMap<>();
  private final RecordMetadata eventMetadata = new RecordMetadata();

  private final ErrorResponseWriter errorResponseWriter = new ErrorResponseWriter();

  private final Map<ValueType, UnifiedRecordValue> recordsByType = new EnumMap<>(ValueType.class);
  private final BackpressureMetrics metrics;
  private boolean isDiskSpaceAvailable = true;
  private final CommandResponseWriter responseWriter;

  public QueryApiRequestHandler(final ServerTransport serverTransport) {
    metrics = new BackpressureMetrics();
    initEventTypeMap();
    // todo: either use a customized query response writer, or generalize the writer
    responseWriter = new CommandResponseWriterImpl(serverTransport);
  }

  private void initEventTypeMap() {
    recordsByType.put(ValueType.JOB, new JobRecord());
    recordsByType.put(ValueType.PROCESS, new ProcessRecord());
    recordsByType.put(ValueType.PROCESS_INSTANCE, new ProcessInstanceRecord());
  }

  private void handleExecuteQueryRequest(
      final ServerOutput output,
      final int partitionId,
      final long requestId,
      final DirectBuffer buffer,
      final int messageOffset) {

    if (!isDiskSpaceAvailable) {
      errorResponseWriter
          .resourceExhausted(
              String.format(
                  "Cannot accept requests for partition %d. Broker is out of disk space",
                  partitionId))
          .tryWriteResponse(output, partitionId, requestId);
      return;
    }

    executeQueryRequestDecoder.wrap(
        buffer,
        messageOffset + messageHeaderDecoder.encodedLength(),
        messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    final QueryService queryService = leadingStreams.get(partitionId);

    if (queryService == null) {
      errorResponseWriter
          .partitionLeaderMismatch(partitionId)
          .tryWriteResponseOrLogFailure(output, partitionId, requestId);
      return;
    }

    final ValueType queryValueType = executeQueryRequestDecoder.valueType();

    if (!recordsByType.containsKey(queryValueType)) {
      errorResponseWriter
          .unsupportedMessage(queryValueType.name(), recordsByType.keySet().toArray())
          .tryWriteResponseOrLogFailure(output, partitionId, requestId);
      return;
    }

    metrics.receivedRequest(partitionId);
    final RequestLimiter<Intent> limiter = partitionLimiters.get(partitionId);
    if (!limiter.tryAcquire(partitionId, requestId, Intent.UNKNOWN)) {
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

    final long key = executeQueryRequestDecoder.key();
    final UnifiedRecordValue result = recordsByType.get(queryValueType);
    result.reset();

    if (queryValueType == ValueType.JOB) {
      queryService.getBpmnProcessIdForJob(key).ifPresent(((JobRecord) result)::setBpmnProcessId);
    } else if (queryValueType == ValueType.PROCESS) {
      queryService
          .getBpmnProcessIdForProcess(key)
          .ifPresent(((ProcessRecord) result)::setBpmnProcessId);
    } else if (queryValueType == ValueType.PROCESS_INSTANCE) {
      queryService
          .getBpmnProcessIdForProcessInstance(key)
          .ifPresent(((ProcessInstanceRecord) result)::setBpmnProcessId);
    }

    boolean written = false;
    try {
      written =
          responseWriter
              .key(key)
              .intent(Intent.UNKNOWN)
              .partitionId(partitionId)
              .valueType(queryValueType)
              .recordType(RecordType.EVENT)
              .valueWriter(result)
              .tryWriteResponse(partitionId, requestId);

      LOG.info("!!!!!!!!!!             OMG Query Request HANDLED             !!!!!!!!");

    } catch (final Exception ex) {
      LOG.error("Unexpected error on writing {} command", Intent.UNKNOWN, ex);
    } finally {
      if (!written) {
        limiter.onIgnore(partitionId, requestId);
      }
    }
  }

  public void addPartition(
      final int partitionId,
      final RequestLimiter<Intent> limiter,
      final QueryService queryService) {
    cmdQueue.add(
        () -> {
          leadingStreams.put(partitionId, queryService);
          partitionLimiters.put(partitionId, limiter);
        });
  }

  public void removePartition(final int partitionId) {
    cmdQueue.add(
        () -> {
          leadingStreams.remove(partitionId);
          partitionLimiters.remove(partitionId);
        });
  }

  public void onDiskSpaceNotAvailable() {
    cmdQueue.add(
        () -> {
          isDiskSpaceAvailable = false;
          LOG.debug("Broker is out of disk space. All client requests will be rejected");
        });
  }

  public void onDiskSpaceAvailable() {
    cmdQueue.add(() -> isDiskSpaceAvailable = true);
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

    LOG.info(
        "OMGOMGOMGOMGOMGOMGOMGOMG OMGOMGOMGOMGOMGOMGOMG OMGOMGOMGOMGOMGOMGOMG OMGOMGOMGOMGOMGOMGOMG");

    messageHeaderDecoder.wrap(buffer, offset);

    final int templateId = messageHeaderDecoder.templateId();
    final int clientVersion = messageHeaderDecoder.version();

    if (clientVersion > Protocol.PROTOCOL_VERSION) {
      errorResponseWriter
          .invalidClientVersion(Protocol.PROTOCOL_VERSION, clientVersion)
          .tryWriteResponse(output, partitionId, requestId);
      return;
    }

    if (templateId == ExecuteCommandRequestDecoder.TEMPLATE_ID) {
      handleExecuteQueryRequest(output, partitionId, requestId, buffer, offset);
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
