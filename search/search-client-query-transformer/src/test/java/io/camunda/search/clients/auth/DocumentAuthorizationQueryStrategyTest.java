/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.query.SearchQueryBuilders.authorizationSearchQuery;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.CREATE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.query.SearchMatchNoneQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryBase;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.entity.Permission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentAuthorizationQueryStrategyTest {

  @Mock private AuthorizationSearchClient authorizationSearchClient;

  private DocumentAuthorizationQueryStrategy queryStrategy;

  @BeforeEach
  void setUp() {
    when(authorizationSearchClient.withSecurityContext(SecurityContext.withoutAuthentication()))
        .thenReturn(authorizationSearchClient);
    queryStrategy = new DocumentAuthorizationQueryStrategy(authorizationSearchClient);
  }

  @Test
  void shouldReturnRequestUnchangedWhenAuthorizationNotRequired() {
    // given
    final var originalRequest = mock(SearchQueryRequest.class);
    final var securityContext = SecurityContext.of(s -> s.withAuthentication(a -> a.user(123L)));

    // when
    final SearchQueryRequest result =
        queryStrategy.applyAuthorizationToQuery(
            originalRequest, securityContext, SearchQueryBase.class);

    // then
    assertThat(result).isSameAs(originalRequest);
  }

  @Test
  void shouldReturnRequestUnchangedWhenNoAuthentication() {
    // given
    final var originalRequest = mock(SearchQueryRequest.class);
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthorization(
                    a ->
                        a.resourceType(PROCESS_DEFINITION)
                            .permissionType(READ_PROCESS_DEFINITION)));

    // when
    final SearchQueryRequest result =
        queryStrategy.applyAuthorizationToQuery(
            originalRequest, securityContext, ProcessDefinitionQuery.class);

    // then
    assertThat(result).isSameAs(originalRequest);
  }

  @Test
  void shouldReturnRequestUnchangedWhenAuthorizedResourceContainsWildcard() {
    // given
    final SearchQueryRequest originalRequest = mock(SearchQueryRequest.class);
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthentication(a -> a.user(123L))
                    .withAuthorization(
                        a ->
                            a.permissionType(READ_PROCESS_DEFINITION)
                                .resourceType(PROCESS_DEFINITION)));
    when(authorizationSearchClient.findAllAuthorizations(any()))
        .thenReturn(
            List.of(
                new AuthorizationEntity(
                    null,
                    null,
                    null,
                    List.of(
                        new Permission(READ_PROCESS_DEFINITION, Set.of("foo", "*")),
                        new Permission(CREATE, Set.of("bar"))))));

    // when
    final SearchQueryRequest result =
        queryStrategy.applyAuthorizationToQuery(
            originalRequest, securityContext, ProcessDefinitionQuery.class);

    // then
    assertThat(result).isSameAs(originalRequest);
  }

  @Test
  void shouldReturnMatchNoneQueryWhenNoAuthorizedResourcesFound() {
    // given
    final SearchQueryRequest originalRequest =
        new SearchQueryRequest.Builder().index("index").build();
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthentication(a -> a.user(123L))
                    .withAuthorization(
                        a ->
                            a.permissionType(READ_PROCESS_DEFINITION)
                                .resourceType(PROCESS_DEFINITION)));
    when(authorizationSearchClient.findAllAuthorizations(any())).thenReturn(List.of());

    // when
    final SearchQueryRequest result =
        queryStrategy.applyAuthorizationToQuery(
            originalRequest, securityContext, ProcessDefinitionQuery.class);

    // then
    assertThat(result.query().queryOption()).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  void shouldApplyAuthorizationFilterToQuery() {
    // given
    final SearchQueryRequest originalRequest =
        new SearchQueryRequest.Builder().index("index").query(mock(SearchQuery.class)).build();
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthentication(a -> a.user(123L))
                    .withAuthorization(
                        a ->
                            a.permissionType(READ_PROCESS_DEFINITION)
                                .resourceType(PROCESS_DEFINITION)));
    final var authorizationQueryCaptor = ArgumentCaptor.forClass(AuthorizationQuery.class);
    when(authorizationSearchClient.findAllAuthorizations(authorizationQueryCaptor.capture()))
        .thenReturn(
            List.of(
                new AuthorizationEntity(
                    null,
                    null,
                    null,
                    List.of(
                        new Permission(READ_PROCESS_DEFINITION, Set.of("foo")),
                        new Permission(CREATE, Set.of("bar"))))));

    // when
    final SearchQueryRequest result =
        queryStrategy.applyAuthorizationToQuery(
            originalRequest, securityContext, ProcessDefinitionQuery.class);

    // then
    assertThat(result.query())
        .isEqualTo(and(originalRequest.query(), stringTerms("bpmnProcessId", List.of("foo"))));
    assertThat(authorizationQueryCaptor.getValue())
        .isEqualTo(
            authorizationSearchQuery(
                q ->
                    q.filter(
                        f ->
                            f.ownerIds(List.of(123L))
                                .resourceType("PROCESS_DEFINITION")
                                .permissionType(READ_PROCESS_DEFINITION))));
  }

  private SearchQueryResponse<AuthorizationEntity> buildSearchQueryResponse(
      final AuthorizationEntity authorizationEntity) {
    return SearchQueryResponse.of(
        r ->
            r.hits(
                List.of(
                    new SearchQueryHit.Builder<AuthorizationEntity>()
                        .source(authorizationEntity)
                        .build())));
  }
}
