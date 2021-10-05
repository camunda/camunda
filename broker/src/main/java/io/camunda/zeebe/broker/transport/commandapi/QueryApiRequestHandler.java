/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.QueryApiCfg;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.engine.state.QueryService.ClosedServiceException;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ExecuteQueryRequestDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.transport.RequestHandler;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.util.sched.Actor;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

/**
 * Request handler for ExecuteQueryRequest SBE messages. When successful, it looks up the
 * bpmnProcessId of a process based on the request details. Make sure to set {@link
 * QueryApiCfg#setEnabled(boolean)} to true to enable this functionality.
 */
@SuppressWarnings("removal")
@Deprecated(forRemoval = true, since = "1.2.0")
public final class QueryApiRequestHandler extends Actor implements RequestHandler {
  private static final Set<ValueType> ACCEPTED_VALUE_TYPES =
      EnumSet.of(ValueType.PROCESS, ValueType.PROCESS_INSTANCE, ValueType.JOB);

  private final Map<Integer, QueryService> queryServicePerPartition = new Int2ObjectHashMap<>();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final ExecuteQueryRequestDecoder messageDecoder = new ExecuteQueryRequestDecoder();

  private final ErrorResponseWriter errorResponseWriter = new ErrorResponseWriter();
  private final QueryResponseWriter queryResponseWriter = new QueryResponseWriter();
  private final QueryApiCfg config;
  private final String actorName;

  public QueryApiRequestHandler(final QueryApiCfg config, final int nodeId) {
    this.config = config;
    actorName = buildActorName(nodeId, "QueryApi");
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorClosing() {
    queryServicePerPartition.clear();
  }

  public void addPartition(final int partitionId, final QueryService queryService) {
    actor.run(() -> queryServicePerPartition.put(partitionId, queryService));
  }

  public void removePartition(final int partitionId) {
    actor.run(() -> queryServicePerPartition.remove(partitionId));
  }

  @Override
  public void onRequest(
      final ServerOutput serverOutput,
      final int partitionId,
      final long requestId,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    actor.run(
        () -> {
          try {
            handleRequest(serverOutput, partitionId, requestId, buffer, offset);
          } catch (final Exception e) {
            Loggers.TRANSPORT_LOGGER.error(
                "Failed to handle query on partition {}", partitionId, e);
            errorResponseWriter
                .internalError(
                    "Failed to handle query due to internal error; see the broker logs for"
                        + " more")
                .tryWriteResponse(serverOutput, partitionId, requestId);
          }
        });
  }

  private void handleRequest(
      final ServerOutput serverOutput,
      final int partitionId,
      final long requestId,
      final DirectBuffer buffer,
      final int offset) {
    if (!config.isEnabled()) {
      errorResponseWriter
          .errorCode(ErrorCode.UNSUPPORTED_MESSAGE)
          .errorMessage(
              "Failed to handle query as the query API is disabled; did you configure"
                  + " zeebe.broker.experimental.queryapi.enabled?")
          .tryWriteResponse(serverOutput, partitionId, requestId);
      return;
    }

    if (!decodeMessage(serverOutput, partitionId, requestId, buffer, offset)) {
      return;
    }

    final var queryService = queryServicePerPartition.get(partitionId);
    if (queryService == null) {
      errorResponseWriter
          .partitionLeaderMismatch(partitionId)
          .tryWriteResponse(serverOutput, partitionId, requestId);
      return;
    }

    try {
      handleQuery(serverOutput, partitionId, requestId, queryService);
    } catch (final ClosedServiceException e) {
      Loggers.TRANSPORT_LOGGER.debug(
          "Failed to handle query on partition {} as the query service was closed concurrently",
          partitionId,
          e);
      errorResponseWriter
          .partitionLeaderMismatch(partitionId)
          .tryWriteResponse(serverOutput, partitionId, requestId);
    }
  }

  /** {@link #messageDecoder} here is guaranteed to hold a valid, decoded query. */
  private void handleQuery(
      final ServerOutput serverOutput,
      final int partitionId,
      final long requestId,
      final QueryService queryService) {
    final var key = messageDecoder.key();

    final Optional<DirectBuffer> bpmnProcessId;
    switch (messageDecoder.valueType()) {
      case PROCESS:
        bpmnProcessId = queryService.getBpmnProcessIdForProcess(key);
        break;
      case PROCESS_INSTANCE:
        bpmnProcessId = queryService.getBpmnProcessIdForProcessInstance(key);
        break;
      case JOB:
        bpmnProcessId = queryService.getBpmnProcessIdForJob(key);
        break;
      default:
        failOnInvalidValueType(serverOutput, partitionId, requestId);
        return;
    }

    if (bpmnProcessId.isEmpty()) {
      failOnResourceNotFound(serverOutput, partitionId, requestId, key);
      return;
    }

    queryResponseWriter
        .bpmnProcessId(bpmnProcessId.get())
        .tryWriteResponse(serverOutput, partitionId, requestId);
  }

  private boolean decodeMessage(
      final ServerOutput serverOutput,
      final int partitionId,
      final long requestId,
      final DirectBuffer buffer,
      final int offset) {
    if (decodeMessageHeader(serverOutput, partitionId, requestId, buffer, offset)) {
      return false;
    }

    messageDecoder.wrap(
        buffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());

    return true;
  }

  private boolean decodeMessageHeader(
      final ServerOutput serverOutput,
      final int partitionId,
      final long requestId,
      final DirectBuffer buffer,
      final int offset) {
    headerDecoder.wrap(buffer, offset);
    final int templateId = headerDecoder.templateId();
    final int clientVersion = headerDecoder.version();

    if (clientVersion > Protocol.PROTOCOL_VERSION) {
      errorResponseWriter
          .invalidClientVersion(Protocol.PROTOCOL_VERSION, clientVersion)
          .tryWriteResponse(serverOutput, partitionId, requestId);
      return true;
    }

    if (templateId != ExecuteQueryRequestDecoder.TEMPLATE_ID) {
      errorResponseWriter
          .invalidMessageTemplate(templateId, ExecuteQueryRequestDecoder.TEMPLATE_ID)
          .tryWriteResponse(serverOutput, partitionId, requestId);
      return true;
    }
    return false;
  }

  private void failOnResourceNotFound(
      final ServerOutput serverOutput,
      final int partitionId,
      final long requestId,
      final long key) {
    errorResponseWriter
        .errorCode(ErrorCode.PROCESS_NOT_FOUND)
        .errorMessage(
            "Expected to find the process ID for resource of type %s with key %d, but"
                + " no such resource was found",
            messageDecoder.valueType(), key)
        .tryWriteResponse(serverOutput, partitionId, requestId);
  }

  private void failOnInvalidValueType(
      final ServerOutput serverOutput, final int partitionId, final long requestId) {
    errorResponseWriter
        .internalError(
            "Expected to handle query with value type of %s, but was %s",
            ACCEPTED_VALUE_TYPES, messageDecoder.valueType())
        .tryWriteResponse(serverOutput, partitionId, requestId);
  }
}
