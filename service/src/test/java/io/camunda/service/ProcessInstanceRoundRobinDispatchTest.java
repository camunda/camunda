/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.SequenceFlowSearchClient;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedTopologyManager;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ProcessInstanceRoundRobinDispatchTest {

  private static final int PARTITION_COUNT = 3;

  private BrokerClient brokerClient;
  private ProcessInstanceServices services;
  private CamundaAuthentication authentication;

  @BeforeEach
  void setUp() {
    brokerClient = mock(BrokerClient.class);
    when(brokerClient.getTopologyManager()).thenReturn(new StubbedTopologyManager(PARTITION_COUNT));

    final var executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(ForkJoinPool.commonPool());

    authentication = CamundaAuthentication.none();

    services =
        new ProcessInstanceServices(
            brokerClient,
            mock(SecurityContextProvider.class),
            mock(ProcessInstanceSearchClient.class),
            mock(SequenceFlowSearchClient.class),
            mock(IncidentServices.class),
            executorProvider,
            mock(BrokerRequestAuthorizationConverter.class));
  }

  @Test
  void shouldRoundRobinAcrossPartitionsForSameProcessId() {
    // given
    final var partitions = capturePartitions();

    // when — first request warms up the per-definition handler via the global handler;
    // subsequent requests use the per-definition handler and cycle through all partitions
    for (int i = 0; i < PARTITION_COUNT + 1; i++) {
      services.createProcessInstance(createRequest("my-process"), authentication).join();
    }

    // then — the per-definition handler (requests 2..N) hit every partition
    final var perDefPartitions = partitions.subList(1, partitions.size());
    assertThat(perDefPartitions).hasSize(PARTITION_COUNT).doesNotHaveDuplicates();
  }

  @Test
  void shouldDistributeIndependentlyPerProcessId() {
    // given — warm up per-definition handlers for both process IDs
    final var partitions = capturePartitions();
    services.createProcessInstance(createRequest("process-a"), authentication).join();
    services.createProcessInstance(createRequest("process-b"), authentication).join();
    partitions.clear();

    // when — send PARTITION_COUNT requests for each process definition
    for (int i = 0; i < PARTITION_COUNT; i++) {
      services.createProcessInstance(createRequest("process-a"), authentication).join();
    }
    final var partitionsA = List.copyOf(partitions);
    partitions.clear();

    for (int i = 0; i < PARTITION_COUNT; i++) {
      services.createProcessInstance(createRequest("process-b"), authentication).join();
    }
    final var partitionsB = List.copyOf(partitions);

    // then — both process definitions independently cover all partitions
    assertThat(partitionsA).hasSize(PARTITION_COUNT).doesNotHaveDuplicates();
    assertThat(partitionsB).hasSize(PARTITION_COUNT).doesNotHaveDuplicates();
    assertThat(partitionsA).containsExactlyInAnyOrderElementsOf(partitionsB);
  }

  @Test
  void shouldNotPopulateMapOnFailedRequest() {
    // given — first request fails (RuntimeException is not retryable, so no retry)
    final var partitions = new ArrayList<Integer>();
    when(brokerClient.sendRequest(any(BrokerCreateProcessInstanceRequest.class)))
        .thenAnswer(
            invocation -> {
              partitions.add(
                  invocation
                      .getArgument(0, BrokerCreateProcessInstanceRequest.class)
                      .getPartitionId());
              return CompletableFuture.failedFuture(new RuntimeException("broker error"));
            });

    try {
      services.createProcessInstance(createRequest("my-process"), authentication).join();
    } catch (final Exception ignored) {
      // expected
    }

    // given — subsequent requests succeed
    when(brokerClient.sendRequest(any(BrokerCreateProcessInstanceRequest.class)))
        .thenAnswer(
            invocation -> {
              partitions.add(
                  invocation
                      .getArgument(0, BrokerCreateProcessInstanceRequest.class)
                      .getPartitionId());
              return CompletableFuture.completedFuture(
                  new BrokerResponse<>(new ProcessInstanceCreationRecord()));
            });

    // when — send a second request for the same bpmnProcessId
    services.createProcessInstance(createRequest("my-process"), authentication).join();

    // then — the second request used the global handler (not a per-definition one),
    // so its partition is the global handler's next offset, different from the first
    assertThat(partitions).hasSize(2);
    assertThat(partitions.get(0)).isNotEqualTo(partitions.get(1));
  }

  private List<Integer> capturePartitions() {
    final var partitions = new ArrayList<Integer>();
    when(brokerClient.sendRequest(any(BrokerCreateProcessInstanceRequest.class)))
        .thenAnswer(
            invocation -> {
              partitions.add(
                  invocation
                      .getArgument(0, BrokerCreateProcessInstanceRequest.class)
                      .getPartitionId());
              return CompletableFuture.completedFuture(
                  new BrokerResponse<>(new ProcessInstanceCreationRecord()));
            });
    return partitions;
  }

  private static ProcessInstanceCreateRequest createRequest(final String bpmnProcessId) {
    return new ProcessInstanceCreateRequest(
        123L,
        bpmnProcessId,
        -1,
        null,
        "<default>",
        null,
        null,
        null,
        List.of(),
        List.of(),
        null,
        null,
        null);
  }
}
