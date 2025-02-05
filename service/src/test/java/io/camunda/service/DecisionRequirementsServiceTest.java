/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class DecisionRequirementsServiceTest {

  private DecisionRequirementsServices services;
  private DecisionRequirementSearchClient client;
  private SecurityContextProvider securityContextProvider;
  private Authentication authentication;

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
    when(client.searchDecisionRequirements(any()))
        .thenReturn(new SearchQueryResult<>(1, List.of(decisionRequirementEntity), null, null));
    when(securityContextProvider.isAuthorized(
            "decReqId",
            authentication,
            Authorization.of(a -> a.decisionRequirementsDefinition().read())))
        .thenReturn(true);

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
    final var decisionRequirementResult = mock(SearchQueryResult.class);
    when(decisionRequirementResult.items()).thenReturn(List.of(decisionRequirementEntity));
    when(decisionRequirementResult.total()).thenReturn(1L);
    when(client.searchDecisionRequirements(any())).thenReturn(decisionRequirementResult);
    when(securityContextProvider.isAuthorized(
            "decReqId",
            authentication,
            Authorization.of(a -> a.decisionRequirementsDefinition().read())))
        .thenReturn(true);

    // when
    final String expectedXml = "<xml/>";
    final var searchQueryResult = services.getDecisionRequirementsXml(124L);

    // then
    assertThat(searchQueryResult).isEqualTo(expectedXml);
  }

  @Test
  void shouldGetByKeyThrowForbiddenExceptionForUnauthorizedDecisionReq() {
    // given
    final var decisionRequirementEntity = mock(DecisionRequirementsEntity.class);
    when(decisionRequirementEntity.decisionRequirementsKey()).thenReturn(124L);
    when(decisionRequirementEntity.decisionRequirementsId()).thenReturn("decReqId");
    final var decisionRequirementResult = mock(SearchQueryResult.class);
    when(decisionRequirementResult.items()).thenReturn(List.of(decisionRequirementEntity));
    when(decisionRequirementResult.total()).thenReturn(1L);
    when(client.searchDecisionRequirements(any())).thenReturn(decisionRequirementResult);
    when(securityContextProvider.isAuthorized(
            "decReqId",
            authentication,
            Authorization.of(a -> a.decisionRequirementsDefinition().read())))
        .thenReturn(false);

    // then
    final var exception = assertThrows(ForbiddenException.class, () -> services.getByKey(124L));
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ' on resource 'DECISION_REQUIREMENTS_DEFINITION'");
  }

  @Test
  void shouldGetXmlThrowForbiddenExceptionForUnauthorizedDecisionReq() {
    // given
    final var decisionRequirementEntity = mock(DecisionRequirementsEntity.class);
    when(decisionRequirementEntity.decisionRequirementsKey()).thenReturn(124L);
    when(decisionRequirementEntity.decisionRequirementsId()).thenReturn("decReqId");
    final var decisionRequirementResult = mock(SearchQueryResult.class);
    when(decisionRequirementResult.items()).thenReturn(List.of(decisionRequirementEntity));
    when(decisionRequirementResult.total()).thenReturn(1L);
    when(client.searchDecisionRequirements(any())).thenReturn(decisionRequirementResult);
    when(securityContextProvider.isAuthorized(
            "decReqId",
            authentication,
            Authorization.of(a -> a.decisionRequirementsDefinition().read())))
        .thenReturn(false);

    // then
    final var exception =
        assertThrows(ForbiddenException.class, () -> services.getDecisionRequirementsXml(124L));
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ' on resource 'DECISION_REQUIREMENTS_DEFINITION'");
  }
}
