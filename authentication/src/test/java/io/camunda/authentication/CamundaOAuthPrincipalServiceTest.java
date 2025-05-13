/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static io.camunda.authentication.CamundaOAuthPrincipalService.CLAIM_NOT_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.authentication.entity.AuthenticationContext;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestInstance(Lifecycle.PER_CLASS)
public class CamundaOAuthPrincipalServiceTest {

  private CamundaOAuthPrincipalService camundaOAuthPrincipalService;

  @Nested
  class ApplicationIdClaimConfiguration {
    private static final String APPLICATION_ID_CLAIM = "application-id";
    @Mock private MappingServices mappingServices;
    @Mock private TenantServices tenantServices;
    @Mock private RoleServices roleServices;
    @Mock private GroupServices groupServices;
    @Mock private AuthorizationServices authorizationServices;
    @Mock private SecurityConfiguration securityConfiguration;
    @Mock private AuthenticationConfiguration authenticationConfiguration;
    @Mock private OidcAuthenticationConfiguration oidcAuthenticationConfiguration;
    @Mock private JwtDecoder jwtDecoder;
    @Mock private Jwt jwt;

    @BeforeEach
    public void setUp() throws Exception {
      MockitoAnnotations.openMocks(this).close();

      when(securityConfiguration.getAuthentication()).thenReturn(authenticationConfiguration);
      when(authenticationConfiguration.getOidc()).thenReturn(oidcAuthenticationConfiguration);
      when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn("not-tested");
      when(oidcAuthenticationConfiguration.getApplicationIdClaim())
          .thenReturn(APPLICATION_ID_CLAIM);
      when(jwtDecoder.decode(any())).thenReturn(jwt);

      camundaOAuthPrincipalService =
          new CamundaOAuthPrincipalService(
              mappingServices,
              tenantServices,
              roleServices,
              groupServices,
              authorizationServices,
              securityConfiguration,
              jwtDecoder);
    }

    @Test
    public void shouldThrowExceptionWhenNoApplicationIdClaimFound() {
      final var jsonString =
          """
          {
            "sub": "user@example.com"
          }
          """;

      // given
      when(jwt.getTokenValue()).thenReturn(jsonString);
      // given
      final Map<String, Object> claims = Map.of("sub", "user@example.com");
      when(jwt.getClaims()).thenReturn(claims);
      // when
      final var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> camundaOAuthPrincipalService.loadOAuthContext(jwt.getTokenValue()));

      assertThat(exception.getMessage())
          .isEqualTo(
              "Neither username claim (%s) nor applicationId claim (%s) could be found in the claims. Please check your OIDC configuration."
                  .formatted("not-tested", APPLICATION_ID_CLAIM));
    }

    @Test
    public void shouldThrowExceptionWhenApplicationIdClaimIsNotAString() {
      final var jsonString =
          """
          {
            "sub": "user@example.com",
            "%s": [
              "app-1",
              "app-2"
            ]
          }
          """
              .formatted(APPLICATION_ID_CLAIM);

      // given
      when(jwt.getTokenValue()).thenReturn(jsonString);
      when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn("not-tested");
      when(oidcAuthenticationConfiguration.getApplicationIdClaim())
          .thenReturn(APPLICATION_ID_CLAIM);
      // given
      final Map<String, Object> claims = Map.of(APPLICATION_ID_CLAIM, List.of("app-1", "app-2"));
      when(jwt.getClaims()).thenReturn(claims);

      // when
      final var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> camundaOAuthPrincipalService.loadOAuthContext(jwt.getTokenValue()));

      assertThat(exception.getMessage())
          .isEqualTo(CLAIM_NOT_STRING.formatted("application", APPLICATION_ID_CLAIM));
    }

    @Test
    public void shouldLoadUserWhenUsingApplicationIdClaim() {
      final var jsonString =
          """
          {
            "sub": "user@example.com",
            "%s": "app-1",
          }
          """
              .formatted(APPLICATION_ID_CLAIM);

      // given
      when(jwt.getTokenValue()).thenReturn(jsonString);
      // given
      final Map<String, Object> claims =
          Map.of("sub", UUID.randomUUID().toString(), APPLICATION_ID_CLAIM, "app-1");
      when(jwt.getClaims()).thenReturn(claims);
      // when
      final OAuthContext oAuthContext =
          camundaOAuthPrincipalService.loadOAuthContext(jwt.getTokenValue());

      // then
      assertThat(oAuthContext).isNotNull();
      final AuthenticationContext authenticationContext = oAuthContext.authenticationContext();
      assertThat(authenticationContext.applicationId()).isEqualTo("app-1");
    }
  }

  @Nested
  class UsernameClaimConfiguration {
    private static final String USERNAME_CLAIM = "email";
    @Mock private MappingServices mappingServices;
    @Mock private TenantServices tenantServices;
    @Mock private RoleServices roleServices;
    @Mock private GroupServices groupServices;
    @Mock private AuthorizationServices authorizationServices;
    @Mock private SecurityConfiguration securityConfiguration;
    @Mock private AuthenticationConfiguration authenticationConfiguration;
    @Mock private OidcAuthenticationConfiguration oidcAuthenticationConfiguration;
    @Mock private JwtDecoder jwtDecoder;
    @Mock private Jwt jwt;

    @BeforeEach
    public void setUp() throws Exception {
      MockitoAnnotations.openMocks(this).close();

      when(securityConfiguration.getAuthentication()).thenReturn(authenticationConfiguration);
      when(authenticationConfiguration.getOidc()).thenReturn(oidcAuthenticationConfiguration);
      when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn(USERNAME_CLAIM);
      when(oidcAuthenticationConfiguration.getApplicationIdClaim()).thenReturn("not-tested");
      when(jwtDecoder.decode(any())).thenReturn(jwt);

      camundaOAuthPrincipalService =
          new CamundaOAuthPrincipalService(
              mappingServices,
              tenantServices,
              roleServices,
              groupServices,
              authorizationServices,
              securityConfiguration,
              jwtDecoder);
    }

    @Test
    public void shouldThrowExceptionWhenNoUsernameClaimFound() {
      final String jsonString =
          """
          {
            "not-username": "test"
          }
          """;

      // given
      when(jwt.getTokenValue()).thenReturn(jsonString);
      // when
      final var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> camundaOAuthPrincipalService.loadOAuthContext(jwt.getTokenValue()));

      assertThat(exception.getMessage())
          .isEqualTo(
              "Neither username claim (%s) nor applicationId claim (%s) could be found in the claims. Please check your OIDC configuration."
                  .formatted(USERNAME_CLAIM, "not-tested"));
    }

    @Test
    public void shouldThrowExceptionWhenUsernameClaimIsNotAString() {
      final String jsonString =
          """
          {
            "%s": [
              "test",
              "test2"
            ]
          }
          """
              .formatted(USERNAME_CLAIM);

      // given
      when(jwt.getTokenValue()).thenReturn(jsonString);
      // given
      final Map<String, Object> claims = Map.of(USERNAME_CLAIM, List.of("app-1", "app-2"));
      when(jwt.getClaims()).thenReturn(claims);
      // when
      final var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> camundaOAuthPrincipalService.loadOAuthContext(jwt.getTokenValue()));

      assertThat(exception.getMessage())
          .isEqualTo(CLAIM_NOT_STRING.formatted("username", USERNAME_CLAIM));
    }

    @Test
    public void loadUser() {
      final String jsonString =
          """
          {
            "sub": "test|foo@camunda.test",
            "email": "foo@camunda.test",
            "role": "R1",
            "group": "G1"
          }
          """;

      // given
      when(jwt.getTokenValue()).thenReturn(jsonString);
      // given
      final Map<String, Object> claims =
          Map.of(
              "sub", "",
              "email", "",
              "role", "R1",
              "group", "G1");
      when(jwt.getClaims()).thenReturn(claims);
      when(mappingServices.getMatchingMappings(claims))
          .thenReturn(
              List.of(
                  new MappingEntity("test-id", 5L, "role", "R1", "role-r1"),
                  new MappingEntity("test-id-2", 7L, "group", "G1", "group-g1")));

      final var roleR1 = new RoleEntity(8L, "roleR1", "Role R1", "R1 description");
      when(roleServices.getRolesByMemberIds(Set.of("test-id", "test-id-2"), EntityType.MAPPING))
          .thenReturn(List.of(roleR1));
      when(authorizationServices.getAuthorizedApplications(Set.of("test-id", "test-id-2", "8")))
          .thenReturn(List.of("*"));

      // when
      final OAuthContext oAuthContext =
          camundaOAuthPrincipalService.loadOAuthContext(jwt.getTokenValue());

      // then
      assertThat(oAuthContext).isNotNull();
      assertThat(oAuthContext.mappingIds()).isEqualTo(Set.of("test-id", "test-id-2"));
      final AuthenticationContext authenticationContext = oAuthContext.authenticationContext();
      assertThat(authenticationContext.roles()).containsAll(Set.of(roleR1));
      assertThat(authenticationContext.groups()).isEmpty();
      assertThat(authenticationContext.tenants()).isEmpty();
      assertThat(authenticationContext.authorizedApplications()).containsAll(Set.of("*"));
    }

    @Test
    public void shouldLoadTenantsFromMappings() {
      final String jsonString =
          """
          {
            "sub": "user@example.com",
            "%s": "scooby-doo",
            "role": "R1",
            "group": "G1"
          }
          """
              .formatted(USERNAME_CLAIM);

      // given
      when(jwt.getTokenValue()).thenReturn(jsonString);
      // given
      final Map<String, Object> claims =
          Map.of("sub", "user@example.com", USERNAME_CLAIM, "scooby-doo");
      when(jwt.getClaims()).thenReturn(claims);

      final var mapping1 = new MappingEntity("map-1", 1L, "role", "R1", "role-r1");
      final var mapping2 = new MappingEntity("map-2", 2L, "group", "G1", "group-g1");

      when(mappingServices.getMatchingMappings(claims)).thenReturn(List.of(mapping1, mapping2));

      final var tenantEntity1 = new TenantEntity(100L, "t1", "Tenant One", "First Tenant");
      final var tenantEntity2 = new TenantEntity(200L, "t2", "Tenant Two", "Second Tenant");

      when(tenantServices.getTenantsByMemberIds(Set.of("map-1", "map-2")))
          .thenReturn(List.of(tenantEntity1, tenantEntity2));

      final var roleR1 = new RoleEntity(10L, "roleR1", "Role R1", "R1 description");
      when(roleServices.getRolesByMemberIds(Set.of("map-1", "map-2"), EntityType.MAPPING))
          .thenReturn(List.of(roleR1));

      when(authorizationServices.getAuthorizedApplications(Set.of("map-1", "map-2", "10")))
          .thenReturn(List.of("app-1", "app-2"));

      // when
      final OAuthContext oAuthContext =
          camundaOAuthPrincipalService.loadOAuthContext(jwt.getTokenValue());

      // then
      assertThat(oAuthContext).isNotNull();
      assertThat(oAuthContext.mappingIds()).isEqualTo(Set.of("map-1", "map-2"));

      final AuthenticationContext authenticationContext = oAuthContext.authenticationContext();
      assertThat(authenticationContext.username()).isEqualTo("scooby-doo");
      assertThat(authenticationContext.roles()).containsExactly(roleR1);
      assertThat(authenticationContext.groups()).isEmpty();
      assertThat(authenticationContext.tenants())
          .containsExactlyInAnyOrder(
              TenantServices.TenantDTO.fromEntity(tenantEntity1),
              TenantServices.TenantDTO.fromEntity(tenantEntity2));
      assertThat(authenticationContext.authorizedApplications())
          .containsExactlyInAnyOrder("app-1", "app-2");
    }
  }

  @Nested
  class NestedClaimConfiguration {
    private static final String USERNAME_CLAIM = "meta.email";
    @Mock private MappingServices mappingServices;
    @Mock private TenantServices tenantServices;
    @Mock private RoleServices roleServices;
    @Mock private GroupServices groupServices;
    @Mock private AuthorizationServices authorizationServices;
    @Mock private SecurityConfiguration securityConfiguration;
    @Mock private AuthenticationConfiguration authenticationConfiguration;
    @Mock private OidcAuthenticationConfiguration oidcAuthenticationConfiguration;
    @Mock private JwtDecoder jwtDecoder;
    @Mock private Jwt jwt;

    @BeforeEach
    public void setUp() throws Exception {
      MockitoAnnotations.openMocks(this).close();

      when(securityConfiguration.getAuthentication()).thenReturn(authenticationConfiguration);
      when(authenticationConfiguration.getOidc()).thenReturn(oidcAuthenticationConfiguration);
      when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn(USERNAME_CLAIM);
      when(oidcAuthenticationConfiguration.getApplicationIdClaim()).thenReturn("not-tested");
      when(jwtDecoder.decode(any())).thenReturn(jwt);

      camundaOAuthPrincipalService =
          new CamundaOAuthPrincipalService(
              mappingServices,
              tenantServices,
              roleServices,
              groupServices,
              authorizationServices,
              securityConfiguration,
              jwtDecoder);
    }

    @Test
    public void shouldFindUsernameInNestedClaim() {
      final String jsonString =
          """
          {
            "meta": {
              "email": "test"
            }
          }
          """;

      // given
      when(jwt.getTokenValue()).thenReturn(jsonString);
      // when
      final OAuthContext oAuthContext =
          camundaOAuthPrincipalService.loadOAuthContext(jwt.getTokenValue());

      // then
      assertThat(oAuthContext).isNotNull();

      final AuthenticationContext authenticationContext = oAuthContext.authenticationContext();
      assertThat(authenticationContext.username()).isEqualTo("test");
    }
  }
}
