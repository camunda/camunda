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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.DecisionDefinitionSearchClient;
import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class DecisionDefinitionServiceTest {

  private DecisionDefinitionServices services;
  private DecisionDefinitionSearchClient client;
  private DecisionRequirementSearchClient decisionRequirementSearchClient;
  private SecurityContextProvider securityContextProvider;
  private CamundaAuthentication authentication;

  @BeforeEach
  public void before() {
    client = mock(DecisionDefinitionSearchClient.class);
    decisionRequirementSearchClient = mock(DecisionRequirementSearchClient.class);
    securityContextProvider = mock(SecurityContextProvider.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    when(decisionRequirementSearchClient.withSecurityContext(any()))
        .thenReturn(decisionRequirementSearchClient);
    services =
        new DecisionDefinitionServices(
            mock(BrokerClient.class),
            securityContextProvider,
            client,
            decisionRequirementSearchClient,
            authentication);
  }

  @Test
  public void shouldReturnDecisionDefinition() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchDecisionDefinitions(any())).thenReturn(result);

    final DecisionDefinitionQuery searchQuery =
        SearchQueryBuilders.decisionDefinitionSearchQuery().build();

    // when
    final SearchQueryResult<DecisionDefinitionEntity> searchQueryResult =
        services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnDecisionDefinitionXml() {
    // given
    final var definitionEntity = mock(DecisionDefinitionEntity.class);
    when(definitionEntity.decisionRequirementsKey()).thenReturn(42L);
    when(definitionEntity.decisionDefinitionId()).thenReturn("decId");
    when(client.getDecisionDefinitionByKey(any(Long.class))).thenReturn(definitionEntity);

    final var requirementEntity = mock(DecisionRequirementsEntity.class);
    when(requirementEntity.xml()).thenReturn("<foo>bar</foo>");
    when(decisionRequirementSearchClient.getDecisionRequirementsByKey(any(Long.class), eq(true)))
        .thenReturn(requirementEntity);

    // when
    final var xml = services.getDecisionDefinitionXml(42L);

    // then
    assertThat(xml).isEqualTo("<foo>bar</foo>");
  }

  @Test
  public void shouldGetDecisionDefinitionByKey() {
    // given
    final var definitionEntity = mock(DecisionDefinitionEntity.class);
    when(definitionEntity.decisionDefinitionKey()).thenReturn(42L);
    when(definitionEntity.decisionDefinitionId()).thenReturn("decId");
    final var definitionResult = mock(SearchQueryResult.class);
    when(definitionResult.items()).thenReturn(List.of(definitionEntity));
    when(client.getDecisionDefinitionByKey(any(Long.class))).thenReturn(definitionEntity);

    // when
    final DecisionDefinitionEntity decisionDefinition = services.getByKey(42L);

    // then
    assertThat(decisionDefinition.decisionDefinitionKey()).isEqualTo(42L);
  }
}
