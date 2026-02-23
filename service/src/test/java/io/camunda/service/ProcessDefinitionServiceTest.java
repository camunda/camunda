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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.entities.ProcessDefinitionInstanceStatisticsEntity;
import io.camunda.search.entities.ProcessDefinitionInstanceVersionStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProcessDefinitionServiceTest {

  private ProcessDefinitionServices services;
  private ProcessDefinitionSearchClient processDefinitionSearchClient;
  private SecurityContextProvider securityContextProvider;
  private CamundaAuthentication authentication;
  private BrokerClient brokerClient;
  private FormServices formServices;
  private ApiServicesExecutorProvider executorProvider;
  private BrokerRequestAuthorizationConverter authorizationConverter;

  @BeforeEach
  public void before() {
    processDefinitionSearchClient = mock(ProcessDefinitionSearchClient.class);
    when(processDefinitionSearchClient.withSecurityContext(any()))
        .thenReturn(processDefinitionSearchClient);
    securityContextProvider = mock(SecurityContextProvider.class);
    authentication = CamundaAuthentication.none();
    brokerClient = mock(BrokerClient.class);
    formServices = mock(FormServices.class);
    executorProvider = mock(ApiServicesExecutorProvider.class);
    authorizationConverter = mock(BrokerRequestAuthorizationConverter.class);

    services =
        new ProcessDefinitionServices(
            brokerClient,
            securityContextProvider,
            processDefinitionSearchClient,
            formServices,
            authentication,
            executorProvider,
            authorizationConverter);
  }

  @Test
  public void shouldReturnProcessDefinitionInstanceStatistics() {
    // given
    final var statsEntity =
        new ProcessDefinitionInstanceStatisticsEntity(
            "complexProcess", "<default>", "Complex process", true, 5L, 10L);
    final var statsResult =
        new SearchQueryResult.Builder<ProcessDefinitionInstanceStatisticsEntity>()
            .total(1L)
            .items(List.of(statsEntity))
            .startCursor(null)
            .endCursor(null)
            .build();
    when(processDefinitionSearchClient.processDefinitionInstanceStatistics(any()))
        .thenReturn(statsResult);

    final var query = new ProcessDefinitionInstanceStatisticsQuery.Builder().build();

    // when
    final var result = services.getProcessDefinitionInstanceStatistics(query);

    // then
    assertThat(result).isEqualTo(statsResult);
    verify(processDefinitionSearchClient).processDefinitionInstanceStatistics(query);
  }

  @Test
  public void shouldReturnProcessDefinitionInstanceVersionStatistics() {
    // given
    final var processDefinitionId = "complexProcess";
    final var statsEntity =
        new ProcessDefinitionInstanceVersionStatisticsEntity(
            processDefinitionId, 1L, 2, "Complex process", "<default>", 3L, 4L);
    final var statsResult =
        new SearchQueryResult.Builder<ProcessDefinitionInstanceVersionStatisticsEntity>()
            .total(1L)
            .items(List.of(statsEntity))
            .startCursor(null)
            .endCursor(null)
            .build();

    when(processDefinitionSearchClient.processDefinitionInstanceVersionStatistics(any()))
        .thenReturn(statsResult);

    final var query =
        ProcessDefinitionInstanceVersionStatisticsQuery.of(
            b -> b.filter(f -> f.processDefinitionId(processDefinitionId)));

    // when
    final var result = services.searchProcessDefinitionInstanceVersionStatistics(query);

    // then
    assertThat(result).isEqualTo(statsResult);

    verify(processDefinitionSearchClient)
        .processDefinitionInstanceVersionStatistics(
            argThat(
                q ->
                    q != null
                        && q.filter() != null
                        && q.filter().processDefinitionId() != null
                        && processDefinitionId.equals(q.filter().processDefinitionId())
                        && q.filter().tenantId() == null));
  }
}
