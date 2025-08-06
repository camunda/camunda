/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.DecisionDefinitionSearchClient;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.authorization.Authorizations;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class DecisionDefinitionServiceTest {

  private DecisionDefinitionServices services;
  private DecisionDefinitionSearchClient client;
  private DecisionRequirementsServices decisionRequirementServices;

  @BeforeEach
  public void before() {
    client = mock(DecisionDefinitionSearchClient.class);
    decisionRequirementServices = mock(DecisionRequirementsServices.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    when(decisionRequirementServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(decisionRequirementServices);
    services =
        new DecisionDefinitionServices(
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            client,
            decisionRequirementServices,
            null,
            mock(ApiServicesExecutorProvider.class));
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
    when(client.getDecisionDefinition(eq(42L))).thenReturn(definitionEntity);
    when(decisionRequirementServices.getDecisionRequirementsXml(any(Long.class)))
        .thenReturn("<foo>bar</foo>");

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
    when(client.getDecisionDefinition(eq(42L))).thenReturn(definitionEntity);

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
    when(client.getDecisionDefinition(any(Long.class)))
        .thenThrow(
            new ResourceAccessDeniedException(
                Authorizations.DECISION_DEFINITION_READ_AUTHORIZATION));

    // when
    final ThrowingCallable executable = () -> services.getByKey(1L);

    // then
    final var exception =
        assertThatExceptionOfType(ServiceException.class).isThrownBy(executable).actual();
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
    when(client.getDecisionDefinition(any(Long.class)))
        .thenThrow(
            new ResourceAccessDeniedException(
                Authorizations.DECISION_DEFINITION_READ_AUTHORIZATION));

    // when
    final ThrowingCallable executable = () -> services.getDecisionDefinitionXml(1L);

    // then
    final var exception =
        assertThatExceptionOfType(ServiceException.class).isThrownBy(executable).actual();
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_DECISION_DEFINITION' on resource 'DECISION_DEFINITION'");
    assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
    verify(decisionRequirementServices, never()).getDecisionRequirementsXml(any(Long.class));
  }
}
