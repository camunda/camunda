/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.security.auth.Authentication;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.service.util.StubbedTopologyManager;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.RequestRetriesExhaustedException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public final class ProcessInstanceServiceTest {

  private ProcessInstanceServices services;
  private BrokerClient brokerClient;

  @BeforeEach
  public void before() {

    brokerClient = mock(BrokerClient.class);
    when(brokerClient.getTopologyManager()).thenReturn(new StubbedTopologyManager(3));

    services =
        new ProcessInstanceServices(
            brokerClient,
            new StubbedCamundaSearchClient(),
            null,
            new Authentication.Builder().token("token").build());
  }

  @Nested
  @DisplayName("Retry on different partitions")
  class RetryPartitionsTest {

    public static final String RETRY_EXHAUSTED_ERROR =
        "Expected to execute the command on one of the partitions, but all failed; there are no more partitions available to retry. Please try again. If the error persists contact your zeebe operator";

    private static Stream<Named<Throwable>> retryableErrors() {
      return Stream.of(
          Named.of(
              "PARTITION_LEADER_MISMATCH",
              new BrokerErrorException(
                  new BrokerError(ErrorCode.PARTITION_LEADER_MISMATCH, "leader mismatch"))),
          Named.of(
              "RESOURCE_EXHAUSTED",
              new BrokerErrorException(
                  new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "resource exhausted"))),
          Named.of("ConnectException", new ConnectException("connection refused")));
    }

    @ParameterizedTest
    @MethodSource("retryableErrors")
    void shouldRetryCreateProcessInstanceOnRetryableError(final Throwable retryableError) {
      // given
      final var request = createProcessInstanceRequest();
      final var mockResponse = new ProcessInstanceCreationRecord();
      final var triedPartitions = new ArrayList<Integer>();
      when(brokerClient.sendRequest(any(BrokerCreateProcessInstanceRequest.class)))
          .thenAnswer(
              invocation -> {
                final var brokerRequest =
                    invocation.getArgument(0, BrokerCreateProcessInstanceRequest.class);
                triedPartitions.add(brokerRequest.getPartitionId());
                if (triedPartitions.size() == 1) {
                  return CompletableFuture.failedFuture(retryableError);
                }
                return CompletableFuture.completedFuture(new BrokerResponse<>(mockResponse));
              });

      // when
      final var result = services.createProcessInstance(request).join();

      // then - retried on a different partition and succeeded
      assertThat(result).isNotNull();
      assertThat(triedPartitions).hasSize(2);
      assertThat(triedPartitions.get(0)).isNotEqualTo(triedPartitions.get(1));
    }

    @ParameterizedTest
    @MethodSource("retryableErrors")
    void shouldRetryCreateProcessInstanceWithResultOnRetryableError(
        final Throwable retryableError) {
      // given
      final var request = createProcessInstanceWithResultRequest(null);
      final var mockResponse = new ProcessInstanceResultRecord();
      final var triedPartitions = new ArrayList<Integer>();
      when(brokerClient.sendRequest(any(BrokerCreateProcessInstanceWithResultRequest.class)))
          .thenAnswer(
              invocation -> {
                final var brokerRequest =
                    invocation.getArgument(0, BrokerCreateProcessInstanceWithResultRequest.class);
                triedPartitions.add(brokerRequest.getPartitionId());
                if (triedPartitions.size() == 1) {
                  return CompletableFuture.failedFuture(retryableError);
                }
                return CompletableFuture.completedFuture(new BrokerResponse<>(mockResponse));
              });

      // when
      final var result = services.createProcessInstanceWithResult(request).join();

      // then
      assertThat(result).isNotNull();
      assertThat(triedPartitions).hasSize(2);
      assertThat(triedPartitions.get(0)).isNotEqualTo(triedPartitions.get(1));
    }

    @Test
    void shouldExhaustRetriesForCreateProcessInstanceWhenAllPartitionsFail() {
      // given - topology has 3 partitions, all fail with retryable errors
      final var request = createProcessInstanceRequest();
      final var triedPartitions = new ArrayList<Integer>();
      when(brokerClient.sendRequest(any(BrokerCreateProcessInstanceRequest.class)))
          .thenAnswer(
              invocation -> {
                final var brokerRequest =
                    invocation.getArgument(0, BrokerCreateProcessInstanceRequest.class);
                triedPartitions.add(brokerRequest.getPartitionId());
                return CompletableFuture.failedFuture(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.PARTITION_LEADER_MISMATCH, "leader mismatch")));
              });

      // when / then - all 3 partitions tried with distinct IDs, then RESOURCE_EXHAUSTED error
      assertThatThrownBy(() -> services.createProcessInstance(request).join())
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(CamundaServiceException.class)
          .extracting(Throwable::getCause)
          .satisfies(
              cause -> {
                final var serviceException = (RequestRetriesExhaustedException) cause.getCause();
                assertThat(serviceException.getMessage()).isEqualTo(RETRY_EXHAUSTED_ERROR);
              });
      assertThat(triedPartitions).hasSize(3);
      assertThat(triedPartitions).doesNotHaveDuplicates();
    }

    @Test
    void shouldExhaustRetriesForCreateProcessInstanceWithResultWhenAllPartitionsFail() {
      // given
      final var request = createProcessInstanceWithResultRequest(null);
      final var triedPartitions = new ArrayList<Integer>();
      when(brokerClient.sendRequest(any(BrokerCreateProcessInstanceWithResultRequest.class)))
          .thenAnswer(
              invocation -> {
                final var brokerRequest =
                    invocation.getArgument(0, BrokerCreateProcessInstanceWithResultRequest.class);
                triedPartitions.add(brokerRequest.getPartitionId());
                return CompletableFuture.failedFuture(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "resource exhausted")));
              });

      // when / then
      assertThatThrownBy(() -> services.createProcessInstanceWithResult(request).join())
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(CamundaServiceException.class)
          .extracting(Throwable::getCause)
          .satisfies(
              cause -> {
                final var serviceException = (RequestRetriesExhaustedException) cause.getCause();
                assertThat(serviceException.getMessage()).isEqualTo(RETRY_EXHAUSTED_ERROR);
              });
      assertThat(triedPartitions).hasSize(3);
      assertThat(triedPartitions).doesNotHaveDuplicates();
    }

    @Test
    void shouldNotRetryCreateProcessInstanceOnNonRetryableError() {
      // given - a non-retryable error like PROCESS_NOT_FOUND
      final var request = createProcessInstanceRequest();
      when(brokerClient.sendRequest(any(BrokerCreateProcessInstanceRequest.class)))
          .thenReturn(
              CompletableFuture.failedFuture(
                  new BrokerErrorException(
                      new BrokerError(ErrorCode.PROCESS_NOT_FOUND, "process not found"))));

      // when / then - only tried once, no retry
      assertThatThrownBy(() -> services.createProcessInstance(request).join())
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(CamundaServiceException.class);
      verify(brokerClient, times(1)).sendRequest(any(BrokerCreateProcessInstanceRequest.class));
    }

    @Test
    void shouldRetryCreateProcessInstanceWithResultUsingCustomTimeout() {
      // given
      final var request = createProcessInstanceWithResultRequest(600_000L);
      final var mockResponse = new ProcessInstanceResultRecord();
      final var triedPartitions = new ArrayList<Integer>();
      when(brokerClient.sendRequest(
              any(BrokerCreateProcessInstanceWithResultRequest.class),
              eq(java.time.Duration.ofMillis(600_000L))))
          .thenAnswer(
              invocation -> {
                final var brokerRequest =
                    invocation.getArgument(0, BrokerCreateProcessInstanceWithResultRequest.class);
                triedPartitions.add(brokerRequest.getPartitionId());
                if (triedPartitions.size() == 1) {
                  return CompletableFuture.failedFuture(
                      new BrokerErrorException(
                          new BrokerError(ErrorCode.PARTITION_LEADER_MISMATCH, "leader mismatch")));
                }
                return CompletableFuture.completedFuture(new BrokerResponse<>(mockResponse));
              });

      // when
      final var result = services.createProcessInstanceWithResult(request).join();

      // then
      assertThat(result).isNotNull();
      assertThat(triedPartitions).hasSize(2);
      assertThat(triedPartitions.get(0)).isNotEqualTo(triedPartitions.get(1));
      verify(brokerClient, times(2))
          .sendRequest(
              any(BrokerCreateProcessInstanceWithResultRequest.class),
              eq(java.time.Duration.ofMillis(600_000L)));
    }

    @Test
    void shouldPreserveRoundRobinDispatchStrategyAcrossWithAuthenticationCalls() {
      // given - the services instance has a RequestRetryHandler with round-robin strategy
      final var triedPartitions = new ArrayList<Integer>();
      final var mockResponse = new ProcessInstanceCreationRecord();

      when(brokerClient.sendRequest(any(BrokerCreateProcessInstanceRequest.class)))
          .thenAnswer(
              invocation -> {
                final var brokerRequest =
                    invocation.getArgument(0, BrokerCreateProcessInstanceRequest.class);
                triedPartitions.add(brokerRequest.getPartitionId());
                return CompletableFuture.completedFuture(new BrokerResponse<>(mockResponse));
              });

      // when - calling withAuthentication multiple times and creating instances
      final var auth1 = authentication("token1");
      final var auth2 = authentication("token2");
      final var auth3 = authentication("token3");

      services
          .withAuthentication(auth1)
          .createProcessInstance(createProcessInstanceRequest())
          .join();
      services
          .withAuthentication(auth2)
          .createProcessInstance(createProcessInstanceRequest())
          .join();
      services
          .withAuthentication(auth3)
          .createProcessInstance(createProcessInstanceRequest())
          .join();

      // then - the round robin strategy should distribute requests across different partitions
      // With 3 partitions and round-robin, we expect different partitions to be used
      assertThat(triedPartitions).hasSize(3);
      assertThat(triedPartitions).doesNotHaveDuplicates();
    }

    private Authentication authentication(final String token1) {
      return new Authentication.Builder().token(token1).build();
    }

    private ProcessInstanceServices.ProcessInstanceCreateRequest createProcessInstanceRequest() {
      return new ProcessInstanceServices.ProcessInstanceCreateRequest(
          123L, // processDefinitionKey
          "test-process", // bpmnProcessId
          -1, // version
          null, // variables
          "<default>", // tenantId
          false, // awaitCompletion
          null, // requestTimeout
          null, // operationReference
          List.of(), // startInstructions
          List.of() // fetchVariables
          );
    }

    private ProcessInstanceServices.ProcessInstanceCreateRequest
        createProcessInstanceWithResultRequest(final Long requestTimeout) {
      return new ProcessInstanceServices.ProcessInstanceCreateRequest(
          123L, // processDefinitionKey
          "test-process", // bpmnProcessId
          -1, // version
          null, // variables
          "<default>", // tenantId
          true, // awaitCompletion
          requestTimeout, // requestTimeout
          null, // operationReference
          List.of(), // startInstructions
          List.of() // fetchVariables
          );
    }
  }
}
