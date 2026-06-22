/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.PhysicalTenantSearchClientReaders;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.Authorization;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.api.model.authz.ResourceType;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuthorizationRepositoryAdapterTest {

  private final AuthorizationReader authorizationReader = mock(AuthorizationReader.class);
  private final SearchClientReaders searchClientReaders = mock(SearchClientReaders.class);
  private final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders =
      new PhysicalTenantSearchClientReaders(Map.of("default", searchClientReaders));
  private final AuthorizationRepositoryAdapter adapter =
      new AuthorizationRepositoryAdapter(physicalTenantSearchClientReaders);

  @BeforeEach
  void setUp() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    when(searchClientReaders.authorizationReader()).thenReturn(authorizationReader);
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldReturnAuthorizationsForPrincipal() {
    // given
    final var authentication = CamundaAuthentication.of(b -> b.user("alice"));
    final var entity =
        new AuthorizationEntity(
            1L,
            "alice",
            "USER",
            ResourceType.COMPONENT.name(),
            null,
            "operate",
            null,
            new java.util.HashSet<>(Set.of(PermissionType.ACCESS)));
    when(authorizationReader.search(any(AuthorizationQuery.class), any(ResourceAccessChecks.class)))
        .thenReturn(SearchQueryResult.<AuthorizationEntity>of(b -> b.items(List.of(entity))));

    // when
    final Set<Authorization> result =
        adapter.findAuthorizations(authentication, ResourceType.COMPONENT);

    // then
    assertThat(result)
        .containsExactly(
            new Authorization(ResourceType.COMPONENT, "operate", Set.of(PermissionType.ACCESS)));
  }

  @Test
  void shouldUnionPermissionsForSameResource() {
    // given
    final var authentication = CamundaAuthentication.of(b -> b.user("alice"));
    final var entity1 =
        new AuthorizationEntity(
            1L,
            "alice",
            "USER",
            ResourceType.PROCESS_DEFINITION.name(),
            null,
            "*",
            null,
            new java.util.HashSet<>(Set.of(PermissionType.READ_PROCESS_DEFINITION)));
    final var entity2 =
        new AuthorizationEntity(
            2L,
            "admins",
            "ROLE",
            ResourceType.PROCESS_DEFINITION.name(),
            null,
            "*",
            null,
            new java.util.HashSet<>(Set.of(PermissionType.READ_PROCESS_INSTANCE)));
    when(authorizationReader.search(any(AuthorizationQuery.class), any(ResourceAccessChecks.class)))
        .thenReturn(
            SearchQueryResult.<AuthorizationEntity>of(b -> b.items(List.of(entity1, entity2))));

    // when
    final Set<Authorization> result =
        adapter.findAuthorizations(authentication, ResourceType.PROCESS_DEFINITION);

    // then — single record, union of permissions
    assertThat(result)
        .containsExactly(
            new Authorization(
                ResourceType.PROCESS_DEFINITION,
                "*",
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION, PermissionType.READ_PROCESS_INSTANCE)));
  }

  @Test
  void shouldReturnEmptySetWhenNoEntities() {
    // given
    final var authentication = CamundaAuthentication.of(b -> b.user("alice"));
    when(authorizationReader.search(any(AuthorizationQuery.class), any(ResourceAccessChecks.class)))
        .thenReturn(SearchQueryResult.empty());

    // when / then
    assertThat(adapter.findAuthorizations(authentication, ResourceType.COMPONENT)).isEmpty();
  }

  @Test
  void shouldReturnEmptySetAndSkipQueryWhenAuthenticationHasNoOwnerIds() {
    // given — no user, client, group, role, or mapping rule
    final var authentication = CamundaAuthentication.none();

    // when
    final Set<Authorization> result =
        adapter.findAuthorizations(authentication, ResourceType.COMPONENT);

    // then
    assertThat(result).isEmpty();
    verifyNoInteractions(searchClientReaders);
    verifyNoInteractions(authorizationReader);
  }

  @Test
  void shouldSkipEntitiesWithNullResourceId() {
    // given
    final var authentication = CamundaAuthentication.of(b -> b.user("alice"));
    final var nullResourceIdEntity =
        new AuthorizationEntity(
            1L,
            "alice",
            "USER",
            ResourceType.COMPONENT.name(),
            null,
            null,
            null,
            new java.util.HashSet<>(Set.of(PermissionType.ACCESS)));
    final var validEntity =
        new AuthorizationEntity(
            2L,
            "alice",
            "USER",
            ResourceType.COMPONENT.name(),
            null,
            "operate",
            null,
            new java.util.HashSet<>(Set.of(PermissionType.ACCESS)));
    when(authorizationReader.search(any(AuthorizationQuery.class), any(ResourceAccessChecks.class)))
        .thenReturn(
            SearchQueryResult.<AuthorizationEntity>of(
                b -> b.items(List.of(nullResourceIdEntity, validEntity))));

    // when
    final Set<Authorization> result =
        adapter.findAuthorizations(authentication, ResourceType.COMPONENT);

    // then — only the entity with a non-null resourceId is returned
    assertThat(result)
        .containsExactly(
            new Authorization(ResourceType.COMPONENT, "operate", Set.of(PermissionType.ACCESS)));
  }

  @Test
  void shouldMapEveryProtocolResourceTypeToLibraryResourceType() {
    // Asserts the lifted enums (per ADR-0007) stay in lockstep — fail fast if a value drifts.
    for (final var ocType : AuthorizationResourceType.values()) {
      assertThat(AuthorizationRepositoryAdapter.toLibrary(ocType))
          .describedAs("AuthorizationResourceType.%s", ocType.name())
          .isEqualTo(ResourceType.valueOf(ocType.name()));
    }
  }

  @Test
  void shouldMapEveryProtocolPermissionTypeToLibraryPermissionType() {
    for (final var ocPermission : PermissionType.values()) {
      assertThat(AuthorizationRepositoryAdapter.toLibrary(ocPermission))
          .describedAs("PermissionType.%s", ocPermission.name())
          .isEqualTo(PermissionType.valueOf(ocPermission.name()));
    }
  }

  @Test
  void shouldRouteToReaderForCurrentPhysicalTenant() {
    // given a request scoped to a non-default physical tenant, and an adapter that knows both
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, "blue");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    final var blueReader = mock(AuthorizationReader.class);
    final var blueReaders = mock(SearchClientReaders.class);
    when(blueReaders.authorizationReader()).thenReturn(blueReader);
    when(blueReader.search(any(AuthorizationQuery.class), any(ResourceAccessChecks.class)))
        .thenReturn(SearchQueryResult.<AuthorizationEntity>of(b -> b.items(List.of())));
    final var multiTenantAdapter =
        new AuthorizationRepositoryAdapter(
            new PhysicalTenantSearchClientReaders(
                Map.of("default", searchClientReaders, "blue", blueReaders)));

    final var authentication = CamundaAuthentication.of(b -> b.user("alice"));

    // when
    multiTenantAdapter.findAuthorizations(authentication, ResourceType.COMPONENT);

    // then the read is routed to the current tenant's reader, not the default one
    verify(blueReader).search(any(AuthorizationQuery.class), any(ResourceAccessChecks.class));
    verifyNoInteractions(authorizationReader);
  }

  @Test
  void shouldFailFastWhenNoReaderForPhysicalTenant() {
    // given a request scoped to a physical tenant that has no registered reader
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, "ghost");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    final var authentication = CamundaAuthentication.of(b -> b.user("alice"));

    // when / then
    assertThatThrownBy(() -> adapter.findAuthorizations(authentication, ResourceType.COMPONENT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ghost");
  }
}
