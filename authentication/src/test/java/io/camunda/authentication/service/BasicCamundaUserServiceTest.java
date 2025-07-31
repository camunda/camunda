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
import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BasicCamundaUserServiceTest {

  @Mock private CamundaAuthenticationProvider authenticationProvider;
  @Mock private ResourceAccessProvider resourceAccessProvider;
  @Mock private TenantServices tenantServices;
  @Mock private UserServices userServices;
  @Mock private CamundaAuthentication authentication;
  private BasicCamundaUserService basicCamundaUserService;

  @BeforeEach
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();

    when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(tenantServices);
    when(userServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userServices);
    when(resourceAccessProvider.resolveResourceAccess(
            eq(authentication), eq(APPLICATION_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.allowed(APPLICATION_ACCESS_AUTHORIZATION));

    when(authentication.authenticatedUsername()).thenReturn("foo@bar.com");
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(authentication);

    final var user = mock(UserEntity.class);
    when(user.userKey()).thenReturn(100L);
    when(user.name()).thenReturn("Foo Bar");
    when(user.email()).thenReturn("foo@bar.com");
    when(userServices.getUser(eq("foo@bar.com"))).thenReturn(user);

    basicCamundaUserService =
        new BasicCamundaUserService(
            authenticationProvider, resourceAccessProvider, userServices, tenantServices);
  }

  @Test
  void shouldIncludeName() {
    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then
    assertThat(currentUser.username()).isEqualTo("foo@bar.com");
    assertThat(currentUser.displayName()).isEqualTo("Foo Bar");
  }

  @Test
  void shouldIncludeGroups() {
    // given
    when(authentication.authenticatedGroupIds()).thenReturn(List.of("group1", "group2"));

    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then
    assertThat(currentUser.groups()).containsExactlyInAnyOrder("group1", "group2");
  }

  @Test
  void shouldIncludeRoles() {
    // given
    when(authentication.authenticatedRoleIds()).thenReturn(List.of("role1", "role2"));

    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then
    assertThat(currentUser.roles()).containsExactlyInAnyOrder("role1", "role2");
  }

  @Test
  void shouldIncludeTenants() {
    // given
    when(authentication.authenticatedTenantIds()).thenReturn(List.of("tenant1", "tenant2"));
    when(tenantServices.search(any(TenantQuery.class)))
        .thenReturn(
            SearchQueryResult.of(
                new TenantEntity(1L, "tenant1", "name", "desc"),
                new TenantEntity(2L, "tenant2", "name", "desc")));

    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then
    assertThat(currentUser.tenants().stream().map(TenantEntity::tenantId).toList())
        .containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  void shouldIncludeAuthorizedApplications() {
    // given
    final var allowedAuthorization = withAuthorization(APPLICATION_ACCESS_AUTHORIZATION, "operate");
    when(resourceAccessProvider.resolveResourceAccess(
            eq(authentication), eq(APPLICATION_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.allowed(allowedAuthorization));

    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedApplications()).containsExactlyInAnyOrder("operate");
  }

  @Test
  void shouldContainWildcardInAuthorizedApplications() {
    // given
    final var allowedAuthorization = withAuthorization(APPLICATION_ACCESS_AUTHORIZATION, "*");
    when(resourceAccessProvider.resolveResourceAccess(
            eq(authentication), eq(APPLICATION_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.wildcard(allowedAuthorization));

    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedApplications())
        .containsExactlyInAnyOrder(WILDCARD.getResourceId());
  }

  @Test
  void shouldReturnEmptyListOfAuthorizedApplicationIfDenied() {
    // given
    when(resourceAccessProvider.resolveResourceAccess(
            eq(authentication), eq(APPLICATION_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.denied(APPLICATION_ACCESS_AUTHORIZATION));

    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedApplications()).isEmpty();
  }

  @Test
  void shouldReturnNullIfAnonymouslyAuthenticated() {
    // given
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.anonymous());

    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then
    assertThat(currentUser).isNull();
  }

  @Test
  void shouldReturnNullIfNoAuthenticationPresent() {
    // given
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(null);

    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then
    assertThat(currentUser).isNull();
  }
}
