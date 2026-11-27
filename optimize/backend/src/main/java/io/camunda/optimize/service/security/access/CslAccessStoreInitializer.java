/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.access;

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
import io.camunda.security.api.model.config.initialization.ConfiguredRole;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Initializes the access store from {@code camunda.security.initialization.*} and refuses to start
 * without an optimize-admin holder, so a fresh install always has someone who can grant access.
 *
 * <p>Initialization is insert-if-missing on every start, like the Orchestration Cluster: a role,
 * mapping rule or authorization that exists is left as an admin changed it, but a configured member
 * or grant that was removed comes back until it is removed from the configuration.
 *
 * <p>Runs once all singletons exist, which is after the schema is created and before the web server
 * accepts requests.
 */
@Component
@Conditional(CslAccessStoreCondition.class)
public class CslAccessStoreInitializer implements SmartInitializingSingleton {

  static final String OPTIMIZE_COMPONENT = "optimize";
  static final String OPTIMIZE_ADMIN_COMPONENT = "optimize-admin";
  static final String ADMIN_ROLE_ID = "admin";

  private static final Logger LOG = LoggerFactory.getLogger(CslAccessStoreInitializer.class);
  private static final String WILDCARD = "*";
  private static final Map<String, EntityType> MEMBER_TYPES_BY_CONFIG_KEY =
      Map.of(
          "users", EntityType.USER,
          "clients", EntityType.CLIENT,
          "groups", EntityType.GROUP,
          "mappingRules", EntityType.MAPPING_RULE);

  private final CamundaSecurityLibraryProperties securityProperties;
  private final RoleRepository roleRepository;
  private final MappingRuleRepository mappingRuleRepository;
  private final AuthorizationRepository authorizationRepository;

  public CslAccessStoreInitializer(
      final CamundaSecurityLibraryProperties securityProperties,
      final RoleRepository roleRepository,
      final MappingRuleRepository mappingRuleRepository,
      final AuthorizationRepository authorizationRepository) {
    this.securityProperties = securityProperties;
    this.roleRepository = roleRepository;
    this.mappingRuleRepository = mappingRuleRepository;
    this.authorizationRepository = authorizationRepository;
  }

  @Override
  public void afterSingletonsInstantiated() {
    applyInitializationConfig(securityProperties.getInitialization());
    verifyAdminExists();
  }

  private void applyInitializationConfig(final InitializationConfiguration initialization) {
    initialization
        .getMappingRules()
        .forEach(
            rule -> {
              if (mappingRuleRepository.get(rule.mappingRuleId()).isEmpty()) {
                mappingRuleRepository.upsert(
                    new MappingRuleDto(
                        rule.mappingRuleId(),
                        rule.claimName(),
                        rule.claimValue(),
                        rule.mappingRuleId()));
              }
            });

    initialization.getRoles().forEach(this::initializeRole);
    initialization
        .getDefaultRoles()
        .forEach(
            (roleId, membersByKind) -> {
              if (!ADMIN_ROLE_ID.equals(roleId)) {
                LOG.debug("Ignoring default role [{}], which grants nothing in Optimize.", roleId);
                return;
              }
              initializeAdminRole(membersByKind);
            });

    initialization.getAuthorizations().stream()
        // Optimize decides only component access; other grants belong to other apps
        .filter(
            authorization -> authorization.resourceType() == AuthorizationResourceType.COMPONENT)
        .map(CslAccessStoreInitializer::toAuthorization)
        .forEach(this::insertIfMissing);
  }

  private void initializeRole(final ConfiguredRole role) {
    insertRoleIfMissing(new RoleDto(role.roleId(), role.name(), role.description()));
    addMembers(role.roleId(), EntityType.USER, role.users());
    addMembers(role.roleId(), EntityType.CLIENT, role.clients());
    addMembers(role.roleId(), EntityType.GROUP, role.groups());
    addMembers(role.roleId(), EntityType.MAPPING_RULE, role.mappingRules());
  }

  private void initializeAdminRole(final Map<String, Collection<String>> membersByKind) {
    insertRoleIfMissing(new RoleDto(ADMIN_ROLE_ID, "Admin", null));
    membersByKind.forEach(
        (kind, memberIds) -> {
          final EntityType memberType = MEMBER_TYPES_BY_CONFIG_KEY.get(kind);
          if (memberType == null) {
            throw new OptimizeConfigurationException(
                String.format(
                    "Unknown member kind [%s] in camunda.security.initialization.defaultRoles.%s,"
                        + " expected one of %s.",
                    kind, ADMIN_ROLE_ID, MEMBER_TYPES_BY_CONFIG_KEY.keySet()));
          }
          addMembers(ADMIN_ROLE_ID, memberType, memberIds);
        });
    for (final String component : List.of(OPTIMIZE_COMPONENT, OPTIMIZE_ADMIN_COMPONENT)) {
      insertIfMissing(
          initializedAuthorization(
              AuthorizationOwnerType.ROLE,
              ADMIN_ROLE_ID,
              AuthorizationResourceType.COMPONENT,
              AuthorizationResourceMatcher.ID,
              component,
              null,
              Set.of(PermissionType.ACCESS)));
    }
  }

  private void insertRoleIfMissing(final RoleDto role) {
    if (roleRepository.get(role.roleId()).isEmpty()) {
      roleRepository.upsert(role);
    }
  }

  private void addMembers(
      final String roleId, final EntityType memberType, final Collection<String> memberIds) {
    if (memberIds == null) {
      return;
    }
    final List<RoleMemberDto> existing = roleRepository.getMembers(roleId);
    memberIds.stream()
        .map(memberId -> new RoleMemberDto(memberType, memberId))
        .filter(member -> !existing.contains(member))
        .forEach(member -> roleRepository.addMember(roleId, member));
  }

  /**
   * An authorization counts as present when one exists for the same owner and resource, whatever
   * its permissions, so permissions an admin changed are kept.
   */
  private void insertIfMissing(final AuthorizationDto authorization) {
    final boolean present =
        authorizationRepository.getAll().stream()
            .anyMatch(
                existing ->
                    existing.ownerType() == authorization.ownerType()
                        && Objects.equals(existing.ownerId(), authorization.ownerId())
                        && existing.resourceType() == authorization.resourceType()
                        && existing.resourceMatcher() == authorization.resourceMatcher()
                        && Objects.equals(existing.resourceId(), authorization.resourceId())
                        && Objects.equals(
                            existing.resourcePropertyName(), authorization.resourcePropertyName()));
    if (!present) {
      authorizationRepository.upsert(authorization);
    }
  }

  private void verifyAdminExists() {
    final boolean adminExists =
        authorizationRepository.getAll().stream()
            .filter(CslAccessStoreInitializer::grantsOptimizeAdmin)
            .anyMatch(this::hasHolder);
    if (!adminExists) {
      throw new OptimizeConfigurationException(
          "Nobody holds optimize-admin in Optimize's access store, so nobody could grant access."
              + " Configure an admin, for example with"
              + " camunda.security.initialization.defaultRoles.admin.users, or keep Management"
              + " Identity with "
              + CslAccessStoreCondition.MANAGEMENT_IDENTITY_ENABLED
              + "=true.");
    }
    LOG.info("Optimize's access store has an optimize-admin holder.");
  }

  private static boolean grantsOptimizeAdmin(final AuthorizationDto authorization) {
    return authorization.resourceType() == AuthorizationResourceType.COMPONENT
        && authorization.permissionTypes().contains(PermissionType.ACCESS)
        && (authorization.resourceMatcher() == AuthorizationResourceMatcher.ANY
            || OPTIMIZE_ADMIN_COMPONENT.equals(authorization.resourceId()));
  }

  /** A role grant reaches nobody until the role has a member. */
  private boolean hasHolder(final AuthorizationDto authorization) {
    return authorization.ownerType() != AuthorizationOwnerType.ROLE
        || !roleRepository.getMembers(authorization.ownerId()).isEmpty();
  }

  private static AuthorizationDto toAuthorization(final ConfiguredAuthorization configured) {
    final AuthorizationResourceMatcher matcher;
    final String resourceId;
    if (configured.hasResourcePropertyName()) {
      matcher = AuthorizationResourceMatcher.PROPERTY;
      resourceId = null;
    } else if (!configured.hasResourceId() || WILDCARD.equals(configured.resourceId())) {
      matcher = AuthorizationResourceMatcher.ANY;
      resourceId = WILDCARD;
    } else {
      matcher = AuthorizationResourceMatcher.ID;
      resourceId = configured.resourceId();
    }
    return initializedAuthorization(
        configured.ownerType(),
        configured.ownerId(),
        configured.resourceType(),
        matcher,
        resourceId,
        configured.resourcePropertyName(),
        Set.copyOf(configured.permissions()));
  }

  /**
   * Gives an initialized authorization an id derived from its owner and resource, so instances
   * starting at the same time on an empty store write the same document instead of one each, which
   * a later revoke would only partly remove.
   */
  static AuthorizationDto initializedAuthorization(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final AuthorizationResourceMatcher matcher,
      final String resourceId,
      final String resourcePropertyName,
      final Set<PermissionType> permissionTypes) {
    // the matcher tells which of resource id and property name the grant uses
    final String resource =
        matcher == AuthorizationResourceMatcher.PROPERTY ? resourcePropertyName : resourceId;
    return new AuthorizationDto(
        String.join(":", ownerType.name(), ownerId, resourceType.name(), matcher.name(), resource),
        ownerType,
        ownerId,
        resourceType,
        matcher,
        resourceId,
        resourcePropertyName,
        permissionTypes);
  }
}
