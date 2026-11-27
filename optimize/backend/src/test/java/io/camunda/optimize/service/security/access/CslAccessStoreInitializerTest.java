/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.optimize.dto.optimize.access.AuthorizationDto;
import io.camunda.optimize.dto.optimize.access.MappingRuleDto;
import io.camunda.optimize.dto.optimize.access.RoleDto;
import io.camunda.optimize.dto.optimize.access.RoleMemberDto;
import io.camunda.optimize.service.db.repository.AuthorizationRepository;
import io.camunda.optimize.service.db.repository.MappingRuleRepository;
import io.camunda.optimize.service.db.repository.RoleRepository;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.security.api.model.authz.AuthorizationOwnerType;
import io.camunda.security.api.model.authz.AuthorizationResourceMatcher;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.api.model.config.initialization.ConfiguredAuthorization;
import io.camunda.security.api.model.config.initialization.ConfiguredMappingRule;
import io.camunda.security.api.model.config.initialization.ConfiguredRole;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CslAccessStoreInitializerTest {

  private final InMemoryRoles roles = new InMemoryRoles();
  private final InMemoryMappingRules mappingRules = new InMemoryMappingRules();
  private final InMemoryAuthorizations authorizations = new InMemoryAuthorizations();
  private CamundaSecurityLibraryProperties properties;

  @BeforeEach
  void setup() {
    properties = new CamundaSecurityLibraryProperties();
    // the defaults are immutable; Spring binds configured values through these setters
    properties.getInitialization().setMappingRules(new ArrayList<>());
    properties.getInitialization().setRoles(new ArrayList<>());
    properties.getInitialization().setAuthorizations(new ArrayList<>());
    properties.getInitialization().setDefaultRoles(new LinkedHashMap<>());
  }

  @Test
  void shouldCreateTheAdminRoleWithItsMembersAndOptimizeGrants() {
    // given
    adminMembers("users", List.of("demo"));
    adminMembers("mappingRules", List.of("admins-claim"));

    // when
    start();

    // then
    assertThat(roles.get("admin")).isPresent();
    assertThat(roles.getMembers("admin"))
        .containsExactlyInAnyOrder(
            new RoleMemberDto(EntityType.USER, "demo"),
            new RoleMemberDto(EntityType.MAPPING_RULE, "admins-claim"));
    assertThat(authorizations.getAll())
        .extracting(
            AuthorizationDto::ownerType, AuthorizationDto::ownerId, AuthorizationDto::resourceId)
        .containsExactlyInAnyOrder(
            tuple(AuthorizationOwnerType.ROLE, "admin", "optimize"),
            tuple(AuthorizationOwnerType.ROLE, "admin", "optimize-admin"));
  }

  @Test
  void shouldInitializeConfiguredMappingRulesRolesAndComponentGrantsOnly() {
    // given
    adminMembers("users", List.of("demo"));
    properties
        .getInitialization()
        .getMappingRules()
        .add(new ConfiguredMappingRule("finance", "department", "finance"));
    properties
        .getInitialization()
        .getRoles()
        .add(
            new ConfiguredRole(
                "analysts", "Analysts", null, List.of(), List.of(), List.of("finance"), List.of()));
    properties
        .getInitialization()
        .getAuthorizations()
        .addAll(
            List.of(
                ConfiguredAuthorization.idBased(
                    AuthorizationOwnerType.ROLE,
                    "analysts",
                    AuthorizationResourceType.COMPONENT,
                    "optimize",
                    Set.of(PermissionType.ACCESS)),
                ConfiguredAuthorization.wildcard(
                    AuthorizationOwnerType.ROLE,
                    "analysts",
                    AuthorizationResourceType.PROCESS_DEFINITION,
                    Set.of(PermissionType.READ_PROCESS_DEFINITION))));

    // when
    start();

    // then
    assertThat(mappingRules.get("finance"))
        .contains(new MappingRuleDto("finance", "department", "finance", "finance"));
    assertThat(roles.getMembers("analysts"))
        .containsExactly(new RoleMemberDto(EntityType.MAPPING_RULE, "finance"));
    assertThat(authorizations.getAll())
        .filteredOn(authorization -> authorization.ownerId().equals("analysts"))
        .singleElement()
        .satisfies(
            authorization -> {
              assertThat(authorization.resourceType())
                  .isEqualTo(AuthorizationResourceType.COMPONENT);
              assertThat(authorization.resourceMatcher())
                  .isEqualTo(AuthorizationResourceMatcher.ID);
              assertThat(authorization.resourceId()).isEqualTo("optimize");
            });
  }

  @Test
  void shouldKeepWhatAnAdminChangedOnRestart() {
    // given
    adminMembers("users", List.of("demo"));
    start();
    roles.upsert(new RoleDto("admin", "Optimize admins", "renamed by an admin"));
    final AuthorizationDto adminGrant = grantOn("optimize-admin");
    authorizations.upsert(
        new AuthorizationDto(
            adminGrant.id(),
            adminGrant.ownerType(),
            adminGrant.ownerId(),
            adminGrant.resourceType(),
            adminGrant.resourceMatcher(),
            adminGrant.resourceId(),
            null,
            Set.of(PermissionType.ACCESS, PermissionType.READ)));

    // when
    start();

    // then
    assertThat(roles.get("admin"))
        .contains(new RoleDto("admin", "Optimize admins", "renamed by an admin"));
    assertThat(authorizations.getAll()).hasSize(2);
    assertThat(grantOn("optimize-admin").permissionTypes())
        .containsExactlyInAnyOrder(PermissionType.ACCESS, PermissionType.READ);
  }

  @Test
  void shouldGiveEveryInstanceTheSameIdsForTheSameGrants() {
    // given two instances that both find an empty store
    adminMembers("users", List.of("demo"));
    final InMemoryAuthorizations otherInstanceAuthorizations = new InMemoryAuthorizations();

    // when
    start();
    new CslAccessStoreInitializer(
            properties,
            new InMemoryRoles(),
            new InMemoryMappingRules(),
            otherInstanceAuthorizations)
        .afterSingletonsInstantiated();

    // then their writes land on the same documents rather than duplicating the grants
    assertThat(authorizations.getAll())
        .extracting(AuthorizationDto::id)
        .hasSize(2)
        .containsExactlyInAnyOrderElementsOf(
            otherInstanceAuthorizations.getAll().stream().map(AuthorizationDto::id).toList());
  }

  @Test
  void shouldIdentifyInitializedGrantsByOwnerAndResource() {
    // given
    adminMembers("users", List.of("demo"));

    // when
    start();

    // then
    assertThat(authorizations.getAll())
        .extracting(AuthorizationDto::id)
        .containsExactlyInAnyOrder(
            "ROLE:admin:COMPONENT:ID:optimize", "ROLE:admin:COMPONENT:ID:optimize-admin");
  }

  @Test
  void shouldRestoreConfiguredMembersOnRestart() {
    // given
    adminMembers("users", List.of("demo"));
    start();
    roles.removeMember("admin", new RoleMemberDto(EntityType.USER, "demo"));

    // when
    start();

    // then the configuration stays the source of the bootstrap admins
    assertThat(roles.getMembers("admin"))
        .containsExactly(new RoleMemberDto(EntityType.USER, "demo"));
  }

  @Test
  void shouldFailWithoutAnAdmin() {
    // when / then
    assertThatThrownBy(this::start)
        .isInstanceOf(OptimizeConfigurationException.class)
        .hasMessageContaining("camunda.security.initialization.defaultRoles.admin.users");
  }

  @Test
  void shouldFailWhenTheAdminRoleHasNoMembers() {
    // given
    adminMembers("users", List.of());

    // when / then
    assertThatThrownBy(this::start).isInstanceOf(OptimizeConfigurationException.class);
  }

  @Test
  void shouldStartWithAnAdminGrantedDirectlyToAUser() {
    // given an admin granted earlier, without any initialization config
    authorizations.upsert(
        new AuthorizationDto(
            "1",
            AuthorizationOwnerType.USER,
            "demo",
            AuthorizationResourceType.COMPONENT,
            AuthorizationResourceMatcher.ID,
            "optimize-admin",
            null,
            Set.of(PermissionType.ACCESS)));

    // when / then
    start();
  }

  @Test
  void shouldRejectAnUnknownMemberKindForTheAdminRole() {
    // given
    adminMembers("people", List.of("demo"));

    // when / then
    assertThatThrownBy(this::start)
        .isInstanceOf(OptimizeConfigurationException.class)
        .hasMessageContaining("people");
  }

  private void adminMembers(final String kind, final Collection<String> memberIds) {
    properties
        .getInitialization()
        .getDefaultRoles()
        .computeIfAbsent("admin", ignored -> new LinkedHashMap<>())
        .put(kind, memberIds);
  }

  private AuthorizationDto grantOn(final String resourceId) {
    return authorizations.getAll().stream()
        .filter(authorization -> authorization.resourceId().equals(resourceId))
        .findFirst()
        .orElseThrow();
  }

  private void start() {
    new CslAccessStoreInitializer(properties, roles, mappingRules, authorizations)
        .afterSingletonsInstantiated();
  }

  private static final class InMemoryRoles implements RoleRepository {

    private final Map<String, RoleDto> roles = new HashMap<>();
    private final Map<String, List<RoleMemberDto>> members = new HashMap<>();

    @Override
    public Optional<RoleDto> get(final String roleId) {
      return Optional.ofNullable(roles.get(roleId));
    }

    @Override
    public List<RoleDto> getAll() {
      return List.copyOf(roles.values());
    }

    @Override
    public void upsert(final RoleDto role) {
      roles.put(role.roleId(), role);
    }

    @Override
    public void delete(final String roleId) {
      roles.remove(roleId);
      members.remove(roleId);
    }

    @Override
    public List<RoleMemberDto> getMembers(final String roleId) {
      return List.copyOf(members.getOrDefault(roleId, List.of()));
    }

    @Override
    public void addMember(final String roleId, final RoleMemberDto member) {
      final List<RoleMemberDto> roleMembers =
          members.computeIfAbsent(roleId, ignored -> new ArrayList<>());
      if (!roleMembers.contains(member)) {
        roleMembers.add(member);
      }
    }

    @Override
    public void removeMember(final String roleId, final RoleMemberDto member) {
      members.getOrDefault(roleId, new ArrayList<>()).remove(member);
    }

    @Override
    public Set<String> getRoleIdsOfMembers(
        final EntityType memberType, final Collection<String> memberIds) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class InMemoryMappingRules implements MappingRuleRepository {

    private final Map<String, MappingRuleDto> mappingRules = new HashMap<>();

    @Override
    public Optional<MappingRuleDto> get(final String mappingRuleId) {
      return Optional.ofNullable(mappingRules.get(mappingRuleId));
    }

    @Override
    public List<MappingRuleDto> getAll() {
      return List.copyOf(mappingRules.values());
    }

    @Override
    public void upsert(final MappingRuleDto mappingRule) {
      mappingRules.put(mappingRule.mappingRuleId(), mappingRule);
    }

    @Override
    public void delete(final String mappingRuleId) {
      mappingRules.remove(mappingRuleId);
    }
  }

  private static final class InMemoryAuthorizations implements AuthorizationRepository {

    private final Map<String, AuthorizationDto> authorizations = new HashMap<>();

    @Override
    public Optional<AuthorizationDto> get(final String id) {
      return Optional.ofNullable(authorizations.get(id));
    }

    @Override
    public List<AuthorizationDto> getAll() {
      return List.copyOf(authorizations.values());
    }

    @Override
    public void upsert(final AuthorizationDto authorization) {
      authorizations.put(authorization.id(), authorization);
    }

    @Override
    public void delete(final String id) {
      authorizations.remove(id);
    }
  }
}
