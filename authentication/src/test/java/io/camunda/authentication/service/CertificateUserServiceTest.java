/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.COMPONENT_ACCESS_AUTHORIZATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.authentication.config.MutualTlsProperties;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import jakarta.json.Json;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

public class CertificateUserServiceTest {

  @Mock private OidcUser oidcUser;
  @Mock private CamundaAuthentication camundaAuthentication;
  @Mock private OAuth2AuthorizedClientRepository authorizedClientRepository;
  @Mock private CamundaAuthenticationProvider authenticationProvider;
  @Mock private ResourceAccessProvider resourceAccessProvider;
  @Mock private TenantServices tenantServices;
  @Mock private UserServices userServices;
  @Mock private RoleServices roleServices;
  @Mock private MutualTlsProperties mtlsProperties;
  private CertificateUserService userService;

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
            any(CamundaAuthentication.class), eq(COMPONENT_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.allowed(withAuthorization(COMPONENT_ACCESS_AUTHORIZATION, "*")));
    when(mtlsProperties.getDefaultRoles()).thenReturn(List.of("ROLE_USER"));
    userService =
        new CertificateUserService(
            authenticationProvider,
            resourceAccessProvider,
            tenantServices,
            userServices,
            roleServices,
            Optional.of(authorizedClientRepository),
            Optional.empty(),
            Optional.of(mtlsProperties));
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
  void shouldIncludeAuthorizedComponents() {
    // given
    final var allowedAuthorization = withAuthorization(COMPONENT_ACCESS_AUTHORIZATION, "operate");
    when(resourceAccessProvider.resolveResourceAccess(
            eq(camundaAuthentication), eq(COMPONENT_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.allowed(allowedAuthorization));

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedComponents()).containsExactlyInAnyOrder("operate");
  }

  @Test
  void shouldContainWildcardInAuthorizedComponents() {
    // given
    final var allowedAuthorization = withAuthorization(COMPONENT_ACCESS_AUTHORIZATION, "*");
    when(resourceAccessProvider.resolveResourceAccess(
            eq(camundaAuthentication), eq(COMPONENT_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.wildcard(allowedAuthorization));

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedComponents()).containsExactlyInAnyOrder("*");
  }

  @Test
  void shouldReturnEmptyListOfAuthorizedComponentsIfDenied() {
    // given
    when(resourceAccessProvider.resolveResourceAccess(
            eq(camundaAuthentication), eq(COMPONENT_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.denied(COMPONENT_ACCESS_AUTHORIZATION));

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedComponents()).isEmpty();
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

  @Test
  void shouldFailForMtlsUserTokenRequest() {
    // given
    final var mockCertificate = mock(X509Certificate.class);
    final var authentication = mock(PreAuthenticatedAuthenticationToken.class);
    when(authentication.getPrincipal()).thenReturn(mockCertificate);

    final var securityContext = mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);

    // when / then
    assertThatThrownBy(() -> userService.getUserToken())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("mTLS users do not have OAuth tokens");
  }

  @Test
  void shouldEnsureUserExistsForNewMtlsUser() throws Exception {
    // given
    final var mockCertificate = mock(X509Certificate.class);
    when(mockCertificate.getSubjectX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=testuser,O=Test,C=US"));

    when(userServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userServices);
    when(userServices.getUser("testuser")).thenThrow(new RuntimeException("User not found"));

    final var createdUser = mock(UserRecord.class);
    when(createdUser.getUsername()).thenReturn("testuser");
    when(userServices.createUser(any()))
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(createdUser));

    when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(roleServices);
    when(roleServices.hasMembersOfType(any(), any())).thenReturn(false);
    when(roleServices.addMember(any()))
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

    // when
    userService.ensureUserExists("testuser", mockCertificate);

    // then - verify user creation was attempted
    Mockito.verify(userServices).createUser(any(UserServices.UserDTO.class));
    Mockito.verify(roleServices).addMember(any(RoleServices.RoleMemberRequest.class));
  }

  @Test
  void shouldSkipCreationForExistingMtlsUser() throws Exception {
    // given
    final var mockCertificate = mock(X509Certificate.class);
    final var existingUser = mock(io.camunda.search.entities.UserEntity.class);

    when(userServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userServices);
    when(userServices.getUser("existinguser")).thenReturn(existingUser);

    // when
    userService.ensureUserExists("existinguser", mockCertificate);

    // then - verify no user creation attempted
    Mockito.verify(userServices, Mockito.never()).createUser(any());
  }

  @Test
  void shouldHandleMtlsCertificateUserClaims() {
    // given
    final var mockCertificate = mock(X509Certificate.class);
    when(mockCertificate.getSubjectX500Principal())
        .thenReturn(
            new javax.security.auth.x500.X500Principal(
                "CN=Test User,EMAILADDRESS=test@example.com,O=Test,C=US"));

    final var authentication = mock(PreAuthenticatedAuthenticationToken.class);
    when(authentication.getPrincipal()).thenReturn(mockCertificate);

    final var securityContext = mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);

    when(camundaAuthentication.authenticatedUsername()).thenReturn("Test User");

    // when
    final var currentUser = userService.getCurrentUser();

    // then
    assertThat(currentUser.displayName()).isEqualTo("Test User");
    assertThat(currentUser.email()).isEqualTo("test@example.com");
  }
}
