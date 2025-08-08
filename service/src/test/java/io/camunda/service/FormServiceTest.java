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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.FormSearchClient;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class FormServiceTest {

  private FormServices services;
  private FormSearchClient client;

  @BeforeEach
  public void before() {
    client = mock(FormSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    services =
        new FormServices(
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            client,
            null,
            mock(ApiServicesExecutorProvider.class));
  }

  @Test
  public void shouldReturnFormByKey() {
    // given
    final var searchQuery =
        SearchQueryBuilders.formSearchQuery().filter(f -> f.formKeys(1L)).build();

    final var result = mock(SearchQueryResult.class);
    when(client.searchForms(any())).thenReturn(result);

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnByFormId() {
    // given
    final var searchQuery =
        SearchQueryBuilders.formSearchQuery().filter(f -> f.formIds("formId")).build();

    final var result = mock(SearchQueryResult.class);
    when(client.searchForms(any())).thenReturn(result);

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }
}
