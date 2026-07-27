/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_ANONYMOUS_USER;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.TokenClaimsAuthenticationResolver;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorizedTenantsResolverTest {

  private EngineSecurityConfig securityConfig;
  private TokenClaimsAuthenticationResolver claimsConverter;

  @BeforeEach
  void setUp() {
    securityConfig = mock(EngineSecurityConfig.class);
    claimsConverter = mock(TokenClaimsAuthenticationResolver.class);
  }

  @Test
  void shouldReturnDefaultTenantsWhenMultiTenancyDisabledAndAuthorizationsDisabled() {
    // given — both flags disabled (single-tenant / auth-disabled deployment)
    when(securityConfig.isMultiTenancyChecksEnabled()).thenReturn(false);
    final Map<String, Object> authorizations = Map.of();

    // when
    final var authorizedTenants =
        AuthorizedTenantsResolver.resolve(authorizations, securityConfig, claimsConverter);

    // then — must be the default tenant, NOT anonymous. Resolving the tenant ids must not throw
    // (regression: AnonymouslyAuthorizedTenants.getAuthorizedTenantIds() threw
    // UnsupportedOperationException, crashing ActivateJobs with tenantFilter=ASSIGNED).
    assertThat(authorizedTenants.isAnonymous()).isFalse();
    assertThat(authorizedTenants.getAuthorizedTenantIds())
        .containsExactly(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  void shouldReturnDefaultTenantsWhenMultiTenancyDisabledForAuthenticatedPrincipal() {
    // given — multi-tenancy off but a principal is present (authorizations enabled)
    when(securityConfig.isMultiTenancyChecksEnabled()).thenReturn(false);
    final Map<String, Object> authorizations = Map.of(AUTHORIZED_USERNAME, "demo-user");

    // when
    final var authorizedTenants =
        AuthorizedTenantsResolver.resolve(authorizations, securityConfig, claimsConverter);

    // then — tenant filtering still applies to the default tenant (matches pre-migration behavior)
    assertThat(authorizedTenants.isAnonymous()).isFalse();
    assertThat(authorizedTenants.getAuthorizedTenantIds())
        .containsExactly(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  void shouldReturnAnonymousWhenAnonymousClaimIsSet() {
    // given
    final Map<String, Object> authorizations = Map.of(AUTHORIZED_ANONYMOUS_USER, true);

    // when
    final var authorizedTenants =
        AuthorizedTenantsResolver.resolve(authorizations, securityConfig, claimsConverter);

    // then
    assertThat(authorizedTenants.isAnonymous()).isTrue();
  }

  @Test
  void shouldReturnEmptyAuthenticatedTenantsWhenMultiTenancyEnabledButNoPrincipal() {
    // given — multi-tenancy on but neither username nor client id present
    when(securityConfig.isMultiTenancyChecksEnabled()).thenReturn(true);
    final Map<String, Object> authorizations = Map.of();

    // when
    final var authorizedTenants =
        AuthorizedTenantsResolver.resolve(authorizations, securityConfig, claimsConverter);

    // then — authorized for no tenant (not anonymous), so tenant-scoped lookups return NOT_FOUND
    assertThat(authorizedTenants.isAnonymous()).isFalse();
    assertThat(authorizedTenants.getAuthorizedTenantIds()).isEmpty();
    assertThat(authorizedTenants.isAuthorizedForTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
        .isFalse();
  }

  @Test
  void shouldNotClassifyClientIdPrincipalAsMissing() {
    // given — multi-tenancy on, principal is a client id
    when(securityConfig.isMultiTenancyChecksEnabled()).thenReturn(true);
    final var authentication = mock(CamundaAuthentication.class);
    when(authentication.anonymousUser()).thenReturn(false);
    when(authentication.authenticatedTenantIds()).thenReturn(List.of("tenant-1"));
    final Map<String, Object> authorizations = Map.of(AUTHORIZED_CLIENT_ID, "client-1");
    when(claimsConverter.resolve(authorizations)).thenReturn(authentication);

    // when
    final var authorizedTenants =
        AuthorizedTenantsResolver.resolve(authorizations, securityConfig, claimsConverter);

    // then — resolved from the converted claims, not the empty no-principal fallback
    assertThat(authorizedTenants.isAnonymous()).isFalse();
    assertThat(authorizedTenants.getAuthorizedTenantIds()).containsExactly("tenant-1");
  }
}
