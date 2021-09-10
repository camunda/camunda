/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.externalapi;

import io.camunda.zeebe.broker.system.configuration.SocketBindingCfg.ExternalApiCfg.QueryApiCfg;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ExecuteQueryRequestDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.transport.RequestHandler;
import io.camunda.zeebe.transport.ServerOutput;
import java.util.Map;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

/**
 * Request handler for ExecuteQueryRequest SBE messages. When successful, it looks up the
 * bpmnProcessId of a process based on the request details. Make sure to set {@link
 * QueryApiCfg#setEnabled(boolean)} to true to enable this functionality.
 */
public final class QueryApiRequestHandler implements RequestHandler {

  private final Map<Integer, QueryService> queryServicePerPartition = new Int2ObjectHashMap<>();
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ExecuteQueryRequestDecoder requestDecoder = new ExecuteQueryRequestDecoder();

  private final ErrorResponseWriter errorResponseWriter = new ErrorResponseWriter();
  private final QueryResponseWriter queryResponseWriter = new QueryResponseWriter();
  private final QueryApiCfg config;

  public QueryApiRequestHandler(final QueryApiCfg config) {
    this.config = config;
  }

  @Override
  public void onRequest(
      final ServerOutput serverOutput,
      final int partitionId,
      final long requestId,
      final DirectBuffer buffer,
      final int offset,
      final int length) {

    if (!config.isEnabled()) {
      errorResponseWriter
          .errorCode(ErrorCode.UNSUPPORTED_MESSAGE)
          .errorMessage("Expected to handle ExecuteQueryRequest, but QueryApi is disabled")
          .tryWriteResponse(serverOutput, partitionId, requestId);
      return;
    }

    messageHeaderDecoder.wrap(buffer, offset);

    // todo: check all things that can be wrong with the message

    requestDecoder.wrap(
        buffer,
        offset + messageHeaderDecoder.encodedLength(),
        messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    final var key = requestDecoder.key();

    final var queryService = queryServicePerPartition.get(partitionId);
    if (queryService == null) {
      errorResponseWriter
          .partitionLeaderMismatch(partitionId)
          .tryWriteResponse(serverOutput, partitionId, requestId);
      return;
    }

    final Optional<DirectBuffer> bpmnProcessId;
    switch (requestDecoder.valueType()) {
      case PROCESS:
        bpmnProcessId = queryService.getBpmnProcessIdForProcess(key);
        break;
      default:
        // todo: add other value types
        // todo: deal with this failure
        bpmnProcessId = Optional.empty();
        break;
    }

    if (bpmnProcessId.isEmpty()) {
      final String type = getValueTypeName(requestDecoder.valueType());
      errorResponseWriter
          .errorCode(ErrorCode.PROCESS_NOT_FOUND)
          .errorMessage(
              String.format(
                  "Expected to handle ExecuteQueryRequest, but no %s found with key %d", type, key))
          .tryWriteResponse(serverOutput, partitionId, requestId);
      return;
    }

    queryResponseWriter
        .bpmnProcessId(bpmnProcessId.get())
        .tryWriteResponse(serverOutput, partitionId, requestId);
  }

  private static String getValueTypeName(final ValueType valueType) {
    switch (valueType) {
      case PROCESS:
        return "process";
      case PROCESS_INSTANCE:
        return "process instance";
      case JOB:
        return "job";
      default:
        return "element";
    }
  }

  public void addPartition(final int partitionId, final QueryService queryService) {
    queryServicePerPartition.put(partitionId, queryService);
  }

  public void removePartition(final int partitionId) {
    queryServicePerPartition.remove(partitionId);
  }
}
