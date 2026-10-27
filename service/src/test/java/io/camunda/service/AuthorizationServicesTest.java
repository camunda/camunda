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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class AuthorizationServicesTest {

  private static final String PHYSICAL_TENANT_ID = "test-tenant";
  private AuthorizationServices services;
  private AuthorizationSearchClient client;
  private SecurityContextProvider securityContextProvider;

  @BeforeEach
  public void before() {
    final var stubbedBrokerClient = new StubbedBrokerClient();
    client = mock(AuthorizationSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    final var executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(ForkJoinPool.commonPool());
    securityContextProvider = mock(SecurityContextProvider.class);
    final var brokerRequestAuthorizationConverter = mock(BrokerRequestAuthorizationConverter.class);
    services =
        new AuthorizationServices(
            PHYSICAL_TENANT_ID,
            stubbedBrokerClient,
            securityContextProvider,
            client,
            executorProvider,
            brokerRequestAuthorizationConverter);
  }

  @Test
  public void shouldScopeSearchOwnAuthorizationsToOwnerIdsReachableByAuthentication() {
    // given — a user, member of a group, with no role/client/mapping-rule ids
    final var authentication =
        CamundaAuthentication.of(b -> b.user("foo").group("groupId").tenant("<default>"));
    final var callerQuery =
        AuthorizationQuery.of(q -> q.filter(f -> f.resourceType("PROCESS_DEFINITION")));
    final var expectedResult =
        new SearchQueryResult<>(
            1,
            false,
            List.of(
                new AuthorizationEntity(
                    1L,
                    "foo",
                    "USER",
                    "PROCESS_DEFINITION",
                    null,
                    "myProcess",
                    null,
                    Set.of(PermissionType.READ_PROCESS_DEFINITION))),
            null,
            null);
    when(client.searchAuthorizations(any())).thenReturn(expectedResult);

    // when
    final var result = services.searchOwnAuthorizations(callerQuery, authentication);

    // then
    assertThat(result).isEqualTo(expectedResult);

    final var queryCaptor = ArgumentCaptor.forClass(AuthorizationQuery.class);
    verify(client).searchAuthorizations(queryCaptor.capture());
    final var executedFilter = queryCaptor.getValue().filter();
    assertThat(executedFilter.resourceType()).isEqualTo("PROCESS_DEFINITION");
    assertThat(executedFilter.ownerTypeToOwnerIds())
        .isEqualTo(
            Map.of(
                EntityType.USER, Set.of("foo"),
                EntityType.GROUP, Set.of("groupId")));

    // and — no AUTHORIZATION:READ (or any other) permission is required for this self-service
    // search: the query is already scoped to the caller's own owner ids above, so the security
    // context is built from an anonymous authentication purely to skip the generic
    // resource-access check (see ResourceServices/ProcessInstanceServices for the same pattern)
    verify(securityContextProvider).provideSecurityContext(CamundaAuthentication.anonymous());
    verify(securityContextProvider, never()).provideSecurityContext(authentication);
    verify(securityContextProvider, never())
        .provideSecurityContext(any(), any(RequiredAuthorization.class));
  }

  @Test
  public void shouldReturnEmptyResultWithoutSearchingWhenAuthenticationHasNoOwnerIds() {
    // given — no user, client, group, role, or mapping rule
    final var authentication = CamundaAuthentication.none();
    final var callerQuery = AuthorizationQuery.of(q -> q.filter(f -> f.resourceType("RESOURCE")));

    // when
    final var result = services.searchOwnAuthorizations(callerQuery, authentication);

    // then
    assertThat(result).isEqualTo(SearchQueryResult.empty());
    verify(client, never()).searchAuthorizations(any());
  }

  @Test
  public void shouldPreserveCallerSuppliedSortAndPage() {
    // given
    final var authentication = CamundaAuthentication.of(b -> b.user("foo"));
    final var callerQuery =
        AuthorizationQuery.of(
            q ->
                q.filter(f -> f.resourceType("RESOURCE"))
                    .sort(s -> s.ownerId().asc())
                    .page(p -> p.size(5)));
    final var expectedResult =
        new SearchQueryResult<>(
            1,
            false,
            List.of(
                new AuthorizationEntity(
                    2L, "foo", "USER", "RESOURCE", null, "*", null, Set.of(PermissionType.CREATE))),
            null,
            null);
    when(client.searchAuthorizations(any())).thenReturn(expectedResult);

    // when
    services.searchOwnAuthorizations(callerQuery, authentication);

    // then
    final var queryCaptor = ArgumentCaptor.forClass(AuthorizationQuery.class);
    verify(client).searchAuthorizations(queryCaptor.capture());
    assertThat(queryCaptor.getValue().sort()).isEqualTo(callerQuery.sort());
    assertThat(queryCaptor.getValue().page()).isEqualTo(callerQuery.page());
  }
}
