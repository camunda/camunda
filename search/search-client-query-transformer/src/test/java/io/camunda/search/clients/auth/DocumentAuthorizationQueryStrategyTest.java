/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.impl.AuthorizationChecker;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentAuthorizationQueryStrategyTest {

  @Mock private AuthorizationChecker authorizationChecker;
  private DocumentAuthorizationQueryStrategy queryStrategy;

  @BeforeEach
  void setUp() {
    queryStrategy = new DocumentAuthorizationQueryStrategy(authorizationChecker);
  }

  @Test
  void shouldReturnDisabledAuthorizationCheckAuthorizationNotProvided() {
    // given
    final var securityContext = SecurityContext.of(s -> s.withAuthentication(a -> a.user("foo")));

    // when
    final var result = queryStrategy.resolveAuthorizationCheck(securityContext);

    // then
    assertThat(result.enabled()).isFalse();
  }

  @Test
  void shouldReturnDisabledAuthorizationCheckUnchangedWhenNoAuthentication() {
    // given
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthorization(a -> a.processDefinition().readProcessDefinition()));

    // when
    final var result = queryStrategy.resolveAuthorizationCheck(securityContext);

    // then
    assertThat(result.enabled()).isFalse();
  }

  @Test
  void shouldReturnDisabledAuthorizationCheckWhenAuthorizedResourceContainsWildcard() {
    // given
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthentication(a -> a.user("foo"))
                    .withAuthorization(a -> a.processDefinition().readProcessDefinition()));
    when(authorizationChecker.retrieveAuthorizedResourceKeys(any()))
        .thenReturn(List.of(Authorization.WILDCARD));

    // when
    final var result = queryStrategy.resolveAuthorizationCheck(securityContext);

    // then
    assertThat(result.enabled()).isFalse();
  }

  @Test
  void shouldReturnEnabledAuthorizationCheckWithoutResourceIds() {
    // given
    final SearchQueryRequest originalRequest =
        new SearchQueryRequest.Builder().index("index").build();
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthentication(a -> a.user("foo"))
                    .withAuthorization(
                        Authorization.of(a -> a.processDefinition().readProcessDefinition())));
    when(authorizationChecker.retrieveAuthorizedResourceKeys(any())).thenReturn(List.of());

    // when
    final var result = queryStrategy.resolveAuthorizationCheck(securityContext);

    // then
    assertThat(result.enabled()).isTrue();
    assertThat(result.authorization().permissionType()).isEqualTo(READ_PROCESS_DEFINITION);
    assertThat(result.authorization().resourceType()).isEqualTo(PROCESS_DEFINITION);
    assertThat(result.authorization().resourceIds()).isEmpty();
  }

  @Test
  void shouldReturnEnabledAuthorizationCheckWithResourceIds() {
    // given
    final SearchQueryRequest originalRequest =
        new SearchQueryRequest.Builder().index("index").query(mock(SearchQuery.class)).build();
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthentication(a -> a.user("foo"))
                    .withAuthorization(
                        Authorization.of(a -> a.processDefinition().readProcessDefinition())));
    final var authorizationQueryCaptor = ArgumentCaptor.forClass(AuthorizationQuery.class);
    when(authorizationChecker.retrieveAuthorizedResourceKeys(any()))
        .thenReturn(List.of("foo", "bar"));

    // when
    final var result = queryStrategy.resolveAuthorizationCheck(securityContext);

    // then
    assertThat(result.enabled()).isTrue();
    assertThat(result.authorization().permissionType()).isEqualTo(READ_PROCESS_DEFINITION);
    assertThat(result.authorization().resourceType()).isEqualTo(PROCESS_DEFINITION);
    assertThat(result.authorization().resourceIds()).containsExactlyInAnyOrder("foo", "bar");
  }
}
