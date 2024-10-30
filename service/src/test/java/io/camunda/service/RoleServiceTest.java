/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.RoleSearchClient;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.filter.RoleFilter;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RoleServiceTest {

  private RoleServices services;
  private RoleSearchClient client;

  @BeforeEach
  public void before() {
    client = mock(RoleSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    services =
        new RoleServices(
            mock(BrokerClient.class), mock(SecurityContextProvider.class), client, null);
  }

  @Test
  public void shouldEmptyQueryReturnRoles() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchRoles(any())).thenReturn(result);

    final RoleFilter filter = new RoleFilter.Builder().build();
    final var searchQuery = SearchQueryBuilders.roleSearchQuery((b) -> b.filter(filter));

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnSingleVariable() {
    // given
    final var entity = mock(RoleEntity.class);
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array());
    when(client.searchRoles(any())).thenReturn(result);
  }

  @Test
  public void shouldReturnSingleVariableForGet() {
    // given
    final var entity = mock(RoleEntity.class);
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array());
    when(client.searchRoles(any())).thenReturn(result);

    // when
    final var searchQueryResult = services.getByRoleKey(1L);

    // then
    assertThat(searchQueryResult).isEqualTo(entity);
  }

  @Test
  public void shouldThrownExceptionIfNotFoundByKey() {
    // given
    final var key = 100L;
    when(client.searchRoles(any())).thenReturn(new SearchQueryResult(0, List.of(), null));

    // when / then
    final var exception =
        assertThrowsExactly(NotFoundException.class, () -> services.getByRoleKey(key));
    assertThat(exception.getMessage()).isEqualTo("Role with roleKey 100 not found");
  }
}
