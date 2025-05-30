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
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
import java.util.Set;
import org.assertj.core.util.Arrays;
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

  @Test
  public void shouldReturnSingleAuthorizationForGet() {
    // given
    final var entity = mock(AuthorizationEntity.class);
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array(), Arrays.array());
    when(client.searchAuthorizations(any())).thenReturn(result);

    // when
    final var searchQueryResult = services.findAuthorization(entity.authorizationKey());

    // then
    assertThat(searchQueryResult).contains(entity);
  }

  @Test
  public void shouldThrownExceptionIfAuthorizationNotFound() {
    // given
    final var authorizationKey = 100L;
    when(client.searchAuthorizations(any()))
        .thenReturn(new SearchQueryResult<>(0, List.of(), null, null));

    // when / then
    assertThat(services.findAuthorization(authorizationKey)).isEmpty();
  }
}
