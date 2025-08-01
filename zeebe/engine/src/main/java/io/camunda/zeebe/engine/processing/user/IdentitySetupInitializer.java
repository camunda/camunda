/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import static io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.WILDCARD_PERMISSION;

import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.util.FeatureFlags;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class IdentitySetupInitializer implements StreamProcessorLifecycleAware, Task {
  public static final String DEFAULT_TENANT_ID = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  public static final String DEFAULT_TENANT_NAME = "Default";
  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  private final SecurityConfiguration securityConfig;
  private final FeatureFlags featureFlags;
  private final PasswordEncoder passwordEncoder;

  public IdentitySetupInitializer(
      final SecurityConfiguration securityConfig, final FeatureFlags featureFlags) {
    this.securityConfig = securityConfig;
    this.featureFlags = featureFlags;
    passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    // We can disable identity setup by disabling the feature flag. This is useful to prevent
    // interference in our engine tests, as this setup will write "unexpected" commands/events
    if (!featureFlags.enableIdentitySetup()) {
      return;
    }

    if (context.getPartitionId() != Protocol.DEPLOYMENT_PARTITION) {
      // We should only create users on the deployment partition. The command will be distributed to
      // the other partitions using our command distribution mechanism.
      LOG.debug(
          "Skipping identity setup on partition {} as it is not the deployment partition",
          context.getPartitionId());
      return;
    }

    // We use a timestamp of 0L to ensure this is runs immediately once the stream processor is
    // started,
    context.getScheduleService().runAtAsync(0L, this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final var initialization = securityConfig.getInitialization();
    final var setupRecord = new IdentitySetupRecord();

    setupReadOnlyAdminRole(setupRecord);
    setupAdminRole(setupRecord);
    setupRpaRole(setupRecord);
    setupConnectorsRole(setupRecord);

    initialization
        .getUsers()
        .forEach(
            user ->
                setupRecord.addUser(
                    new UserRecord()
                        .setUsername(user.getUsername())
                        .setName(user.getName())
                        .setEmail(user.getEmail())
                        .setPassword(passwordEncoder.encode(user.getPassword()))));

    initialization
        .getMappingRules()
        .forEach(
            mappingRule ->
                setupRecord.addMappingRule(
                    new MappingRuleRecord()
                        .setMappingRuleId(mappingRule.getMappingRuleId())
                        .setClaimName(mappingRule.getClaimName())
                        .setClaimValue(mappingRule.getClaimValue())
                        .setName(mappingRule.getMappingRuleId())));

    setupRecord.setDefaultTenant(
        new TenantRecord().setTenantId(DEFAULT_TENANT_ID).setName(DEFAULT_TENANT_NAME));

    setupRoleMembership(initialization, setupRecord);

    taskResultBuilder.appendCommandRecord(IdentitySetupIntent.INITIALIZE, setupRecord);
    return taskResultBuilder.build();
  }

  private static void setupRoleMembership(
      final InitializationConfiguration initialization, final IdentitySetupRecord setupRecord) {
    for (final var assignmentsForRole : initialization.getDefaultRoles().entrySet()) {
      final var roleId = assignmentsForRole.getKey();
      for (final var assignmentsForEntityType : assignmentsForRole.getValue().entrySet()) {
        final var entityType =
            switch (assignmentsForEntityType.getKey().toLowerCase()) {
              case "users" -> EntityType.USER;
              case "clients" -> EntityType.CLIENT;
              case "mappingrules" -> EntityType.MAPPING_RULE;
              case "groups" -> EntityType.GROUP;
              case "roles" -> EntityType.ROLE;
              default ->
                  throw new IllegalStateException(
                      "Unexpected entity type for role membership: %s"
                          .formatted(assignmentsForEntityType.getKey()));
            };
        for (final var entityId : assignmentsForEntityType.getValue()) {
          setupRecord.addRoleMember(
              new RoleRecord().setRoleId(roleId).setEntityType(entityType).setEntityId(entityId));
        }
      }
    }
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
              .filter(IdentitySetupInitializer::isReadPermission)
              .collect(Collectors.toSet());

      setupRecord.addAuthorization(
          new AuthorizationRecord()
              .setOwnerType(AuthorizationOwnerType.ROLE)
              .setOwnerId(readOnlyAdminRoleId)
              .setResourceType(resourceType)
              .setResourceMatcher(AuthorizationResourceMatcher.ANY)
              .setResourceId(WILDCARD_PERMISSION)
              .setPermissionTypes(readBasedPermissions));
    }
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
              .setResourceMatcher(AuthorizationResourceMatcher.ANY)
              .setResourceId(WILDCARD_PERMISSION)
              .setPermissionTypes(resourceType.getSupportedPermissionTypes()));
    }
    setupRecord.addTenantMember(
        new TenantRecord()
            .setTenantId(DEFAULT_TENANT_ID)
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
            .setResourceMatcher(AuthorizationResourceMatcher.ANY)
            .setResourceId(WILDCARD_PERMISSION)
            .setPermissionTypes(
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.UPDATE_PROCESS_INSTANCE)));
    setupRecord.addAuthorization(
        new AuthorizationRecord()
            .setOwnerType(AuthorizationOwnerType.ROLE)
            .setOwnerId(connectorsRoleId)
            .setResourceType(AuthorizationResourceType.MESSAGE)
            .setResourceMatcher(AuthorizationResourceMatcher.ANY)
            .setResourceId(WILDCARD_PERMISSION)
            .setPermissionTypes(Set.of(PermissionType.CREATE)));
    setupRecord.addTenantMember(
        new TenantRecord()
            .setTenantId(DEFAULT_TENANT_ID)
            .setEntityType(EntityType.ROLE)
            .setEntityId(connectorsRoleId));
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
                .setResourceMatcher(AuthorizationResourceMatcher.ANY)
                .setResourceId(WILDCARD_PERMISSION)
                .setPermissionTypes(Set.of(PermissionType.READ)))
        .addAuthorization(
            new AuthorizationRecord()
                .setOwnerId(rpaRoleId)
                .setOwnerType(AuthorizationOwnerType.ROLE)
                .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                .setResourceMatcher(AuthorizationResourceMatcher.ANY)
                .setResourceId(WILDCARD_PERMISSION)
                .setPermissionTypes(Set.of(PermissionType.UPDATE_PROCESS_INSTANCE)))
        .addTenantMember(
            new TenantRecord()
                .setTenantId(DEFAULT_TENANT_ID)
                .setEntityType(EntityType.ROLE)
                .setEntityId(rpaRoleId));
  }

  private static boolean isReadPermission(final PermissionType permissionType) {
    return switch (permissionType) {
      case ACCESS,
          READ,
          READ_PROCESS_INSTANCE,
          READ_USER_TASK,
          READ_DECISION_INSTANCE,
          READ_PROCESS_DEFINITION,
          READ_DECISION_DEFINITION ->
          true;
      default -> false;
    };
  }
}
