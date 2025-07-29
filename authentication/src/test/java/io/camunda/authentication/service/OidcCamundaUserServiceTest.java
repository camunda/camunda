/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.APPLICATION_ACCESS_AUTHORIZATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.authentication.entity.AuthenticationContext.AuthenticationContextBuilder;
import io.camunda.authentication.entity.CamundaOidcUser;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.service.TenantServices;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class OidcCamundaUserServiceTest {

  @Mock private CamundaAuthenticationProvider authenticationProvider;
  @Mock private ResourceAccessProvider resourceAccessProvider;
  @Mock private TenantServices tenantServices;
  private OidcCamundaUserService userService;

  @BeforeEach
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();

    final var oidcUser = mock(OidcUser.class);
    when(oidcUser.getName()).thenReturn("foo");
    final var user =
        new CamundaOidcUser(
            oidcUser,
            "a-token",
            new OAuthContext(null, new AuthenticationContextBuilder().withUsername("foo").build()));
    final var authentication = Mockito.mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn(user);

    final var securityContext = Mockito.mock(SecurityContext.class);
    Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);

    when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(tenantServices);

    userService =
        new OidcCamundaUserService(authenticationProvider, resourceAccessProvider, tenantServices);
  }

  @Test
  void shouldIncludeTenants() {
    // given
    final var oidcUser = mock(OidcUser.class);
    when(oidcUser.getName()).thenReturn("foo");
    final var user =
        new CamundaOidcUser(
            oidcUser,
            "a-token",
            new OAuthContext(
                null,
                new AuthenticationContextBuilder()
                    .withTenants(List.of("tenant1", "tenant2"))
                    .withUsername("foo")
                    .build()));
    final var authentication = Mockito.mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn(user);
    final var securityContext = Mockito.mock(SecurityContext.class);
    Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);

    final var camundaAuthentication = mock(CamundaAuthentication.class);
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(camundaAuthentication);
    when(resourceAccessProvider.resolveResourceAccess(
            eq(camundaAuthentication), eq(APPLICATION_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.allowed(APPLICATION_ACCESS_AUTHORIZATION));
    when(tenantServices.search(any(TenantQuery.class)))
        .thenReturn(
            SearchQueryResult.of(
                new TenantEntity(1L, "tenant1", "name", "desc"),
                new TenantEntity(2L, "tenant2", "name", "desc")));

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.tenants().stream().map(TenantEntity::tenantId).toList())
        .containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  void shouldIncludeAuthorizedApplications() {
    // given
    final var allowedAuthorization = withAuthorization(APPLICATION_ACCESS_AUTHORIZATION, "operate");
    final var authentication = mock(CamundaAuthentication.class);
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(authentication);
    when(resourceAccessProvider.resolveResourceAccess(
            eq(authentication), eq(APPLICATION_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.allowed(allowedAuthorization));

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedApplications()).containsExactlyInAnyOrder("operate");
  }

  @Test
  void shouldContainWildcardInAuthorizedApplications() {
    // given
    final var allowedAuthorization = withAuthorization(APPLICATION_ACCESS_AUTHORIZATION, "*");
    final var authentication = mock(CamundaAuthentication.class);
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(authentication);
    when(resourceAccessProvider.resolveResourceAccess(
            eq(authentication), eq(APPLICATION_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.wildcard(allowedAuthorization));

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedApplications()).containsExactlyInAnyOrder("*");
  }

  @Test
  void shouldReturnEmptyListOfAuthorizedApplicationIfDenied() {
    // given
    final var authentication = mock(CamundaAuthentication.class);
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(authentication);
    when(resourceAccessProvider.resolveResourceAccess(
            eq(authentication), eq(APPLICATION_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.denied(APPLICATION_ACCESS_AUTHORIZATION));

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedApplications()).isEmpty();
  }
}
