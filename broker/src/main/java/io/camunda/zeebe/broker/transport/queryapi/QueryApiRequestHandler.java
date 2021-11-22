/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.queryapi;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.QueryApiCfg;
import io.camunda.zeebe.broker.transport.ApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.engine.state.QueryService.ClosedServiceException;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ExecuteQueryRequestDecoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.Either;
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
public final class QueryApiRequestHandler
    extends ApiRequestHandler<QueryRequestReader, QueryResponseWriter> {
  private static final Set<ValueType> ACCEPTED_VALUE_TYPES =
      EnumSet.of(ValueType.PROCESS, ValueType.PROCESS_INSTANCE, ValueType.JOB);

  private final Map<Integer, QueryService> queryServicePerPartition = new Int2ObjectHashMap<>();
  private final QueryApiCfg config;
  private final String actorName;

  public QueryApiRequestHandler(final QueryApiCfg config, final int nodeId) {
    super(new QueryRequestReader(), new QueryResponseWriter());
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
  protected Either<ErrorResponseWriter, QueryResponseWriter> handle(
      final int partitionId,
      final long requestId,
      final QueryRequestReader requestReader,
      final QueryResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    if (!config.isEnabled()) {
      errorWriter
          .errorCode(ErrorCode.UNSUPPORTED_MESSAGE)
          .errorMessage(
              "Failed to handle query as the query API is disabled; did you configure"
                  + " zeebe.broker.experimental.queryapi.enabled?");
      return Either.left(errorWriter);
    }

    final var queryService = queryServicePerPartition.get(partitionId);
    if (queryService == null) {
      errorWriter.partitionLeaderMismatch(partitionId);
      return Either.left(errorWriter);
    }

    try {
      return handleQuery(
          queryServicePerPartition.get(partitionId),
          requestReader.getMessageDecoder(),
          responseWriter,
          errorWriter);
    } catch (final ClosedServiceException e) {
      Loggers.TRANSPORT_LOGGER.debug(
          "Failed to handle query on partition {} as the query service was closed concurrently",
          partitionId,
          e);
      errorWriter.partitionLeaderMismatch(partitionId);
      return Either.left(errorWriter);
    }
  }

  private Either<ErrorResponseWriter, QueryResponseWriter> handleQuery(
      final QueryService queryService,
      final ExecuteQueryRequestDecoder messageDecoder,
      final QueryResponseWriter responseWriter,
      final ErrorResponseWriter errorResponseWriter) {
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
        return Either.left(failOnInvalidValueType(messageDecoder, errorResponseWriter));
    }

    if (bpmnProcessId.isEmpty()) {
      return Either.left(failOnResourceNotFound(key, messageDecoder, errorResponseWriter));
    }

    responseWriter.bpmnProcessId(bpmnProcessId.get());
    return Either.right(responseWriter);
  }

  private ErrorResponseWriter failOnResourceNotFound(
      final long key,
      final ExecuteQueryRequestDecoder messageDecoder,
      final ErrorResponseWriter errorWriter) {
    return errorWriter
        .errorCode(ErrorCode.PROCESS_NOT_FOUND)
        .errorMessage(
            "Expected to find the process ID for resource of type %s with key %d, but"
                + " no such resource was found",
            messageDecoder.valueType(), key);
  }

  private ErrorResponseWriter failOnInvalidValueType(
      final ExecuteQueryRequestDecoder messageDecoder, final ErrorResponseWriter errorWriter) {
    return errorWriter.internalError(
        "Expected to handle query with value type of %s, but was %s",
        ACCEPTED_VALUE_TYPES, messageDecoder.valueType());
  }
}
