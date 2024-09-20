/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.entities.ProcessDefinitionEntity;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class ProcessDefinitionFilterTest {

  private ProcessDefinitionServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new ProcessDefinitionSearchQueryStub().registerWith(client);
    services = new ProcessDefinitionServices(null, client, null, null);
  }

  @Test
  public void shouldReturnProcessDefinition() {
    // given
    final var searchQuery = SearchQueryBuilders.processDefinitionSearchQuery().build();

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult.total()).isEqualTo(1);
    assertThat(searchQueryResult.items()).hasSize(1);
    final ProcessDefinitionEntity item = searchQueryResult.items().getFirst();
    assertThat(item.key()).isEqualTo(1L);
  }
}
