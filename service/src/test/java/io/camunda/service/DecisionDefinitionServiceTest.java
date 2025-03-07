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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.DecisionDefinitionSearchClient;
import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.query.DecisionDefinitionQuery;
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
import org.junit.jupiter.api.function.Executable;

public final class DecisionDefinitionServiceTest {

  private DecisionDefinitionServices services;
  private DecisionDefinitionSearchClient client;
  private DecisionRequirementSearchClient decisionRequirementSearchClient;
  private SecurityContextProvider securityContextProvider;
  private Authentication authentication;

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
    when(client.searchDecisionDefinitions(any()))
        .thenReturn(new SearchQueryResult<>(1, List.of(definitionEntity), null, null));

    final var requirementEntity = mock(DecisionRequirementsEntity.class);
    when(requirementEntity.xml()).thenReturn("<foo>bar</foo>");
    when(decisionRequirementSearchClient.searchDecisionRequirements(any()))
        .thenReturn(new SearchQueryResult<>(1, List.of(requirementEntity), null, null));
    when(securityContextProvider.isAuthorized(
            "decId",
            authentication,
            Authorization.of(a -> a.decisionDefinition().readDecisionDefinition())))
        .thenReturn(true);

    // when
    final var xml = services.getDecisionDefinitionXml(42L);

    // then
    assertThat(xml).isEqualTo("<foo>bar</foo>");
  }

  @Test
  public void shouldThrowNotFoundExceptionOnUnmatchedDecisionKey() {
    // given
    when(client.searchDecisionDefinitions(any()))
        .thenReturn(new SearchQueryResult<>(0, List.of(), null, null));

    // then
    final var exception =
        assertThrows(CamundaSearchException.class, () -> services.getDecisionDefinitionXml(1L));
    assertThat(exception.getMessage()).isEqualTo("Decision definition with key 1 not found");
    assertThat(exception.getReason()).isEqualTo(CamundaSearchException.Reason.NOT_FOUND);
    verify(client).searchDecisionDefinitions(any(DecisionDefinitionQuery.class));
    verify(decisionRequirementSearchClient, never())
        .searchDecisionRequirements(any(DecisionRequirementsQuery.class));
  }

  @Test
  public void shouldThrowNotFoundExceptionOnUnmatchedDecisionRequirementsKey() {
    // given
    final var definitionEntity = mock(DecisionDefinitionEntity.class);
    when(definitionEntity.decisionRequirementsKey()).thenReturn(1L);
    when(definitionEntity.decisionDefinitionId()).thenReturn("decId");
    final var definitionResult = mock(SearchQueryResult.class);
    when(definitionResult.items()).thenReturn(List.of(definitionEntity));
    when(client.searchDecisionDefinitions(any()))
        .thenReturn(new SearchQueryResult<>(1, List.of(definitionEntity), null, null));
    when(decisionRequirementSearchClient.searchDecisionRequirements(any()))
        .thenReturn(new SearchQueryResult<>(0, List.of(), null, null));
    when(securityContextProvider.isAuthorized(
            "decId",
            authentication,
            Authorization.of(a -> a.decisionDefinition().readDecisionDefinition())))
        .thenReturn(true);

    // then
    final var exception =
        assertThrows(CamundaSearchException.class, () -> services.getDecisionDefinitionXml(1L));
    assertThat(exception.getReason()).isEqualTo(CamundaSearchException.Reason.NOT_FOUND);
    assertThat(exception.getMessage()).isEqualTo("Decision requirements with key 1 not found");
  }

  @Test
  public void shouldGetDecisionDefinitionByKey() {
    // given
    final var definitionEntity = mock(DecisionDefinitionEntity.class);
    when(definitionEntity.decisionDefinitionKey()).thenReturn(42L);
    when(definitionEntity.decisionDefinitionId()).thenReturn("decId");
    final var definitionResult = mock(SearchQueryResult.class);
    when(definitionResult.items()).thenReturn(List.of(definitionEntity));
    when(client.searchDecisionDefinitions(any()))
        .thenReturn(new SearchQueryResult(1, List.of(definitionEntity), null, null));
    when(securityContextProvider.isAuthorized(
            "decId",
            authentication,
            Authorization.of(a -> a.decisionDefinition().readDecisionDefinition())))
        .thenReturn(true);

    // when
    final DecisionDefinitionEntity decisionDefinition = services.getByKey(42L);

    // then
    assertThat(decisionDefinition.decisionDefinitionKey()).isEqualTo(42L);
  }

  @Test
  void shouldGetByKeyThrowForbiddenExceptionOnUnauthorizedDecisionKey() {
    // given
    final var definitionEntity = mock(DecisionDefinitionEntity.class);
    when(definitionEntity.decisionDefinitionId()).thenReturn("decId");
    final var definitionResult = mock(SearchQueryResult.class);
    when(definitionResult.items()).thenReturn(List.of(definitionEntity));
    when(client.searchDecisionDefinitions(any()))
        .thenReturn(new SearchQueryResult(1, List.of(definitionEntity), null, null));
    when(securityContextProvider.isAuthorized(
            "decId",
            authentication,
            Authorization.of(a -> a.decisionDefinition().readDecisionDefinition())))
        .thenReturn(false);

    // when
    final Executable executable = () -> services.getByKey(1L);

    // then
    final var exception = assertThrows(ForbiddenException.class, executable);
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_DECISION_DEFINITION' on resource 'DECISION_DEFINITION'");
  }

  @Test
  void shouldGetXmlThrowForbiddenExceptionOnUnauthorizedDecisionKey() {
    // given
    final var definitionEntity = mock(DecisionDefinitionEntity.class);
    when(definitionEntity.decisionDefinitionId()).thenReturn("decId");
    final var definitionResult = mock(SearchQueryResult.class);
    when(definitionResult.items()).thenReturn(List.of(definitionEntity));
    when(client.searchDecisionDefinitions(any()))
        .thenReturn(new SearchQueryResult(1, List.of(definitionEntity), null, null));
    when(securityContextProvider.isAuthorized(
            "decId",
            authentication,
            Authorization.of(a -> a.decisionDefinition().readDecisionDefinition())))
        .thenReturn(false);

    // when
    final Executable executable = () -> services.getDecisionDefinitionXml(1L);

    // then
    final var exception = assertThrows(ForbiddenException.class, executable);
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_DECISION_DEFINITION' on resource 'DECISION_DEFINITION'");
    verify(decisionRequirementSearchClient, never())
        .searchDecisionRequirements(any(DecisionRequirementsQuery.class));
  }
}
