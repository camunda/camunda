/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.AuditLogSearchClient;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntity.AuditLogCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.search.entities.AuditLogEntity.AuditLogResult;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AuditLogServicesTest {

  private static final AuditLogEntity AUDIT_LOG_ENTITY =
      new AuditLogEntity(
          123L,
          "entityKey",
          AuditLogEntityType.USER,
          AuditLogOperationType.CREATE,
          456L,
          BatchOperationType.ADD_VARIABLE,
          "2024-01-01T00:00:00Z",
          "actorId",
          AuditLogActorType.USER,
          "tenant-1",
          AuditLogResult.SUCCESS,
          "annotation",
          AuditLogCategory.OPERATOR,
          "processDefinitionId",
          789L,
          987L,
          654L,
          321L,
          111L,
          "drg-1",
          222L,
          "decisionDefId",
          333L,
          444L);

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

    final SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);
    when(securityContextProvider.provideSecurityContext(authentication))
        .thenReturn(securityContext);
    when(auditLogSearchClient.withSecurityContext(securityContext))
        .thenReturn(auditLogSearchClient);

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
}
