/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.JobSearchClient;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JobServiceTest {

  private JobServices<SearchQueryResult<JobEntity>> services;
  private JobSearchClient client;

  @BeforeEach
  public void before() {
    client = mock(JobSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    services =
        new JobServices<>(
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            null,
            client,
            null,
            mock(ApiServicesExecutorProvider.class));
  }

  @Test
  public void shouldReturnJob() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchJobs(any())).thenReturn(result);

    final var searchQuery = SearchQueryBuilders.jobSearchQuery().build();

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }
}
