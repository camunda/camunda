/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.search.MappingRuleFilter;
import io.camunda.auth.domain.model.search.SearchPage;
import io.camunda.auth.domain.model.search.SearchQuery;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.MappingRuleServices.MappingRuleDTO;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OcMappingRuleManagementAdapterTest {

  private final MappingRuleServices mappingRuleServices = mock(MappingRuleServices.class);
  private final MappingRuleServices authenticatedServices = mock(MappingRuleServices.class);
  private final CamundaAuthenticationProvider authProvider =
      mock(CamundaAuthenticationProvider.class);
  private final CamundaAuthentication authentication = CamundaAuthentication.none();

  private OcMappingRuleManagementAdapter adapter;

  @BeforeEach
  void setUp() {
    when(authProvider.getCamundaAuthentication()).thenReturn(authentication);
    when(mappingRuleServices.withAuthentication(authentication)).thenReturn(authenticatedServices);
    adapter = new OcMappingRuleManagementAdapter(mappingRuleServices, authProvider);
  }

  @Test
  void getByIdDelegatesToMappingRuleServicesGetMappingRule() {
    final var entity = new MappingRuleEntity("mr-1", 3L, "groups", "admin", "Admin Mapping");
    when(authenticatedServices.getMappingRule("mr-1")).thenReturn(entity);

    final var result = adapter.getById("mr-1");

    assertThat(result.mappingRuleKey()).isEqualTo(3L);
    assertThat(result.mappingRuleId()).isEqualTo("mr-1");
    assertThat(result.claimName()).isEqualTo("groups");
    assertThat(result.claimValue()).isEqualTo("admin");
    assertThat(result.name()).isEqualTo("Admin Mapping");
  }

  @Test
  void createDelegatesToMappingRuleServicesCreateMappingRule() {
    when(authenticatedServices.createMappingRule(any(MappingRuleDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    final var result = adapter.create("mr-2", "roles", "editor", "Editor Mapping");

    assertThat(result.mappingRuleId()).isEqualTo("mr-2");
    assertThat(result.claimName()).isEqualTo("roles");
    assertThat(result.claimValue()).isEqualTo("editor");
    assertThat(result.name()).isEqualTo("Editor Mapping");

    final var captor = ArgumentCaptor.forClass(MappingRuleDTO.class);
    verify(authenticatedServices).createMappingRule(captor.capture());
    assertThat(captor.getValue().claimName()).isEqualTo("roles");
    assertThat(captor.getValue().claimValue()).isEqualTo("editor");
    assertThat(captor.getValue().name()).isEqualTo("Editor Mapping");
    assertThat(captor.getValue().mappingRuleId()).isEqualTo("mr-2");
  }

  @Test
  void deleteDelegatesToMappingRuleServicesDeleteMappingRule() {
    when(authenticatedServices.deleteMappingRule("mr-1"))
        .thenReturn(CompletableFuture.completedFuture(null));

    adapter.delete("mr-1");

    verify(authenticatedServices).deleteMappingRule("mr-1");
  }

  @Test
  void searchDelegatesToMappingRuleServicesSearch() {
    final var entity = new MappingRuleEntity("mr-1", 1L, "groups", "admin", "Admin");
    final var queryResult = new SearchQueryResult<>(1L, false, List.of(entity), null, null);
    when(authenticatedServices.search(any(MappingRuleQuery.class))).thenReturn(queryResult);

    final var filter = new MappingRuleFilter("mr-1", "groups", "admin", null);
    final var query = new SearchQuery<>(filter, null, new SearchPage(0, 10));
    final var result = adapter.search(query);

    assertThat(result.total()).isEqualTo(1L);
    assertThat(result.items().get(0).mappingRuleId()).isEqualTo("mr-1");
  }

  @Test
  void mapsNullMappingRuleKeyToZero() {
    final var entity = new MappingRuleEntity("mr-x", null, "claim", "val", "name");
    when(authenticatedServices.getMappingRule("mr-x")).thenReturn(entity);

    final var result = adapter.getById("mr-x");

    assertThat(result.mappingRuleKey()).isEqualTo(0L);
  }

  @Test
  void createUnwrapsRuntimeCompletionException() {
    final var rootCause = new IllegalArgumentException("duplicate");
    when(authenticatedServices.createMappingRule(any(MappingRuleDTO.class)))
        .thenReturn(CompletableFuture.failedFuture(rootCause));

    assertThatThrownBy(() -> adapter.create("mr-1", "groups", "admin", "name"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("duplicate");
  }
}
