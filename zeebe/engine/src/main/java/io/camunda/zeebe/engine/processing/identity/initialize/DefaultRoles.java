/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;

import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultRoles {

  public static void setupDefaultRoles(final IdentitySetupRecord setupRecord) {
    setupReadOnlyAdminRole(setupRecord);
    setupAdminRole(setupRecord);
    setupRpaRole(setupRecord);
    setupConnectorsRole(setupRecord);
    setupAppIntegrationsRole(setupRecord);
  }

  private static void setupReadOnlyAdminRole(final IdentitySetupRecord setupRecord) {
    final var readOnlyAdminRoleId = "readonly-admin";
    setupRecord.addRole(new RoleRecord().setRoleId(readOnlyAdminRoleId).setName("Readonly Admin"));

    for (final var resourceType : AuthorizationResourceType.values()) {
      if (resourceType == AuthorizationResourceType.UNSPECIFIED) {
        // We shouldn't add empty permissions for an unspecified resource type
        continue;
      }

      final var readBasedPermissions =
          resourceType.getSupportedPermissionTypes().stream()
              .filter(DefaultRoles::isReadPermission)
              .collect(Collectors.toSet());

      setupRecord.addAuthorization(
          new AuthorizationRecord()
              .setOwnerType(AuthorizationOwnerType.ROLE)
              .setOwnerId(readOnlyAdminRoleId)
              .setResourceType(resourceType)
              .setResourceMatcher(WILDCARD.getMatcher())
              .setResourceId(WILDCARD.getResourceId())
              .setPermissionTypes(readBasedPermissions));
    }
  }

  private static boolean isReadPermission(final PermissionType permissionType) {
    return switch (permissionType) {
      case ACCESS,
          READ,
          READ_PROCESS_INSTANCE,
          READ_USER_TASK,
          READ_DECISION_INSTANCE,
          READ_PROCESS_DEFINITION,
          READ_DECISION_DEFINITION,
          READ_USAGE_METRIC ->
          true;
      default -> false;
    };
  }

  private static void setupAdminRole(final IdentitySetupRecord setupRecord) {
    final var adminRoleId = DefaultRole.ADMIN.getId();
    setupRecord.addRole(new RoleRecord().setRoleId(adminRoleId).setName("Admin"));
    for (final var resourceType : AuthorizationResourceType.values()) {
      if (resourceType == AuthorizationResourceType.UNSPECIFIED) {
        // We shouldn't add empty permissions for an unspecified resource type
        continue;
      }

      setupRecord.addAuthorization(
          new AuthorizationRecord()
              .setOwnerType(AuthorizationOwnerType.ROLE)
              .setOwnerId(adminRoleId)
              .setResourceType(resourceType)
              .setResourceMatcher(WILDCARD.getMatcher())
              .setResourceId(WILDCARD.getResourceId())
              .setPermissionTypes(resourceType.getSupportedPermissionTypes()));
    }
    setupRecord.addTenantMember(
        new TenantRecord()
            .setTenantId(IdentitySetupInitializer.DEFAULT_TENANT_ID)
            .setEntityType(EntityType.ROLE)
            .setEntityId(adminRoleId));
  }

  private static void setupConnectorsRole(final IdentitySetupRecord setupRecord) {
    final var connectorsRoleId = DefaultRole.CONNECTORS.getId();
    setupRecord.addRole(new RoleRecord().setRoleId(connectorsRoleId).setName("Connectors"));
    setupRecord.addAuthorization(
        new AuthorizationRecord()
            .setOwnerType(AuthorizationOwnerType.ROLE)
            .setOwnerId(connectorsRoleId)
            .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .setResourceMatcher(WILDCARD.getMatcher())
            .setResourceId(WILDCARD.getResourceId())
            .setPermissionTypes(
                Set.of(
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_PROCESS_INSTANCE)));
    setupRecord.addAuthorization(
        new AuthorizationRecord()
            .setOwnerType(AuthorizationOwnerType.ROLE)
            .setOwnerId(connectorsRoleId)
            .setResourceType(AuthorizationResourceType.MESSAGE)
            .setResourceMatcher(WILDCARD.getMatcher())
            .setResourceId(WILDCARD.getResourceId())
            .setPermissionTypes(Set.of(PermissionType.CREATE)));
    setupRecord.addAuthorization(
        new AuthorizationRecord()
            .setOwnerType(AuthorizationOwnerType.ROLE)
            .setOwnerId(connectorsRoleId)
            .setResourceType(AuthorizationResourceType.DOCUMENT)
            .setResourceMatcher(WILDCARD.getMatcher())
            .setResourceId(WILDCARD.getResourceId())
            .setPermissionTypes(
                Set.of(PermissionType.CREATE, PermissionType.READ, PermissionType.DELETE)));
    setupRecord.addTenantMember(
        new TenantRecord()
            .setTenantId(IdentitySetupInitializer.DEFAULT_TENANT_ID)
            .setEntityType(EntityType.ROLE)
            .setEntityId(connectorsRoleId));
  }

  private static void setupAppIntegrationsRole(final IdentitySetupRecord setupRecord) {
    final var appIntegrationsRoleId = DefaultRole.APP_INTEGRATIONS.getId();
    setupRecord.addRole(
        new RoleRecord().setRoleId(appIntegrationsRoleId).setName("App Integrations"));
    setupRecord.addAuthorization(
        new AuthorizationRecord()
            .setOwnerType(AuthorizationOwnerType.ROLE)
            .setOwnerId(appIntegrationsRoleId)
            .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .setResourceMatcher(WILDCARD.getMatcher())
            .setResourceId(WILDCARD.getResourceId())
            .setPermissionTypes(
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.READ_USER_TASK,
                    PermissionType.UPDATE_USER_TASK)));
    setupRecord.addAuthorization(
        new AuthorizationRecord()
            .setOwnerType(AuthorizationOwnerType.ROLE)
            .setOwnerId(appIntegrationsRoleId)
            .setResourceType(AuthorizationResourceType.DOCUMENT)
            .setResourceMatcher(WILDCARD.getMatcher())
            .setResourceId(WILDCARD.getResourceId())
            .setPermissionTypes(Set.of(PermissionType.CREATE)));
    setupRecord.addTenantMember(
        new TenantRecord()
            .setTenantId(IdentitySetupInitializer.DEFAULT_TENANT_ID)
            .setEntityType(EntityType.ROLE)
            .setEntityId(appIntegrationsRoleId));
  }

  private static void setupRpaRole(final IdentitySetupRecord setupRecord) {
    final var rpaRoleId = DefaultRole.RPA.getId();
    setupRecord
        .addRole(new RoleRecord().setRoleId(rpaRoleId).setName("RPA"))
        .addAuthorization(
            new AuthorizationRecord()
                .setOwnerId(rpaRoleId)
                .setOwnerType(AuthorizationOwnerType.ROLE)
                .setResourceType(AuthorizationResourceType.RESOURCE)
                .setResourceMatcher(WILDCARD.getMatcher())
                .setResourceId(WILDCARD.getResourceId())
                .setPermissionTypes(Set.of(PermissionType.READ)))
        .addAuthorization(
            new AuthorizationRecord()
                .setOwnerId(rpaRoleId)
                .setOwnerType(AuthorizationOwnerType.ROLE)
                .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                .setResourceMatcher(WILDCARD.getMatcher())
                .setResourceId(WILDCARD.getResourceId())
                .setPermissionTypes(Set.of(PermissionType.UPDATE_PROCESS_INSTANCE)))
        .addTenantMember(
            new TenantRecord()
                .setTenantId(IdentitySetupInitializer.DEFAULT_TENANT_ID)
                .setEntityType(EntityType.ROLE)
                .setEntityId(rpaRoleId));
  }
}
