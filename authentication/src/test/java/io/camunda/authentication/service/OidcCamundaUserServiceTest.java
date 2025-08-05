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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.TenantEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.service.TenantServices;
import jakarta.json.Json;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

public class OidcCamundaUserServiceTest {

  @Mock private OidcUser oidcUser;
  @Mock private CamundaAuthentication camundaAuthentication;
  @Mock private OAuth2AuthorizedClientRepository authorizedClientRepository;
  @Mock private CamundaAuthenticationProvider authenticationProvider;
  @Mock private ResourceAccessProvider resourceAccessProvider;
  @Mock private TenantServices tenantServices;
  private OidcCamundaUserService userService;

  @BeforeEach
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();

    final var authentication = mock(OAuth2AuthenticationToken.class);
    when(authentication.getPrincipal()).thenReturn(oidcUser);

    final var securityContext = mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);

    when(authenticationProvider.getCamundaAuthentication()).thenReturn(camundaAuthentication);
    when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(tenantServices);
    when(resourceAccessProvider.resolveResourceAccess(
            any(CamundaAuthentication.class), eq(APPLICATION_ACCESS_AUTHORIZATION)))
        .thenReturn(
            ResourceAccess.allowed(withAuthorization(APPLICATION_ACCESS_AUTHORIZATION, "*")));
    userService =
        new OidcCamundaUserService(
            authenticationProvider,
            resourceAccessProvider,
            tenantServices,
            authorizedClientRepository,
            null);
  }

  @Test
  void shouldIncludeUsername() {
    // given
    when(camundaAuthentication.authenticatedUsername()).thenReturn("foo");

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.username()).isEqualTo("foo");
  }

  @Test
  void shouldIncludeName() {
    // given
    when(oidcUser.getFullName()).thenReturn("Foo Bar");

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.displayName()).isEqualTo("Foo Bar");
  }

  @Test
  void shouldIncludeEmail() {
    // given
    when(oidcUser.getEmail()).thenReturn("foo@bar.com");

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.email()).isEqualTo("foo@bar.com");
  }

  @Test
  void shouldIncludeGroups() {
    // given
    when(camundaAuthentication.authenticatedGroupIds()).thenReturn(List.of("group1", "group2"));

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.groups()).containsExactlyInAnyOrder("group1", "group2");
  }

  @Test
  void shouldIncludeRoles() {
    // given
    when(camundaAuthentication.authenticatedRoleIds()).thenReturn(List.of("role1", "role2"));

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.roles()).containsExactlyInAnyOrder("role1", "role2");
  }

  @Test
  void shouldIncludeTenants() {
    // given
    when(camundaAuthentication.authenticatedTenantIds()).thenReturn(List.of("tenant1", "tenant2"));
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
    when(resourceAccessProvider.resolveResourceAccess(
            eq(camundaAuthentication), eq(APPLICATION_ACCESS_AUTHORIZATION)))
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
    when(resourceAccessProvider.resolveResourceAccess(
            eq(camundaAuthentication), eq(APPLICATION_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.wildcard(allowedAuthorization));

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedApplications()).containsExactlyInAnyOrder("*");
  }

  @Test
  void shouldReturnEmptyListOfAuthorizedApplicationIfDenied() {
    // given
    when(resourceAccessProvider.resolveResourceAccess(
            eq(camundaAuthentication), eq(APPLICATION_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.denied(APPLICATION_ACCESS_AUTHORIZATION));

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedApplications()).isEmpty();
  }

  @Test
  void shouldOidcTokenAsCamundaUser() {
    // given
    final Map<String, Object> attributes = Map.of("email", "bar@foo.com", "name", "Bar Foo");
    final var authentication = mock(AbstractOAuth2TokenAuthenticationToken.class);
    when(authentication.getTokenAttributes()).thenReturn(attributes);

    final var oidcToken = mock(OAuth2Token.class);
    when(authentication.getPrincipal()).thenReturn(oidcToken);

    final var securityContext = mock(SecurityContext.class);
    Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.email()).isEqualTo("bar@foo.com");
    assertThat(currentUser.displayName()).isEqualTo("Bar Foo");
  }

  @Test
  void shouldReturnAccessToken() {
    // given
    final var accessTokenValue = "test-access-token";
    final var accessToken = mock(OAuth2AccessToken.class);
    when(accessToken.getTokenValue()).thenReturn(accessTokenValue);

    final var authorizedClient = mock(OAuth2AuthorizedClient.class);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClientRepository.loadAuthorizedClient(any(), any(), any()))
        .thenReturn(authorizedClient);

    final var expectedToken = Json.createValue(accessTokenValue).toString();

    // when
    final var actualToken = userService.getUserToken();

    assertThat(actualToken).isEqualTo(expectedToken);
  }

  @Test
  void shouldFallBackToIdToken() {
    // given
    final var accessToken = mock(OAuth2AccessToken.class);
    when(accessToken.getTokenValue()).thenReturn(null);

    final var authorizedClient = mock(OAuth2AuthorizedClient.class);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClientRepository.loadAuthorizedClient(any(), any(), any()))
        .thenReturn(authorizedClient);

    final var idTokenValue = "test-id-token";
    final var oidcIdToken = mock(OidcIdToken.class);
    when(oidcIdToken.getTokenValue()).thenReturn(idTokenValue);
    when(oidcUser.getIdToken()).thenReturn(oidcIdToken);

    final var expectedToken = Json.createValue(idTokenValue).toString();

    // when
    final var actualToken = userService.getUserToken();

    // then
    assertThat(actualToken).isEqualTo(expectedToken);
  }

  @Test
  void shouldFailIfOidcUserNotAuthenticated() {
    // given
    final var authentication = mock(OAuth2AuthenticationToken.class);
    when(authentication.getPrincipal()).thenReturn(null);

    final var securityContext = mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);

    // when / then
    assertThatThrownBy(() -> userService.getUserToken())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("User is not authenticated or is not a OIDC user");
  }

  @Test
  void shouldFailIfNoTokenExists() {
    // when / then
    assertThatThrownBy(() -> userService.getUserToken())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("User does not have a valid token");
  }
}
