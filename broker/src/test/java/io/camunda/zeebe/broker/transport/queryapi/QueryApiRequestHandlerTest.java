/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.queryapi;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.system.configuration.QueryApiCfg;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.engine.state.QueryService.ClosedServiceException;
import io.camunda.zeebe.protocol.impl.encoding.ErrorResponse;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteQueryRequest;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteQueryResponse;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.EitherAssert;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.sched.ActorScheduler;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@SuppressWarnings("removal")
@Execution(ExecutionMode.CONCURRENT)
final class QueryApiRequestHandlerTest {
  private final ActorScheduler scheduler =
      ActorScheduler.newActorScheduler()
          .setCpuBoundActorThreadCount(2)
          .setIoBoundActorThreadCount(2)
          .build();

  @BeforeEach
  void beforeEach() {
    scheduler.start();
  }

  @AfterEach
  void afterEach() throws Exception {
    scheduler.close();
  }

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
        .extracting(
            ErrorResponse::getErrorCode, error -> BufferUtil.bufferAsString(error.getErrorData()))
        .containsExactly(
            ErrorCode.UNSUPPORTED_MESSAGE,
            "Failed to handle query as the query API is disabled; did you configure "
                + "zeebe.broker.experimental.queryapi.enabled?");
  }

  @DisplayName("should respond with PARTITION_LEADER_MISMATCH when no service is registered")
  @Test
  void noQueryServiceForPartition() {
    // given
    final QueryApiRequestHandler sut = createQueryApiRequestHandler(true);
    sut.addPartition(1, mock(QueryService.class));

    // when
    final Either<ErrorResponse, ExecuteQueryResponse> response =
        new AsyncExecuteQueryRequestSender(sut)
            .sendRequest(new ExecuteQueryRequest().setPartitionId(9999))
            .join();

    // then
    EitherAssert.assertThat(response)
        .isLeft()
        .extracting(Either::getLeft)
        .extracting(
            ErrorResponse::getErrorCode, error -> BufferUtil.bufferAsString(error.getErrorData()))
        .containsExactly(
            ErrorCode.PARTITION_LEADER_MISMATCH,
            "Expected to handle client message on the leader of partition '9999', but this node is not the leader for it");
  }

  @DisplayName("should respond with PARTITION_LEADER_MISMATCH when the service is closed")
  @Test
  void closedQueryService() {
    // given
    final QueryApiRequestHandler sut = createQueryApiRequestHandler(true);
    sut.addPartition(
        1,
        mock(
            QueryService.class,
            i -> {
              throw new ClosedServiceException();
            }));

    // when
    final Either<ErrorResponse, ExecuteQueryResponse> response =
        new AsyncExecuteQueryRequestSender(sut)
            .sendRequest(new ExecuteQueryRequest().setPartitionId(9999))
            .join();

    // then
    EitherAssert.assertThat(response)
        .isLeft()
        .extracting(Either::getLeft)
        .extracting(
            ErrorResponse::getErrorCode, error -> BufferUtil.bufferAsString(error.getErrorData()))
        .containsExactly(
            ErrorCode.PARTITION_LEADER_MISMATCH,
            "Expected to handle client message on the leader of partition '9999', but this node is "
                + "not the leader for it");
  }

  @DisplayName("should respond with PROCESS_NOT_FOUND when no process with key exists")
  @Test
  void processNotFound() {
    // given
    final QueryApiRequestHandler sut = createQueryApiRequestHandler(true);
    sut.addPartition(1, mock(QueryService.class));

    // when
    final Either<ErrorResponse, ExecuteQueryResponse> response =
        new AsyncExecuteQueryRequestSender(sut)
            .sendRequest(
                new ExecuteQueryRequest()
                    .setPartitionId(1)
                    .setKey(1)
                    .setValueType(ValueType.PROCESS))
            .join();

    // then
    EitherAssert.assertThat(response)
        .isLeft()
        .extracting(Either::getLeft)
        .extracting(
            ErrorResponse::getErrorCode, error -> BufferUtil.bufferAsString(error.getErrorData()))
        .containsExactly(
            ErrorCode.PROCESS_NOT_FOUND,
            "Expected to find the process ID for resource of type PROCESS with key 1, but no such "
                + "resource was found");
  }

  @DisplayName("should respond with PROCESS_NOT_FOUND when no process instance with key exists")
  @Test
  void processInstanceNotFound() {
    // given
    final QueryApiRequestHandler sut = createQueryApiRequestHandler(true);
    sut.addPartition(1, mock(QueryService.class));

    // when
    final Either<ErrorResponse, ExecuteQueryResponse> response =
        new AsyncExecuteQueryRequestSender(sut)
            .sendRequest(
                new ExecuteQueryRequest()
                    .setPartitionId(1)
                    .setKey(1)
                    .setValueType(ValueType.PROCESS_INSTANCE))
            .join();

    // then
    EitherAssert.assertThat(response)
        .isLeft()
        .extracting(Either::getLeft)
        .extracting(
            ErrorResponse::getErrorCode, error -> BufferUtil.bufferAsString(error.getErrorData()))
        .containsExactly(
            ErrorCode.PROCESS_NOT_FOUND,
            "Expected to find the process ID for resource of type PROCESS_INSTANCE with key 1, but "
                + "no such resource was found");
  }

  @DisplayName("should respond with PROCESS_NOT_FOUND when no job with key exists")
  @Test
  void jobNotFound() {
    // given
    final QueryApiRequestHandler sut = createQueryApiRequestHandler(true);
    sut.addPartition(1, mock(QueryService.class));

    // when
    final Either<ErrorResponse, ExecuteQueryResponse> response =
        new AsyncExecuteQueryRequestSender(sut)
            .sendRequest(
                new ExecuteQueryRequest().setPartitionId(1).setKey(1).setValueType(ValueType.JOB))
            .join();

    // then
    EitherAssert.assertThat(response)
        .isLeft()
        .extracting(Either::getLeft)
        .extracting(
            ErrorResponse::getErrorCode, error -> BufferUtil.bufferAsString(error.getErrorData()))
        .containsExactly(
            ErrorCode.PROCESS_NOT_FOUND,
            "Expected to find the process ID for resource of type JOB with key 1, but no such "
                + "resource was found");
  }

  @DisplayName("should respond with bpmnProcessId when process found")
  @Test
  void processFound() throws ClosedServiceException {
    // given
    final QueryApiRequestHandler sut = createQueryApiRequestHandler(true);
    final var bpmnProcessId = BufferUtil.wrapString("OneProcessToFindThem");
    final var queryService = mock(QueryService.class);
    sut.addPartition(1, queryService);
    when(queryService.getBpmnProcessIdForProcess(1)).thenReturn(Optional.of(bpmnProcessId));

    // when
    final Either<ErrorResponse, ExecuteQueryResponse> response =
        new AsyncExecuteQueryRequestSender(sut)
            .sendRequest(
                new ExecuteQueryRequest()
                    .setPartitionId(1)
                    .setKey(1)
                    .setValueType(ValueType.PROCESS))
            .join();

    // then
    EitherAssert.assertThat(response)
        .isRight()
        .extracting(Either::get)
        .extracting(ExecuteQueryResponse::getBpmnProcessId)
        .isEqualTo("OneProcessToFindThem");
  }

  @DisplayName("should respond with bpmnProcessId when job found")
  @Test
  void jobFound() throws ClosedServiceException {
    // given
    final QueryApiRequestHandler sut = createQueryApiRequestHandler(true);
    final var bpmnProcessId = BufferUtil.wrapString("OneProcessToFindThem");
    final var queryService = mock(QueryService.class);
    sut.addPartition(1, queryService);

    when(queryService.getBpmnProcessIdForJob(1)).thenReturn(Optional.of(bpmnProcessId));

    // when
    final Either<ErrorResponse, ExecuteQueryResponse> response =
        new AsyncExecuteQueryRequestSender(sut)
            .sendRequest(
                new ExecuteQueryRequest().setPartitionId(1).setKey(1).setValueType(ValueType.JOB))
            .join();

    // then
    EitherAssert.assertThat(response)
        .isRight()
        .extracting(Either::get)
        .extracting(ExecuteQueryResponse::getBpmnProcessId)
        .isEqualTo("OneProcessToFindThem");
  }

  @DisplayName("should respond with bpmnProcessId when process instance found")
  @Test
  void processInstanceFound() throws ClosedServiceException {
    // given
    final QueryApiRequestHandler sut = createQueryApiRequestHandler(true);
    final var bpmnProcessId = BufferUtil.wrapString("OneProcessToFindThem");
    final var queryService = mock(QueryService.class);
    sut.addPartition(1, queryService);
    when(queryService.getBpmnProcessIdForProcessInstance(1)).thenReturn(Optional.of(bpmnProcessId));

    // when
    final Either<ErrorResponse, ExecuteQueryResponse> response =
        new AsyncExecuteQueryRequestSender(sut)
            .sendRequest(
                new ExecuteQueryRequest()
                    .setPartitionId(1)
                    .setKey(1)
                    .setValueType(ValueType.PROCESS_INSTANCE))
            .join();

    // then
    EitherAssert.assertThat(response)
        .isRight()
        .extracting(Either::get)
        .extracting(ExecuteQueryResponse::getBpmnProcessId)
        .isEqualTo("OneProcessToFindThem");
  }

  @DisplayName("should return MALFORMED_REQUEST on exception thrown while reading the request")
  @Test
  void malformedRequest() {
    // given
    final QueryApiRequestHandler sut = createQueryApiRequestHandler(true);

    // when
    final Either<ErrorResponse, ExecuteQueryResponse> response =
        new AsyncExecuteQueryRequestSender(sut).sendExplodingRequest().join();

    // then
    EitherAssert.assertThat(response)
        .isLeft()
        .extracting(Either::getLeft)
        .extracting(
            ErrorResponse::getErrorCode, error -> BufferUtil.bufferAsString(error.getErrorData()))
        .contains(ErrorCode.MALFORMED_REQUEST);
  }

  private QueryApiRequestHandler createQueryApiRequestHandler(final boolean enabled) {
    final var config = new QueryApiCfg();
    config.setEnabled(enabled);
    final var requestHandler = new QueryApiRequestHandler(config, 0);
    scheduler.submitActor(requestHandler);

    return requestHandler;
  }

  private static final class AsyncExecuteQueryRequestSender {
    private final QueryApiRequestHandler sut;
    private int requestCount = 0;

    public AsyncExecuteQueryRequestSender(final QueryApiRequestHandler requestHandler) {
      sut = requestHandler;
    }

    private CompletableFuture<Either<ErrorResponse, ExecuteQueryResponse>> sendRequest(
        final ExecuteQueryRequest queryRequest) {
      final var future = new CompletableFuture<Either<ErrorResponse, ExecuteQueryResponse>>();
      final ServerOutput serverOutput = createServerOutput(future);

      final var partitionId = queryRequest.getPartitionId();
      final var request = BufferUtil.createCopy(queryRequest);
      sut.onRequest(serverOutput, partitionId, requestCount++, request, 0, request.capacity());
      return future;
    }

    /**
     * Sends a request which will raise an exception on the other side since it has a length of -1
     */
    private CompletableFuture<Either<ErrorResponse, ExecuteQueryResponse>> sendExplodingRequest() {
      final var future = new CompletableFuture<Either<ErrorResponse, ExecuteQueryResponse>>();
      final ServerOutput serverOutput = createServerOutput(future);

      final var request = new UnsafeBuffer();
      sut.onRequest(serverOutput, 1, requestCount++, request, 0, -1);
      return future;
    }

    private ServerOutput createServerOutput(
        final CompletableFuture<Either<ErrorResponse, ExecuteQueryResponse>> future) {
      return serverResponse -> {
        final var buffer = new ExpandableArrayBuffer();
        serverResponse.write(buffer, 0);

        final var error = new ErrorResponse();
        if (error.tryWrap(buffer)) {
          error.wrap(buffer, 0, serverResponse.getLength());
          future.complete(Either.left(error));
          return;
        }

        final var response = new ExecuteQueryResponse();
        try {
          response.wrap(buffer, 0, serverResponse.getLength());
          future.complete(Either.right(response));
        } catch (final Exception e) {
          future.completeExceptionally(e);
        }
      };
    }
  }
}
