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
import static org.mockito.Mockito.when;

import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.authorization.Authorizations;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class DecisionRequirementsServiceTest {

  private DecisionRequirementsServices services;
  private DecisionRequirementSearchClient client;

  @BeforeEach
  public void before() {
    client = mock(DecisionRequirementSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    services =
        new DecisionRequirementsServices(
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            client,
            null,
            mock(ApiServicesExecutorProvider.class));
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
    when(client.getDecisionRequirements(eq(124L), eq(false))).thenReturn(decisionRequirementEntity);

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
    when(client.getDecisionRequirements(eq(124L), eq(true))).thenReturn(decisionRequirementEntity);

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
    when(client.getDecisionRequirements(eq(124L), eq(false)))
        .thenThrow(
            new ResourceAccessDeniedException(
                Authorizations.DECISION_REQUIREMENTS_READ_AUTHORIZATION));

    // then
    final var exception =
        assertThatExceptionOfType(ServiceException.class)
            .isThrownBy(() -> services.getByKey(124L))
            .actual();
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
    when(client.getDecisionRequirements(eq(124L), eq(true)))
        .thenThrow(
            new ResourceAccessDeniedException(
                Authorizations.DECISION_REQUIREMENTS_READ_AUTHORIZATION));

    // then
    final var exception =
        assertThatExceptionOfType(ServiceException.class)
            .isThrownBy(() -> services.getDecisionRequirementsXml(124L))
            .actual();
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ' on resource 'DECISION_REQUIREMENTS_DEFINITION'");
    assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
  }
}
