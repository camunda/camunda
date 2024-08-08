/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.service.entities.DecisionDefinitionEntity;
import io.camunda.service.entities.DecisionRequirementsEntity;
import io.camunda.service.exception.NotFoundException;
import io.camunda.service.query.filter.DecisionDefinitionSearchQueryStub;
import io.camunda.service.query.filter.DecisionRequirementsSearchQueryStub;
import io.camunda.service.util.StubbedCamundaSearchClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DecisionDefinitionServicesTest {

  private DecisionDefinitionServices services;
  private StubbedCamundaSearchClient client;
  private DecisionDefinitionSearchQueryStub decisionDefinitionSearchQueryStub;
  private DecisionRequirementsSearchQueryStub decisionRequirementsSearchQueryStub;

  @BeforeEach
  public void before() {
    client = spy(new StubbedCamundaSearchClient());
    decisionDefinitionSearchQueryStub = new DecisionDefinitionSearchQueryStub();
    decisionDefinitionSearchQueryStub.registerWith(client);
    decisionRequirementsSearchQueryStub = new DecisionRequirementsSearchQueryStub();
    decisionRequirementsSearchQueryStub.registerWith(client);
    services = new DecisionDefinitionServices(null, client);
  }

  @Test
  public void shouldReturnDecisionDefinitionXml() {
    // given
    final Long decisionKey = 123L;
    final Long decisionRequirementsKey = 124L;

    // when
    final String decisionDefinitionXml = services.getDecisionDefinitionXml(decisionKey);

    // then
    assertThat(decisionDefinitionXml).isEqualTo("<xml/>");
    verify(client)
        .search(
            SearchQueryRequest.of(
                b ->
                    b.index("operate-decision-8.3.0_alias")
                        .query(q -> q.term(t -> t.field("key").value(decisionKey)))
                        .sort(s -> s.field(f -> f.field("key").asc()))),
            DecisionDefinitionEntity.class);
    verify(client)
        .search(
            SearchQueryRequest.of(
                b ->
                    b.index("operate-decision-requirements-8.3.0_alias")
                        .source(
                            s -> s.filter(f -> f.includes(List.of("xml")).excludes(emptyList())))
                        .query(q -> q.term(t -> t.field("key").value(decisionRequirementsKey)))
                        .sort(s -> s.field(f -> f.field("key").asc()))),
            DecisionRequirementsEntity.class);
  }

  @Test
  public void shouldThorwNotFoundExceptionOnUnmatchedDecisionKey() {
    // given
    final Long decisionKey = 1L;
    decisionDefinitionSearchQueryStub.setReturnEmptyResults(true);

    // then
    final var exception =
        assertThrows(NotFoundException.class, () -> services.getDecisionDefinitionXml(decisionKey));
    assertThat(exception.getMessage())
        .isEqualTo("DecisionDefinition with decisionKey=1 cannot be found");
    verify(client).search(any(SearchQueryRequest.class), eq(DecisionDefinitionEntity.class));
    verify(client, never())
        .search(any(SearchQueryRequest.class), eq(DecisionRequirementsEntity.class));
  }

  @Test
  public void shouldThorwNotFoundExceptionOnUnmatchedDecisionRequirementsKey() {
    // given
    final Long decisionKey = 1L;
    decisionRequirementsSearchQueryStub.setReturnEmptyResults(true);

    // then
    final var exception =
        assertThrows(NotFoundException.class, () -> services.getDecisionDefinitionXml(decisionKey));
    assertThat(exception.getMessage())
        .isEqualTo("DecisionRequirements with decisionRequirementsKey=124 cannot be found");
  }
}
