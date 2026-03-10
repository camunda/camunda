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

import io.camunda.auth.domain.model.AuthorizationRecord;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.search.AuthorizationFilter;
import io.camunda.auth.domain.model.search.SearchPage;
import io.camunda.auth.domain.model.search.SearchQuery;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OcAuthorizationManagementAdapterTest {

  private final AuthorizationServices authorizationServices = mock(AuthorizationServices.class);
  private final AuthorizationServices authenticatedServices = mock(AuthorizationServices.class);
  private final CamundaAuthenticationProvider authProvider =
      mock(CamundaAuthenticationProvider.class);
  private final CamundaAuthentication authentication = CamundaAuthentication.none();

  private OcAuthorizationManagementAdapter adapter;

  @BeforeEach
  void setUp() {
    when(authProvider.getCamundaAuthentication()).thenReturn(authentication);
    when(authorizationServices.withAuthentication(authentication))
        .thenReturn(authenticatedServices);
    adapter = new OcAuthorizationManagementAdapter(authorizationServices, authProvider);
  }

  @Test
  void findByOwnerDelegatesToAuthorizationServicesSearch() {
    final var entity =
        new AuthorizationEntity(
            100L,
            "alice",
            "USER",
            "PROCESS_DEFINITION",
            (short) 1,
            "process-1",
            null,
            Set.of(PermissionType.CREATE, PermissionType.READ));
    final var queryResult = new SearchQueryResult<>(1L, false, List.of(entity), null, null);
    when(authenticatedServices.search(any(AuthorizationQuery.class))).thenReturn(queryResult);

    final var results = adapter.findByOwner("alice", "USER");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).authorizationKey()).isEqualTo(100L);
    assertThat(results.get(0).ownerId()).isEqualTo("alice");
    assertThat(results.get(0).ownerType()).isEqualTo("USER");
    assertThat(results.get(0).resourceType()).isEqualTo("PROCESS_DEFINITION");
    assertThat(results.get(0).permissionTypes()).containsExactlyInAnyOrder("CREATE", "READ");
  }

  @Test
  void getByKeyDelegatesToAuthorizationServicesGetAuthorization() {
    final var entity =
        new AuthorizationEntity(
            200L, "bob", "USER", "DECISION_DEFINITION", (short) 1, "dec-1", null, Set.of());
    when(authenticatedServices.getAuthorization(200L)).thenReturn(entity);

    final var result = adapter.getByKey(200L);

    assertThat(result.authorizationKey()).isEqualTo(200L);
    assertThat(result.ownerId()).isEqualTo("bob");
    assertThat(result.permissionTypes()).isEmpty();
  }

  @Test
  void createDelegatesToAuthorizationServicesCreateAuthorization() {
    when(authenticatedServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    final var record =
        new AuthorizationRecord(
            0L, "alice", "USER", "PROCESS_DEFINITION", "process-1", Set.of("CREATE", "READ"));
    final var result = adapter.create(record);

    assertThat(result.ownerId()).isEqualTo("alice");
    assertThat(result.permissionTypes()).containsExactlyInAnyOrder("CREATE", "READ");

    final var captor = ArgumentCaptor.forClass(CreateAuthorizationRequest.class);
    verify(authenticatedServices).createAuthorization(captor.capture());
    assertThat(captor.getValue().ownerId()).isEqualTo("alice");
    assertThat(captor.getValue().permissionTypes())
        .containsExactlyInAnyOrder(PermissionType.CREATE, PermissionType.READ);
  }

  @Test
  void deleteDelegatesToAuthorizationServicesDeleteAuthorization() {
    when(authenticatedServices.deleteAuthorization(100L))
        .thenReturn(CompletableFuture.completedFuture(null));

    adapter.delete(100L);

    verify(authenticatedServices).deleteAuthorization(100L);
  }

  @Test
  void searchDelegatesToAuthorizationServicesSearch() {
    final var entity =
        new AuthorizationEntity(
            1L, "alice", "USER", "RESOURCE", (short) 1, "r-1", null, Set.of(PermissionType.READ));
    final var queryResult = new SearchQueryResult<>(1L, false, List.of(entity), null, null);
    when(authenticatedServices.search(any(AuthorizationQuery.class))).thenReturn(queryResult);

    final var filter = new AuthorizationFilter("alice", "USER", null);
    final var query = new SearchQuery<>(filter, null, new SearchPage(0, 10));
    final var result = adapter.search(query);

    assertThat(result.total()).isEqualTo(1L);
    assertThat(result.items().get(0).ownerId()).isEqualTo("alice");
  }

  @Test
  void mapsNullAuthorizationKeyToZero() {
    final var entity =
        new AuthorizationEntity(null, "alice", "USER", "RESOURCE", (short) 1, "r", null, null);
    when(authenticatedServices.getAuthorization(0L)).thenReturn(entity);

    final var result = adapter.getByKey(0L);

    assertThat(result.authorizationKey()).isEqualTo(0L);
  }

  @Test
  void mapsNullPermissionTypesToEmptySet() {
    final var entity =
        new AuthorizationEntity(1L, "alice", "USER", "RESOURCE", (short) 1, "r", null, null);
    when(authenticatedServices.getAuthorization(1L)).thenReturn(entity);

    final var result = adapter.getByKey(1L);

    assertThat(result.permissionTypes()).isEmpty();
  }

  @Test
  void createUnwrapsRuntimeCompletionException() {
    final var rootCause = new IllegalArgumentException("invalid");
    when(authenticatedServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(rootCause));

    final var record =
        new AuthorizationRecord(0L, "alice", "USER", "RESOURCE", "r-1", Set.of("READ"));

    assertThatThrownBy(() -> adapter.create(record))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid");
  }
}
