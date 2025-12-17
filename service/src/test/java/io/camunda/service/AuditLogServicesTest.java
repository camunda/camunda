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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.AuditLogSearchClient;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.authorization.Authorizations;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.time.OffsetDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AuditLogServicesTest {

  private static final AuditLogEntity AUDIT_LOG_ENTITY =
      new AuditLogEntity.Builder()
          .auditLogKey("auditLogKey")
          .entityKey("entityKey")
          .entityType(AuditLogEntity.AuditLogEntityType.USER)
          .operationType(AuditLogEntity.AuditLogOperationType.CREATE)
          .batchOperationKey(456L)
          .batchOperationType(BatchOperationType.ADD_VARIABLE)
          .timestamp(OffsetDateTime.now())
          .actorId("actorId")
          .actorType(AuditLogEntity.AuditLogActorType.USER)
          .tenantId("tenant-1")
          .result(AuditLogEntity.AuditLogOperationResult.SUCCESS)
          .annotation("annotation")
          .category(AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .processDefinitionId("processDefinitionId")
          .processDefinitionKey(789L)
          .processInstanceKey(987L)
          .elementInstanceKey(654L)
          .jobKey(321L)
          .userTaskKey(111L)
          .decisionRequirementsId("drg-1")
          .decisionRequirementsKey(222L)
          .decisionDefinitionId("decisionDefId")
          .decisionDefinitionKey(333L)
          .decisionEvaluationKey(444L)
          .build();

  @Mock private BrokerClient brokerClient;
  @Mock private SecurityContextProvider securityContextProvider;
  @Mock private AuditLogSearchClient auditLogSearchClient;
  @Mock private CamundaAuthentication authentication;
  @Mock private ApiServicesExecutorProvider executorProvider;
  @Mock private BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;

  @Mock private AuditLogQuery query;

  private AuditLogServices auditLogServices;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    when(auditLogSearchClient.withSecurityContext(any())).thenReturn(auditLogSearchClient);

    auditLogServices =
        new AuditLogServices(
            brokerClient,
            securityContextProvider,
            auditLogSearchClient,
            authentication,
            executorProvider,
            brokerRequestAuthorizationConverter);
  }

  @Test
  void shouldDelegateSearchToClient() {
    // given
    final var searchResult = SearchQueryResult.of(AUDIT_LOG_ENTITY);

    when(auditLogSearchClient.searchAuditLogs(query)).thenReturn(searchResult);
    when(query.filter()).thenReturn(FilterBuilders.auditLog().build());

    // when
    final var result = auditLogServices.search(query);

    // then
    assertThat(result).isEqualTo(searchResult);

    final var queryCaptor = ArgumentCaptor.forClass(AuditLogQuery.class);
    verify(auditLogSearchClient).searchAuditLogs(queryCaptor.capture());
    assertThat(queryCaptor.getValue()).isEqualTo(query);
  }

  @Test
  void shouldDelegateGetAuditLogToClient() {
    // given
    final var key = "123";

    when(auditLogSearchClient.getAuditLog(key)).thenReturn(AUDIT_LOG_ENTITY);

    // when
    final var result = auditLogServices.getAuditLog(key);

    // then
    assertThat(result).isEqualTo(AUDIT_LOG_ENTITY);
    verify(auditLogSearchClient).getAuditLog(key);
  }

  @Test
  public void searchShouldThrowForbiddenExceptionIfNotAuthorized() {
    // given
    when(auditLogSearchClient.searchAuditLogs(query))
        .thenThrow(new ResourceAccessDeniedException(Authorizations.AUDIT_LOG_READ_AUTHORIZATION));
    when(query.filter()).thenReturn(FilterBuilders.auditLog().build());

    // when
    final ThrowingCallable executeSearch = () -> auditLogServices.search(query);

    // then
    final var exception =
        (ServiceException)
            assertThatThrownBy(executeSearch).isInstanceOf(ServiceException.class).actual();
    assertThat(exception.getMessage())
        .isEqualTo("Unauthorized to perform operation 'READ' on resource 'AUDIT_LOG'");
    assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
  }

  @Test
  public void getAuditLogShouldThrowForbiddenExceptionIfNotAuthorized() {
    // given
    final var key = "123";

    when(auditLogSearchClient.getAuditLog(key))
        .thenThrow(new ResourceAccessDeniedException(Authorizations.AUDIT_LOG_READ_AUTHORIZATION));

    // when
    final ThrowingCallable executeGetByKey = () -> auditLogServices.getAuditLog(key);

    // then
    final var exception =
        (ServiceException)
            assertThatThrownBy(executeGetByKey).isInstanceOf(ServiceException.class).actual();
    assertThat(exception.getMessage())
        .isEqualTo("Unauthorized to perform operation 'READ' on resource 'AUDIT_LOG'");
    assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
  }
}
