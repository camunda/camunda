/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.authentication.entity.AuthenticationContext;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CamundaOAuthPrincipalServiceTest {
  private static final String REGISTRATION_ID = "test";
  private static final String TOKEN_VALUE = "{}";
  private static final Instant TOKEN_ISSUED_AT = Instant.now().truncatedTo(ChronoUnit.SECONDS);
  private static final Instant TOKEN_EXPIRES_AT = TOKEN_ISSUED_AT.plus(1, ChronoUnit.DAYS);

  private CamundaOAuthPrincipalService camundaOAuthPrincipalService;

  @Mock private MappingServices mappingServices;
  @Mock private TenantServices tenantServices;
  @Mock private RoleServices roleServices;
  @Mock private GroupServices groupServices;
  @Mock private AuthorizationServices authorizationServices;
  @Mock private SecurityConfiguration securityConfiguration;
  @Mock private AuthenticationConfiguration authenticationConfiguration;
  @Mock private OidcAuthenticationConfiguration oidcAuthenticationConfiguration;

  @BeforeEach
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();

    when(securityConfiguration.getAuthentication()).thenReturn(authenticationConfiguration);
    when(authenticationConfiguration.getOidc()).thenReturn(oidcAuthenticationConfiguration);
    when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn("sub");

    camundaOAuthPrincipalService =
        new CamundaOAuthPrincipalService(
            mappingServices,
            tenantServices,
            roleServices,
            groupServices,
            authorizationServices,
            securityConfiguration);
  }

  @Test
  public void loadUser() {
    // given
    final Map<String, Object> claims =
        Map.of(
            "sub", "test|foo@camunda.test",
            "email", "foo@camunda.test",
            "role", "R1",
            "group", "G1");
    when(mappingServices.getMatchingMappings(claims))
        .thenReturn(
            List.of(
                new MappingEntity("test-id", 5L, "role", "R1", "role-r1"),
                new MappingEntity("test-id-2", 7L, "group", "G1", "group-g1")));

    final var roleR1 = new RoleEntity(8L, "Role R1");
    when(roleServices.getRolesByMemberKeys(Set.of(5L, 7L))).thenReturn(List.of(roleR1));
    when(authorizationServices.getAuthorizedApplications(Set.of("test-id", "test-id-2", "8")))
        .thenReturn(List.of("*"));

    // when
    final OAuthContext oAuthContext = camundaOAuthPrincipalService.loadOAuthContext(claims);

    // then
    assertThat(oAuthContext).isNotNull();
    assertThat(oAuthContext.mappingKeys()).isEqualTo(Set.of(5L, 7L));
    assertThat(oAuthContext.mappingIds()).isEqualTo(Set.of("test-id", "test-id-2"));
    final AuthenticationContext authenticationContext = oAuthContext.authenticationContext();
    assertThat(authenticationContext.roles()).containsAll(Set.of(roleR1));
    assertThat(authenticationContext.groups()).isEmpty();
    assertThat(authenticationContext.tenants()).isEmpty();
    assertThat(authenticationContext.authorizedApplications()).containsAll(Set.of("*"));
  }
}
