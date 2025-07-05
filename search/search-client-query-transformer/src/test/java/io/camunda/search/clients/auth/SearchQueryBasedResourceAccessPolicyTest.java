/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.clients.security.policy.SearchQueryBasedResourceAccessPolicy;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.security.auth.SecurityContext;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchQueryBasedResourceAccessPolicyTest {

  @Mock private AuthorizationSearchClient authorizationSearchClient;

  private SearchQueryBasedResourceAccessPolicy policy;

  @BeforeEach
  void setUp() {
    when(authorizationSearchClient.withSecurityContext(SecurityContext.withoutAuthentication()))
        .thenReturn(authorizationSearchClient);
    policy = new SearchQueryBasedResourceAccessPolicy(authorizationSearchClient);
  }

  @Test
  void shouldReturnGrantedWhenNoAuthentication() {
    // given
    final var securityContextWithoutAuthentication = SecurityContext.withoutAuthentication();

    // when
    final var result = policy.applySecurityContext(securityContextWithoutAuthentication);

    // then
    assertThat(result.authorizationFilter().granted()).isTrue();
    assertThat(result.authorizationFilter().forbidden()).isFalse();
    assertThat(result.authorizationFilter().requiresCheck()).isFalse();

    assertThat(result.tenantFilter().granted()).isTrue();
    assertThat(result.tenantFilter().forbidden()).isFalse();
    assertThat(result.tenantFilter().requiresCheck()).isFalse();
  }

  @Test
  void shouldReturnGrantedWhenAuthorizedResourceContainsWildcard() {
    // given
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthentication(a -> a.user("foo"))
                    .withAuthorization(
                        a -> a.permissionType(READ).resourceType(PROCESS_DEFINITION)));
    when(authorizationSearchClient.findAllAuthorizations(any()))
        .thenReturn(
            List.of(
                new AuthorizationEntity(
                    null, null, null, null, "*", Set.of(READ_PROCESS_DEFINITION, READ))));

    // when
    final var result = policy.applySecurityContext(securityContext);

    // then
    assertThat(result.authorizationFilter().granted()).isTrue();
    assertThat(result.authorizationFilter().forbidden()).isFalse();
    assertThat(result.authorizationFilter().requiresCheck()).isFalse();

    assertThat(result.tenantFilter().granted()).isTrue();
    assertThat(result.tenantFilter().forbidden()).isFalse();
    assertThat(result.tenantFilter().requiresCheck()).isFalse();
  }

  @Test
  void shouldReturnForbiddenWhenNoAuthorizedResourcesFound() {
    // given
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthentication(a -> a.user("foo"))
                    .withAuthorization(
                        a -> a.permissionType(READ).resourceType(PROCESS_DEFINITION)));
    when(authorizationSearchClient.findAllAuthorizations(any())).thenReturn(List.of());

    // when
    final var result = policy.applySecurityContext(securityContext);

    // then
    assertThat(result.authorizationFilter().granted()).isFalse();
    assertThat(result.authorizationFilter().forbidden()).isTrue();
    assertThat(result.authorizationFilter().requiresCheck()).isFalse();

    assertThat(result.tenantFilter().granted()).isTrue();
    assertThat(result.tenantFilter().forbidden()).isFalse();
    assertThat(result.tenantFilter().requiresCheck()).isFalse();
  }

  @Test
  void shouldReturnRequiredAuthorizationFilterCheck() {
    // given
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthentication(a -> a.user("foo"))
                    .withAuthorization(
                        a -> a.permissionType(READ).resourceType(PROCESS_DEFINITION)));
    when(authorizationSearchClient.findAllAuthorizations(any()))
        .thenReturn(
            List.of(
                new AuthorizationEntity(
                    null, null, null, null, "invoice", Set.of(READ_PROCESS_DEFINITION, READ))));

    // when
    final var result = policy.applySecurityContext(securityContext);

    // then
    assertThat(result.authorizationFilter().granted()).isFalse();
    assertThat(result.authorizationFilter().forbidden()).isFalse();
    assertThat(result.authorizationFilter().requiresCheck()).isTrue();
    assertThat(result.authorizationFilter().requiredAuthorization().resourceType())
        .isEqualTo(PROCESS_DEFINITION);
    assertThat(result.authorizationFilter().requiredAuthorization().permissionType())
        .isEqualTo(READ);
    assertThat(result.authorizationFilter().requiredAuthorization().resourceIds())
        .contains("invoice");

    assertThat(result.tenantFilter().granted()).isTrue();
    assertThat(result.tenantFilter().forbidden()).isFalse();
    assertThat(result.tenantFilter().requiresCheck()).isFalse();
  }

  @Test
  void shouldRequireTenantFilterCheck() {
    // given
    final var securityContextWithoutAuthentication =
        SecurityContext.of(
            b ->
                b.withAuthentication(a -> a.user("foo").tenants(List.of("bar")))
                    .withAuthorization(
                        a -> a.permissionType(READ).resourceType(PROCESS_DEFINITION)));
    when(authorizationSearchClient.findAllAuthorizations(any()))
        .thenReturn(
            List.of(
                new AuthorizationEntity(
                    null, null, null, null, "invoice", Set.of(READ_PROCESS_DEFINITION, READ))));

    // when
    final var result = policy.applySecurityContext(securityContextWithoutAuthentication);

    // then
    assertThat(result.authorizationFilter().granted()).isFalse();
    assertThat(result.authorizationFilter().forbidden()).isFalse();
    assertThat(result.authorizationFilter().requiresCheck()).isTrue();

    assertThat(result.tenantFilter().granted()).isFalse();
    assertThat(result.tenantFilter().forbidden()).isFalse();
    assertThat(result.tenantFilter().requiresCheck()).isTrue();
    assertThat(result.tenantFilter().authenticatedTenants()).contains("bar");
  }
}
