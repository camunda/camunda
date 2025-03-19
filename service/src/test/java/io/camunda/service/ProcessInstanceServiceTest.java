/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.ProcessInstanceServices.NO_PARENT_EXISTS_KEY;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateBatchOperationRequest;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

public final class ProcessInstanceServiceTest {

  private ProcessInstanceServices services;
  private ProcessInstanceSearchClient client;
  private SecurityContextProvider securityContextProvider;
  private Authentication authentication;
  private BrokerClient brokerClient;

  @BeforeEach
  public void before() {
    client = mock(ProcessInstanceSearchClient.class);
    authentication = mock(Authentication.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    securityContextProvider = mock(SecurityContextProvider.class);
    brokerClient = mock(BrokerClient.class);
    services =
        new ProcessInstanceServices(brokerClient, securityContextProvider, client, authentication);
  }

  @Test
  public void shouldReturnProcessInstance() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchProcessInstances(any())).thenReturn(result);

    final ProcessInstanceQuery searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery().build();

    // when
    final SearchQueryResult<ProcessInstanceEntity> searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnProcessInstanceByKey() {
    // given
    final var key = 123L;
    final var entity = mock(ProcessInstanceEntity.class);
    when(entity.processInstanceKey()).thenReturn(key);
    when(entity.processDefinitionId()).thenReturn("processId");
    when(client.searchProcessInstances(any()))
        .thenReturn(new SearchQueryResult(1, List.of(entity), null, null));
    authorizeProcessReadInstance(true, "processId");

    // when
    final var searchQueryResult = services.getByKey(key);

    // then
    assertThat(searchQueryResult.processInstanceKey()).isEqualTo(key);
  }

  @Test
  public void shouldThrownExceptionIfNotFoundByKey() {
    // given
    final var key = 100L;
    when(client.searchProcessInstances(any()))
        .thenReturn(new SearchQueryResult<>(0, List.of(), null, null));

    // when / then
    final var exception =
        assertThrowsExactly(CamundaSearchException.class, () -> services.getByKey(key));
    assertThat(exception.getMessage()).isEqualTo("Process instance with key 100 not found");
    assertThat(exception.getReason()).isEqualTo(CamundaSearchException.Reason.NOT_FOUND);
  }

  @Test
  public void shouldThrownExceptionIfDuplicateFoundByKey() {
    // given
    final var key = 200L;
    final var entity1 = mock(ProcessInstanceEntity.class);
    final var entity2 = mock(ProcessInstanceEntity.class);
    when(client.searchProcessInstances(any()))
        .thenReturn(new SearchQueryResult<>(2, List.of(entity1, entity2), null, null));

    // when / then
    final var exception =
        assertThrowsExactly(CamundaSearchException.class, () -> services.getByKey(key));
    assertThat(exception.getMessage())
        .isEqualTo("Found Process instance with key 200 more than once");
  }

  @Test
  public void getByKeyShouldThrowForbiddenExceptionIfNotAuthorized() {
    // given
    final var entity = mock(ProcessInstanceEntity.class);
    when(entity.processDefinitionId()).thenReturn("processId");
    when(client.searchProcessInstances(any()))
        .thenReturn(new SearchQueryResult<>(1, List.of(entity), null, null));
    authorizeProcessReadInstance(false, "processId");
    // when
    final Executable executeGetByKey = () -> services.getByKey(1L);
    // then
    final var exception = assertThrowsExactly(ForbiddenException.class, executeGetByKey);
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
  }

  @Test
  void shouldCancelProcessInstanceBatchOperationWithResult() {
    // given
    final var filter =
        FilterBuilders.processInstance(b -> b.processDefinitionIds("test-process-definition-id"));

    final long batchOperationKey = 123L;
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(batchOperationKey);
    record.setBatchOperationType(BatchOperationType.PROCESS_CANCELLATION);

    final var captor = ArgumentCaptor.forClass(BrokerCreateBatchOperationRequest.class);
    when(authentication.claims()).thenReturn(emptyMap());
    when(brokerClient.sendRequest(captor.capture()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(record)));

    // when
    final var result = services.cancelProcessInstanceBatchOperationWithResult(filter).join();

    // then
    assertThat(result.getBatchOperationKey()).isEqualTo(batchOperationKey);
    assertThat(result.getBatchOperationType()).isEqualTo(BatchOperationType.PROCESS_CANCELLATION);

    // and our filter got enriched
    final var filterBuffer = captor.getValue().getRequestWriter().getEntityFilterBuffer();
    final var enhancedFilter =
        MsgPackConverter.convertToObject(filterBuffer, ProcessInstanceFilter.class);
    assertThat(enhancedFilter.parentProcessInstanceKeyOperations())
        .containsExactly(Operation.eq(NO_PARENT_EXISTS_KEY));
    assertThat(enhancedFilter.stateOperations())
        .containsExactly(Operation.eq(ProcessInstanceState.ACTIVE.name()));
  }

  private void authorizeProcessReadInstance(final boolean authorize, final String processId) {
    when(securityContextProvider.isAuthorized(
            processId,
            authentication,
            Authorization.of(a -> a.processDefinition().readProcessInstance())))
        .thenReturn(authorize);
  }
}
