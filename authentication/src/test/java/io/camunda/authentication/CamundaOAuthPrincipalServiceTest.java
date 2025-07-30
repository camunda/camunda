/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static io.camunda.security.auth.OidcGroupsLoader.DERIVED_GROUPS_ARE_NOT_STRING_ARRAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.authentication.entity.AuthenticationContext;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

@TestInstance(Lifecycle.PER_CLASS)
public class CamundaOAuthPrincipalServiceTest {

  public static final String GROUP1_NAME = "idp-g1";
  public static final String GROUP2_NAME = "idp-g2";
  private CamundaOAuthPrincipalService camundaOAuthPrincipalService;

  @Nested
  class ClientIdClaimConfiguration {
    private static final String APPLICATION_ID_CLAIM = "client-id";
    @Mock private MappingRuleServices mappingRuleServices;
    @Mock private TenantServices tenantServices;
    @Mock private RoleServices roleServices;
    @Mock private GroupServices groupServices;
    @Mock private SecurityConfiguration securityConfiguration;
    @Mock private AuthenticationConfiguration authenticationConfiguration;
    @Mock private OidcAuthenticationConfiguration oidcAuthenticationConfiguration;

    @BeforeEach
    public void setUp() throws Exception {
      MockitoAnnotations.openMocks(this).close();

      when(securityConfiguration.getAuthentication()).thenReturn(authenticationConfiguration);
      when(authenticationConfiguration.getOidc()).thenReturn(oidcAuthenticationConfiguration);
      when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn("not-tested");
      when(oidcAuthenticationConfiguration.getClientIdClaim()).thenReturn(APPLICATION_ID_CLAIM);
      when(mappingRuleServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(mappingRuleServices);
      when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(tenantServices);
      when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(roleServices);
      when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(groupServices);

      camundaOAuthPrincipalService =
          new CamundaOAuthPrincipalServiceImpl(
              mappingRuleServices,
              tenantServices,
              roleServices,
              groupServices,
              securityConfiguration);
    }

    @Test
    public void shouldThrowExceptionWhenNoClientIdClaimFound() {
      // given
      final Map<String, Object> claims = Map.of("sub", "user@example.com");

      // when
      final var exception =
          assertThatExceptionOfType(OAuth2AuthenticationException.class)
              .isThrownBy(() -> camundaOAuthPrincipalService.loadOAuthContext(claims))
              .actual();

      assertThat(exception.getMessage())
          .isEqualTo(
              "Neither username claim (%s) nor clientId claim (%s) could be found in the claims. Please check your OIDC configuration."
                  .formatted("not-tested", APPLICATION_ID_CLAIM));
    }

    @Test
    public void shouldThrowExceptionWhenClientIdClaimIsNotAString() {
      when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn("not-tested");
      when(oidcAuthenticationConfiguration.getClientIdClaim()).thenReturn(APPLICATION_ID_CLAIM);
      // given
      final Map<String, Object> claims = Map.of(APPLICATION_ID_CLAIM, List.of("app-1", "app-2"));

      // when
      final var exception =
          assertThatExceptionOfType(IllegalArgumentException.class)
              .isThrownBy(() -> camundaOAuthPrincipalService.loadOAuthContext(claims))
              .actual();

      assertThat(exception.getMessage())
          .isEqualTo(
              "Value for $['client-id'] is not a string. Please check your OIDC configuration.");
    }

    @Test
    public void shouldLoadUserWhenUsingClientIdClaim() {
      // given
      final Map<String, Object> claims =
          Map.of("sub", UUID.randomUUID().toString(), APPLICATION_ID_CLAIM, "app-1");

      // when
      final OAuthContext oAuthContext = camundaOAuthPrincipalService.loadOAuthContext(claims);

      // then
      assertThat(oAuthContext).isNotNull();
      final AuthenticationContext authenticationContext = oAuthContext.authenticationContext();
      assertThat(authenticationContext.clientId()).isEqualTo("app-1");
    }
  }

  @Nested
  class UsernameClaimConfiguration {
    private static final String USERNAME_CLAIM = "email";
    @Mock private MappingRuleServices mappingServices;
    @Mock private TenantServices tenantServices;
    @Mock private RoleServices roleServices;
    @Mock private GroupServices groupServices;
    @Mock private SecurityConfiguration securityConfiguration;
    @Mock private AuthenticationConfiguration authenticationConfiguration;
    @Mock private OidcAuthenticationConfiguration oidcAuthenticationConfiguration;

    @BeforeEach
    public void setUp() throws Exception {
      MockitoAnnotations.openMocks(this).close();

      when(securityConfiguration.getAuthentication()).thenReturn(authenticationConfiguration);
      when(authenticationConfiguration.getOidc()).thenReturn(oidcAuthenticationConfiguration);
      when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn(USERNAME_CLAIM);
      when(oidcAuthenticationConfiguration.getClientIdClaim()).thenReturn("not-tested");
      when(mappingServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(mappingServices);
      when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(tenantServices);
      when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(roleServices);
      when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(groupServices);

      camundaOAuthPrincipalService =
          new CamundaOAuthPrincipalServiceImpl(
              mappingServices, tenantServices, roleServices, groupServices, securityConfiguration);
    }

    @Test
    public void shouldThrowExceptionWhenNoUsernameClaimFound() {
      // given
      final Map<String, Object> claims = Map.of("sub", "user@example.com");

      // when
      final var exception =
          assertThatExceptionOfType(OAuth2AuthenticationException.class)
              .isThrownBy(() -> camundaOAuthPrincipalService.loadOAuthContext(claims))
              .actual();

      assertThat(exception.getMessage())
          .isEqualTo(
              "Neither username claim (%s) nor clientId claim (%s) could be found in the claims. Please check your OIDC configuration."
                  .formatted(USERNAME_CLAIM, "not-tested"));
    }

    @Test
    public void shouldThrowExceptionWhenUsernameClaimIsNotAString() {
      // given
      final Map<String, Object> claims = Map.of(USERNAME_CLAIM, List.of("app-1", "app-2"));

      // when
      final var exception =
          assertThatExceptionOfType(IllegalArgumentException.class)
              .isThrownBy(() -> camundaOAuthPrincipalService.loadOAuthContext(claims))
              .actual();

      assertThat(exception.getMessage())
          .isEqualTo("Value for $['email'] is not a string. Please check your OIDC configuration.");
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
      when(mappingServices.getMatchingMappingRules(claims))
          .thenReturn(
              Stream.of(
                  new MappingRuleEntity("test-id", 5L, "role", "R1", "role-r1"),
                  new MappingRuleEntity("test-id-2", 7L, "group", "G1", "group-g1")));

      final var groupRole = new RoleEntity(3L, "roleGroup", "Role Group", "description");
      when(groupServices.getGroupsByMemberTypeAndMemberIds(
              Map.of(
                  EntityType.MAPPING_RULE,
                  Set.of("test-id", "test-id-2"),
                  EntityType.USER,
                  Set.of("foo@camunda.test"))))
          .thenReturn(List.of(new GroupEntity(1L, "group-g1", "G1", "Group G1")));

      final var roleR1 = new RoleEntity(8L, "roleR1", "Role R1", "R1 description");
      when(roleServices.getRolesByMemberTypeAndMemberIds(
              Map.of(
                  EntityType.MAPPING_RULE,
                  Set.of("test-id", "test-id-2"),
                  EntityType.USER,
                  Set.of("foo@camunda.test"),
                  EntityType.GROUP,
                  Set.of("group-g1"))))
          .thenReturn(List.of(roleR1, groupRole));

      final var tenantT1 = new TenantEntity(100L, "t1", "Tenant One", "First Tenant");
      final var groupTenant = new TenantEntity(200L, "tenant1", "Tenant One", "First Tenant");
      when(tenantServices.getTenantsByMemberTypeAndMemberIds(
              Map.of(
                  EntityType.MAPPING_RULE,
                  Set.of("test-id", "test-id-2"),
                  EntityType.USER,
                  Set.of("foo@camunda.test"),
                  EntityType.GROUP,
                  Set.of("group-g1"),
                  EntityType.ROLE,
                  Set.of("roleR1", "roleGroup"))))
          .thenReturn(List.of(tenantT1, groupTenant));

      // when
      final OAuthContext oAuthContext = camundaOAuthPrincipalService.loadOAuthContext(claims);

      // then
      assertThat(oAuthContext).isNotNull();
      assertThat(oAuthContext.mappingIds()).isEqualTo(Set.of("test-id", "test-id-2"));
      final AuthenticationContext authenticationContext = oAuthContext.authenticationContext();
      assertThat(authenticationContext.roles())
          .containsAll(Set.of(roleR1.roleId(), groupRole.roleId()));
      assertThat(authenticationContext.groups()).containsExactly("group-g1");
      assertThat(authenticationContext.tenants())
          .containsAll(List.of(TenantDTO.fromEntity(tenantT1), TenantDTO.fromEntity(groupTenant)));
    }

    @Test
    public void shouldLoadTenantsFromMappings() {
      // given
      final Map<String, Object> claims =
          Map.of("sub", "user@example.com", USERNAME_CLAIM, "scooby-doo");

      final var mapping1 = new MappingRuleEntity("map-1", 1L, "role", "R1", "role-r1");
      final var mapping2 = new MappingRuleEntity("map-2", 2L, "group", "G1", "group-g1");

      when(mappingServices.getMatchingMappingRules(claims))
          .thenReturn(Stream.of(mapping1, mapping2));

      when(groupServices.getGroupsByMemberTypeAndMemberIds(
              Map.of(
                  EntityType.MAPPING_RULE,
                  Set.of("map-1", "map-2"),
                  EntityType.USER,
                  Set.of("scooby-doo"))))
          .thenReturn(List.of(new GroupEntity(1L, "group-g1", "G1", "Group G1")));

      final var roleR1 = new RoleEntity(10L, "roleR1", "Role R1", "R1 description");
      when(roleServices.getRolesByMemberTypeAndMemberIds(
              Map.of(
                  EntityType.MAPPING_RULE,
                  Set.of("map-1", "map-2"),
                  EntityType.GROUP,
                  Set.of("group-g1"),
                  EntityType.USER,
                  Set.of("scooby-doo"))))
          .thenReturn(List.of(roleR1));

      final var tenantEntity1 = new TenantEntity(100L, "t1", "Tenant One", "First Tenant");
      final var tenantEntity2 = new TenantEntity(200L, "t2", "Tenant Two", "Second Tenant");

      when(tenantServices.getTenantsByMemberTypeAndMemberIds(
              Map.of(
                  EntityType.MAPPING_RULE,
                  Set.of("map-1", "map-2"),
                  EntityType.USER,
                  Set.of("scooby-doo"),
                  EntityType.GROUP,
                  Set.of("group-g1"),
                  EntityType.ROLE,
                  Set.of("roleR1"))))
          .thenReturn(List.of(tenantEntity1, tenantEntity2));

      // when
      final OAuthContext oAuthContext = camundaOAuthPrincipalService.loadOAuthContext(claims);

      // then
      assertThat(oAuthContext).isNotNull();
      assertThat(oAuthContext.mappingIds()).isEqualTo(Set.of("map-1", "map-2"));

      final AuthenticationContext authenticationContext = oAuthContext.authenticationContext();
      assertThat(authenticationContext.username()).isEqualTo("scooby-doo");
      assertThat(authenticationContext.roles()).containsExactly(roleR1.roleId());
      assertThat(authenticationContext.groups()).containsExactly("group-g1");
      assertThat(authenticationContext.tenants())
          .containsExactlyInAnyOrder(
              TenantDTO.fromEntity(tenantEntity1), TenantDTO.fromEntity(tenantEntity2));
    }
  }

  @Nested
  class GroupsClaimConfiguration {
    private static final String GROUPS_CLAIM = "$.groups[*].['name']";
    @Mock private MappingRuleServices mappingServices;
    @Mock private TenantServices tenantServices;
    @Mock private RoleServices roleServices;
    @Mock private GroupServices groupServices;
    @Mock private SecurityConfiguration securityConfiguration;
    @Mock private AuthenticationConfiguration authenticationConfiguration;
    @Mock private OidcAuthenticationConfiguration oidcAuthenticationConfiguration;

    @BeforeEach
    public void setUp() throws Exception {
      MockitoAnnotations.openMocks(this).close();

      when(securityConfiguration.getAuthentication()).thenReturn(authenticationConfiguration);
      when(authenticationConfiguration.getOidc()).thenReturn(oidcAuthenticationConfiguration);
      when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn("sub");
      when(oidcAuthenticationConfiguration.getClientIdClaim()).thenReturn("not-tested");
      when(oidcAuthenticationConfiguration.getGroupsClaim()).thenReturn(GROUPS_CLAIM);
      when(mappingServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(mappingServices);
      when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(tenantServices);
      when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(roleServices);
      when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(groupServices);

      camundaOAuthPrincipalService =
          new CamundaOAuthPrincipalServiceImpl(
              mappingServices, tenantServices, roleServices, groupServices, securityConfiguration);
    }

    @Test
    public void shouldFetchClaimGroupWhenGroupClaimIsPresent() {
      // given
      final Map<String, Object> claims =
          Map.of(
              "groups",
              List.of(
                  Map.of("name", GROUP1_NAME, "id", "idp-g1-id"),
                  Map.of("name", GROUP2_NAME, "id", "idp-g2-id")),
              "sub",
              "user1");
      final var mappingRule1 = new MappingRuleEntity("map-1", 1L, "role", "R1", "role-r1");
      final var mappingRule2 = new MappingRuleEntity("map-2", 2L, "group", "G1", "group-g1");
      when(mappingServices.getMatchingMappingRules(claims))
          .thenReturn(Stream.of(mappingRule1, mappingRule2));
      when(groupServices.getGroupsByMemberIds(Set.of("map-1", "map-2"), EntityType.MAPPING_RULE))
          .thenReturn(List.of(new GroupEntity(1L, "local-g1", "G1", "Group G1")));

      // when
      final var oauthContext = camundaOAuthPrincipalService.loadOAuthContext(claims);

      // then
      assertThat(oauthContext.authenticationContext().groups())
          .containsExactlyInAnyOrder(GROUP1_NAME, GROUP2_NAME);
    }

    @Test
    public void shouldLoadGroupWhenGroupsClaimIsAString() {
      // given
      when(oidcAuthenticationConfiguration.getGroupsClaim()).thenReturn("$.groups['name']");

      camundaOAuthPrincipalService =
          new CamundaOAuthPrincipalServiceImpl(
              mappingServices, tenantServices, roleServices, groupServices, securityConfiguration);
      final Map<String, Object> claims =
          Map.of("groups", Map.of("name", GROUP1_NAME, "id", "idp-g1-id"), "sub", "user1");
      // when
      final var oauthContext = camundaOAuthPrincipalService.loadOAuthContext(claims);

      // then
      assertThat(oauthContext.authenticationContext().groups()).containsExactly(GROUP1_NAME);
    }

    @Test
    public void shouldThrowExceptionWhenGroupsClaimIsNotStringArray() {
      // given
      final Map<String, Object> claims =
          Map.of(
              "groups",
              List.of(
                  Map.of("name", List.of(GROUP1_NAME), "id", "idp-g1-id"),
                  Map.of("name", GROUP2_NAME, "id", "idp-g2-id")),
              "sub",
              "user1");
      // when
      final var exception =
          assertThatExceptionOfType(IllegalArgumentException.class)
              .isThrownBy(() -> camundaOAuthPrincipalService.loadOAuthContext(claims))
              .actual();

      assertThat(exception.getMessage())
          .isEqualTo(DERIVED_GROUPS_ARE_NOT_STRING_ARRAY.formatted(GROUPS_CLAIM));
    }
  }
}
