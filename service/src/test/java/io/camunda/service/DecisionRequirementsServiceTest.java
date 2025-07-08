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

import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class DecisionRequirementsServiceTest {

  private DecisionRequirementsServices services;
  private DecisionRequirementSearchClient client;
  private SecurityContextProvider securityContextProvider;
  private CamundaAuthentication authentication;

  @BeforeEach
  public void before() {
    client = mock(DecisionRequirementSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    securityContextProvider = mock(SecurityContextProvider.class);
    services =
        new DecisionRequirementsServices(
            mock(BrokerClient.class), securityContextProvider, client, authentication);
  }

  @Test
  public void shouldReturnDecisionRequirements() {
    // given
    final DecisionRequirementsQuery searchQuery =
        SearchQueryBuilders.decisionRequirementsSearchQuery().build();

    // when
    final var result = mock(SearchQueryResult.class);
    when(client.searchDecisionRequirements(any())).thenReturn(result);
    final SearchQueryResult<DecisionRequirementsEntity> searchQueryResult =
        services.search(searchQuery);

    // then
    assertThat(result).isEqualTo(searchQueryResult);
  }

  @Test
  public void shouldReturnDecisionRequirementByKey() {
    // given
    final var decisionRequirementEntity = mock(DecisionRequirementsEntity.class);
    when(decisionRequirementEntity.decisionRequirementsKey()).thenReturn(124L);
    when(decisionRequirementEntity.decisionRequirementsId()).thenReturn("decReqId");
    when(client.getDecisionRequirementsByKey(any(Long.class), eq(false)))
        .thenReturn(decisionRequirementEntity);

    // when
    final var searchQueryResult = services.getByKey(124L);

    // then
    final DecisionRequirementsEntity item = searchQueryResult;
    assertThat(item.decisionRequirementsKey()).isEqualTo(124L);
  }

  @Test
  public void shouldReturnDecisionRequirementsXmlByKey() {
    // given
    final var decisionRequirementEntity = mock(DecisionRequirementsEntity.class);
    when(decisionRequirementEntity.decisionRequirementsKey()).thenReturn(124L);
    when(decisionRequirementEntity.decisionRequirementsId()).thenReturn("decReqId");
    when(decisionRequirementEntity.xml()).thenReturn("<xml/>");
    when(client.getDecisionRequirementsByKey(any(Long.class), eq(true)))
        .thenReturn(decisionRequirementEntity);

    // when
    final String expectedXml = "<xml/>";
    final var searchQueryResult = services.getDecisionRequirementsXml(124L);

    // then
    assertThat(searchQueryResult).isEqualTo(expectedXml);
  }
}
