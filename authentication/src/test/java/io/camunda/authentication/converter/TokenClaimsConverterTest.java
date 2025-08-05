/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static io.camunda.security.auth.OidcGroupsLoader.DERIVED_GROUPS_ARE_NOT_STRING_ARRAY;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.authentication.service.DefaultMembershipService;
import io.camunda.authentication.service.MembershipService;
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
public class TokenClaimsConverterTest {

  public static final String GROUP1_NAME = "idp-g1";
  public static final String GROUP2_NAME = "idp-g2";
  private TokenClaimsConverter converter;
  private MembershipService membershipService;

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

      membershipService =
          new DefaultMembershipService(
              mappingRuleServices,
              tenantServices,
              roleServices,
              groupServices,
              securityConfiguration);

      converter = new TokenClaimsConverter(securityConfiguration, membershipService);
    }

    @Test
    public void shouldThrowExceptionWhenNoClientIdClaimFound() {
      // given
      final Map<String, Object> claims = Map.of("sub", "user@example.com");

      // when
      final var exception =
          assertThatExceptionOfType(OAuth2AuthenticationException.class)
              .isThrownBy(() -> converter.convert(claims))
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
              .isThrownBy(() -> converter.convert(claims))
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
      final var camundaAuthentication = converter.convert(claims);

      // then
      assertThat(camundaAuthentication).isNotNull();
      assertThat(camundaAuthentication.authenticatedClientId()).isEqualTo("app-1");
      assertThat(camundaAuthentication.claims()).containsEntry(AUTHORIZED_CLIENT_ID, "app-1");
      assertThat(camundaAuthentication.claims()).containsEntry(USER_TOKEN_CLAIMS, claims);
    }
  }

  @Nested
  class UsernameClaimConfiguration {

    private static final String USERNAME_CLAIM = "email";

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
      when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn(USERNAME_CLAIM);
      when(oidcAuthenticationConfiguration.getClientIdClaim()).thenReturn("not-tested");
      when(mappingRuleServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(mappingRuleServices);
      when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(tenantServices);
      when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(roleServices);
      when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(groupServices);

      membershipService =
          new DefaultMembershipService(
              mappingRuleServices,
              tenantServices,
              roleServices,
              groupServices,
              securityConfiguration);

      converter = new TokenClaimsConverter(securityConfiguration, membershipService);
    }

    @Test
    public void shouldThrowExceptionWhenNoUsernameClaimFound() {
      // given
      final Map<String, Object> claims = Map.of("sub", "user@example.com");

      // when
      final var exception =
          assertThatExceptionOfType(OAuth2AuthenticationException.class)
              .isThrownBy(() -> converter.convert(claims))
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
              .isThrownBy(() -> converter.convert(claims))
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
      when(mappingRuleServices.getMatchingMappingRules(claims))
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
      final var authentication = converter.convert(claims);

      // then
      assertThat(authentication).isNotNull();
      assertThat(authentication.authenticatedUsername()).isEqualTo("foo@camunda.test");
      assertThat(authentication.claims()).containsEntry(AUTHORIZED_USERNAME, "foo@camunda.test");
      assertThat(authentication.claims()).containsEntry(USER_TOKEN_CLAIMS, claims);
      assertThat(authentication.authenticatedMappingRuleIds())
          .containsExactlyInAnyOrder("test-id", "test-id-2");
      assertThat(authentication.authenticatedRoleIds())
          .containsExactlyInAnyOrder(roleR1.roleId(), groupRole.roleId());
      assertThat(authentication.authenticatedGroupIds()).containsExactly("group-g1");
      assertThat(authentication.claims().get(USER_GROUPS_CLAIMS)).isNull();
      assertThat(authentication.authenticatedTenantIds())
          .containsExactlyInAnyOrder(tenantT1.tenantId(), groupTenant.tenantId());
    }

    @Test
    public void shouldLoadTenantsFromMappingRules() {
      // given
      final Map<String, Object> claims =
          Map.of("sub", "user@example.com", USERNAME_CLAIM, "scooby-doo");

      final var mappingRule1 = new MappingRuleEntity("map-1", 1L, "role", "R1", "role-r1");
      final var mappingRule2 = new MappingRuleEntity("map-2", 2L, "group", "G1", "group-g1");

      when(mappingRuleServices.getMatchingMappingRules(claims))
          .thenReturn(Stream.of(mappingRule1, mappingRule2));

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
      final var authentication = converter.convert(claims);

      // then
      assertThat(authentication).isNotNull();
      assertThat(authentication.authenticatedMappingRuleIds())
          .containsExactlyInAnyOrder("map-1", "map-2");
      assertThat(authentication.authenticatedUsername()).isEqualTo("scooby-doo");
      assertThat(authentication.authenticatedRoleIds()).containsExactly(roleR1.roleId());
      assertThat(authentication.authenticatedGroupIds()).containsExactly("group-g1");
      assertThat(authentication.authenticatedTenantIds())
          .containsExactlyInAnyOrder(tenantEntity1.tenantId(), tenantEntity2.tenantId());
    }
  }

  @Nested
  class GroupsClaimConfiguration {

    private static final String GROUPS_CLAIM = "$.groups[*].['name']";

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
      when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn("sub");
      when(oidcAuthenticationConfiguration.getClientIdClaim()).thenReturn("not-tested");
      when(oidcAuthenticationConfiguration.getGroupsClaim()).thenReturn(GROUPS_CLAIM);
      when(mappingRuleServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(mappingRuleServices);
      when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(tenantServices);
      when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(roleServices);
      when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
          .thenReturn(groupServices);

      membershipService =
          new DefaultMembershipService(
              mappingRuleServices,
              tenantServices,
              roleServices,
              groupServices,
              securityConfiguration);

      converter = new TokenClaimsConverter(securityConfiguration, membershipService);
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

      // when
      final var authentication = converter.convert(claims);

      // then
      assertThat(authentication.authenticatedGroupIds())
          .containsExactlyInAnyOrder(GROUP1_NAME, GROUP2_NAME);
      assertThat((List<String>) authentication.claims().get(USER_GROUPS_CLAIMS))
          .containsExactlyInAnyOrder(GROUP1_NAME, GROUP2_NAME);
    }

    @Test
    public void shouldLoadGroupWhenGroupsClaimIsAString() {
      // given
      when(oidcAuthenticationConfiguration.getGroupsClaim()).thenReturn("$.groups['name']");

      membershipService =
          new DefaultMembershipService(
              mappingRuleServices,
              tenantServices,
              roleServices,
              groupServices,
              securityConfiguration);
      converter = new TokenClaimsConverter(securityConfiguration, membershipService);
      final Map<String, Object> claims =
          Map.of("groups", Map.of("name", GROUP1_NAME, "id", "idp-g1-id"), "sub", "user1");

      // when
      final var authentication = converter.convert(claims);

      // then
      assertThat(authentication.authenticatedGroupIds()).containsExactly(GROUP1_NAME);
      assertThat((List<String>) authentication.claims().get(USER_GROUPS_CLAIMS))
          .containsExactly(GROUP1_NAME);
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
              .isThrownBy(() -> converter.convert(claims))
              .actual();

      assertThat(exception.getMessage())
          .isEqualTo(DERIVED_GROUPS_ARE_NOT_STRING_ARRAY.formatted(GROUPS_CLAIM));
    }
  }
}
