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
import io.camunda.zeebe.protocol.record.ErrorResponseDecoder;
import io.camunda.zeebe.protocol.record.ExecuteQueryResponseDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.MsgPackHelper;
import io.camunda.zeebe.test.broker.protocol.commandapi.ErrorResponse;
import io.camunda.zeebe.test.broker.protocol.queryapi.ExecuteQueryRequest;
import io.camunda.zeebe.test.broker.protocol.queryapi.ExecuteQueryResponse;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.EitherAssert;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class QueryApiRequestHandlerTest {

  @DisplayName("should respond with UNSUPPORTED_MESSAGE when QueryApi is disabled")
  @Test
  void disabledQueryApi() {
    // given
    final QueryApiRequestHandler sut = createQueryApiRequestHandler(false);

    // when
    final Either<ErrorResponse, ExecuteQueryResponse> response =
        new AsyncExecuteQueryRequestSender(sut).sendRequest(new ExecuteQueryRequest()).join();

    // then
    EitherAssert.assertThat(response)
        .isLeft()
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode, ErrorResponse::getErrorData)
        .containsExactly(
            ErrorCode.UNSUPPORTED_MESSAGE,
            "Expected to handle ExecuteQueryRequest, but QueryApi is disabled");
  }

  @DisplayName("should respond with INTERNAL_ERROR when partition is unknown")
  @Test
  void unknownPartition() {
    // given
    final QueryApiRequestHandler sut = createQueryApiRequestHandler(true);
    sut.addPartition(1, new QueryServiceReturning(null));

    // when
    final Either<ErrorResponse, ExecuteQueryResponse> response =
        new AsyncExecuteQueryRequestSender(sut)
            .sendRequest(new ExecuteQueryRequest().partitionId(9999))
            .join();

    // then
    EitherAssert.assertThat(response)
        .isLeft()
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode, ErrorResponse::getErrorData)
        .containsExactly(
            ErrorCode.INTERNAL_ERROR,
            "Expected to handle ExecuteQueryRequest, but unable to access query service");
  }

  @DisplayName("should respond with PROCESS_NOT_FOUND when no process with key exists")
  @Test
  void processNotFound() {
    // given
    final QueryApiRequestHandler sut = createQueryApiRequestHandler(true);
    sut.addPartition(1, new QueryServiceReturning(null));

    // when
    final Either<ErrorResponse, ExecuteQueryResponse> response =
        new AsyncExecuteQueryRequestSender(sut)
            .sendRequest(
                new ExecuteQueryRequest().partitionId(1).key(1).valueType(ValueType.PROCESS))
            .join();

    // then
    EitherAssert.assertThat(response)
        .isLeft()
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode, ErrorResponse::getErrorData)
        .containsExactly(
            ErrorCode.PROCESS_NOT_FOUND,
            "Expected to handle ExecuteQueryRequest, but no process found with key 1");
  }

  @DisplayName("should respond with bpmnProcessId when process found")
  @Test
  void processFound() {
    // given
    final QueryApiRequestHandler sut = createQueryApiRequestHandler(true);
    sut.addPartition(1, new QueryServiceReturning("OneProcessToFindThem"));

    // when
    final Either<ErrorResponse, ExecuteQueryResponse> response =
        new AsyncExecuteQueryRequestSender(sut)
            .sendRequest(
                new ExecuteQueryRequest().partitionId(1).key(1).valueType(ValueType.PROCESS))
            .join();

    // then
    EitherAssert.assertThat(response)
        .isRight()
        .extracting(Either::get)
        .extracting(ExecuteQueryResponse::getBpmnProcessId)
        .isEqualTo("OneProcessToFindThem");
  }

  private static QueryApiRequestHandler createQueryApiRequestHandler(final boolean enabled) {
    final var config = new QueryApiCfg();
    config.setEnabled(enabled);
    return new QueryApiRequestHandler(config);
  }

  private static final class AsyncExecuteQueryRequestSender {

    private final QueryApiRequestHandler sut;
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ExecuteQueryResponseDecoder queryResponseDecoder =
        new ExecuteQueryResponseDecoder();

    private int requestCount = 0;

    public AsyncExecuteQueryRequestSender(final QueryApiRequestHandler requestHandler) {
      sut = requestHandler;
    }

    private CompletableFuture<Either<ErrorResponse, ExecuteQueryResponse>> sendRequest(
        final ExecuteQueryRequest queryRequest) {
      final var future = new CompletableFuture<Either<ErrorResponse, ExecuteQueryResponse>>();
      final ServerOutput serverOutput =
          serverResponse -> {
            // we need to write the serverResponse into a buffer in order to read it as SBE
            final var bytes = new byte[serverResponse.getLength()];
            final var buffer = new UnsafeBuffer(bytes);
            serverResponse.write(buffer, 0);

            messageHeaderDecoder.wrap(buffer, 0);
            // we can use the template id to determine the type of response that was send
            switch (messageHeaderDecoder.templateId()) {
              case ErrorResponseDecoder.TEMPLATE_ID:
                future.complete(Either.left(decodeErrorResponse(buffer)));
                break;
              case ExecuteQueryResponseDecoder.TEMPLATE_ID:
                future.complete(Either.right(decodeQueryResponse(buffer)));
                break;
              default:
                throw new IllegalStateException(
                    String.format(
                        "Expected to decode a specific response type, but %d is an unknown template id",
                        messageHeaderDecoder.templateId()));
            }
          };
      final var partitionId = queryRequest.getPartitionId();
      final var request = BufferUtil.createCopy(queryRequest);
      sut.onRequest(serverOutput, partitionId, requestCount++, request, 0, request.capacity());
      return future;
    }

    private static ErrorResponse decodeErrorResponse(final UnsafeBuffer buffer) {
      final var result = new ErrorResponse(new MsgPackHelper());
      result.wrap(buffer, 0, buffer.capacity());
      return result;
    }

    private static ExecuteQueryResponse decodeQueryResponse(final UnsafeBuffer buffer) {
      final var result = new ExecuteQueryResponse();
      result.wrap(buffer, 0, buffer.capacity());
      return result;
    }
  }

  private static final class QueryServiceReturning implements QueryService {

    private final DirectBuffer bpmnProcessId;

    public QueryServiceReturning(final String bpmnProcessId) {
      this.bpmnProcessId = bpmnProcessId == null ? null : BufferUtil.wrapString(bpmnProcessId);
    }

    @Override
    public Optional<DirectBuffer> getBpmnProcessIdForProcess(final long processKey) {
      return Optional.ofNullable(bpmnProcessId);
    }

    @Override
    public Optional<DirectBuffer> getBpmnProcessIdForProcessInstance(
        final long processInstanceKey) {
      return Optional.ofNullable(bpmnProcessId);
    }

    @Override
    public Optional<DirectBuffer> getBpmnProcessIdForJob(final long jobKey) {
      return Optional.ofNullable(bpmnProcessId);
    }
  }
}
