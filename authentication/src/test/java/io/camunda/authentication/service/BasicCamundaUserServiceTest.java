/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static io.camunda.security.api.model.authz.AuthorizationScope.WILDCARD;
import static io.camunda.security.core.auth.RequiredAuthorization.withRequiredAuthorization;
import static io.camunda.service.authorization.Authorizations.COMPONENT_ACCESS_AUTHORIZATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.search.entities.UserEntity;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.authz.ResourceAccess;
import io.camunda.security.core.authz.ResourceAccessProvider;
import io.camunda.service.UserServices;
import io.camunda.service.registry.DefaultServiceRegistry;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class BasicCamundaUserServiceTest {

  private static final String TENANT_A = "tenanta";

  @Mock private CamundaAuthenticationProvider authenticationProvider;
  @Mock private ResourceAccessProvider resourceAccessProvider;
  @Mock private UserServices userServices;
  @Mock private UserServices tenantAUserServices;
  @Mock private CamundaAuthentication authentication;
  private BasicCamundaUserService basicCamundaUserService;

  @BeforeEach
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();

    when(resourceAccessProvider.resolveResourceAccess(
            eq(authentication), eq(COMPONENT_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.allowed(COMPONENT_ACCESS_AUTHORIZATION));

    when(authentication.authenticatedUsername()).thenReturn("foo@bar.com");
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(authentication);

    final var user = mock(UserEntity.class);
    when(user.userKey()).thenReturn(100L);
    when(user.name()).thenReturn("Foo Bar");
    when(user.email()).thenReturn("foo@bar.com");
    when(userServices.getUser(eq("foo@bar.com"), any())).thenReturn(user);

    final var serviceRegistry =
        DefaultServiceRegistry.of(
            b ->
                b.userServices(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, userServices)
                    .userServices(TENANT_A, tenantAUserServices));

    basicCamundaUserService =
        new BasicCamundaUserService(
            authenticationProvider, resourceAccessProvider, serviceRegistry);

    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
  }

  @AfterEach
  void clearRequestScope() {
    RequestContextHolder.resetRequestAttributes();
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
  void shouldIncludeTenantIds() {
    // given
    when(authentication.authenticatedTenantIds()).thenReturn(List.of("tenant1", "tenant2"));

    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then
    assertThat(currentUser.tenants()).containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  void shouldIncludeAuthorizedComponents() {
    // given
    final var allowedAuthorization =
        withRequiredAuthorization(COMPONENT_ACCESS_AUTHORIZATION, "operate");
    when(resourceAccessProvider.resolveResourceAccess(
            eq(authentication), eq(COMPONENT_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.allowed(allowedAuthorization));

    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedComponents()).containsExactlyInAnyOrder("operate");
  }

  @Test
  void shouldNormalizeIdentityComponentToAdmin() {
    // given
    final var allowedAuthorization =
        withRequiredAuthorization(COMPONENT_ACCESS_AUTHORIZATION, "identity");
    when(resourceAccessProvider.resolveResourceAccess(
            eq(authentication), eq(COMPONENT_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.allowed(allowedAuthorization));

    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedComponents()).containsExactlyInAnyOrder("admin");
  }

  @Test
  void shouldContainWildcardInAuthorizedComponents() {
    // given
    final var allowedAuthorization = withRequiredAuthorization(COMPONENT_ACCESS_AUTHORIZATION, "*");
    when(resourceAccessProvider.resolveResourceAccess(
            eq(authentication), eq(COMPONENT_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.wildcard(allowedAuthorization));

    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedComponents())
        .containsExactlyInAnyOrder(WILDCARD.getResourceId());
  }

  @Test
  void shouldReturnEmptyListOfAuthorizedComponentsIfDenied() {
    // given
    when(resourceAccessProvider.resolveResourceAccess(
            eq(authentication), eq(COMPONENT_ACCESS_AUTHORIZATION)))
        .thenReturn(ResourceAccess.denied(COMPONENT_ACCESS_AUTHORIZATION));

    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then
    assertThat(currentUser.authorizedComponents()).isEmpty();
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

  @Test
  void shouldRouteUserLookupToRequestPhysicalTenant() {
    // given a request scoped to a non-default physical tenant
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, TENANT_A);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    final var tenantAUser = mock(UserEntity.class);
    when(tenantAUser.userKey()).thenReturn(200L);
    when(tenantAUser.name()).thenReturn("Tenant A User");
    when(tenantAUser.email()).thenReturn("foo@bar.com");
    when(tenantAUserServices.getUser(eq("foo@bar.com"), any())).thenReturn(tenantAUser);

    // when
    final var currentUser = basicCamundaUserService.getCurrentUser();

    // then — resolved via the tenant-A user services, not the default
    assertThat(currentUser.displayName()).isEqualTo("Tenant A User");
  }
}
