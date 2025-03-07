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

import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AuthorizationServiceTest {

  private AuthorizationServices services;
  private AuthorizationSearchClient client;
  private SecurityConfiguration securityConfiguration;

  @BeforeEach
  public void before() {
    securityConfiguration = new SecurityConfiguration();
    client = mock(AuthorizationSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    services =
        new AuthorizationServices(
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            client,
            null,
            securityConfiguration);
  }

  @Test
  public void emptyQueryReturnsAllResults() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchAuthorizations(any())).thenReturn(result);

    final AuthorizationFilter filter = new AuthorizationFilter.Builder().build();
    final var searchQuery = SearchQueryBuilders.authorizationSearchQuery((b) -> b.filter(filter));

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void noApplicationAuthorizationWhenAuthorizationsEnabled() {
    // given
    securityConfiguration.getAuthorizations().setEnabled(true);

    // when
    final var authorizedApplications = services.getAuthorizedApplications(Set.of());

    // then
    assertThat(authorizedApplications).isEmpty();
  }

  @Test
  public void wildcardApplicationAuthorizationWhenAuthorizationsDisabled() {
    // given
    securityConfiguration.getAuthorizations().setEnabled(false);

    // when
    final var authorizedApplications = services.getAuthorizedApplications(Set.of());

    // then
    assertThat(authorizedApplications).containsExactly("*");
  }
}
