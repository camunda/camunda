/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.validation.IdentityInitializationException;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Dispatches a broker command with the <code>IdentitySetupIntent.INITIALIZE</code> intent
 * containing the values required to initialize:
 *
 * <ul>
 *   <li>System default entities
 *   <li>User defined configuration at <code>camunda.security.initialization</code>
 * </ul>
 */
public final class IdentitySetupInitializer implements StreamProcessorLifecycleAware {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private final SecurityConfiguration securityConfig;
  private final boolean enableIdentitySetup;
  private final AuthorizationConfigurer authorizationConfigurer;
  private final TenantConfigurer tenantConfigurer;
  private final PasswordEncoder passwordEncoder;
  private final RoleConfigurer roleConfigurer;

  public IdentitySetupInitializer(
      final SecurityConfiguration securityConfig,
      final boolean enableIdentitySetup,
      final AuthorizationConfigurer authorizationConfigurer,
      final TenantConfigurer tenantConfigurer,
      final RoleConfigurer roleConfigurer) {
    this.securityConfig = securityConfig;
    this.enableIdentitySetup = enableIdentitySetup;
    this.authorizationConfigurer = authorizationConfigurer;
    this.tenantConfigurer = tenantConfigurer;
    this.roleConfigurer = roleConfigurer;
    passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    // We can disable identity setup by disabling the feature flag. This is useful to prevent
    // interference in our engine tests, as this setup will write "unexpected" commands/events
    if (!enableIdentitySetup) {
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
    context
        .getScheduleService()
        .runAtAsync(
            0L,
            (taskResultBuilder) -> {
              taskResultBuilder.appendCommandRecord(
                  IdentitySetupIntent.INITIALIZE, buildSetupRecord());
              return taskResultBuilder.build();
            });
  }

  private IdentitySetupRecord buildSetupRecord() {
    final var initialization = securityConfig.getInitialization();
    final var setupRecord = new IdentitySetupRecord();

    PlatformDefaultEntities.setupDefaultTenant(setupRecord);
    PlatformDefaultEntities.setupDefaultRoles(setupRecord);

    final Either<List<String>, List<AuthorizationRecord>> configuredAuthorizations =
        authorizationConfigurer.configureEntities(initialization.getAuthorizations());
    final Either<List<String>, List<TenantRecord>> configuredTenants =
        tenantConfigurer.configureEntities(initialization.getTenants());
    final Either<List<String>, List<RoleRecord>> configuredRoles =
        roleConfigurer.configureEntities(initialization.getRoles());

    configuredAuthorizations.ifLeft(
        (violations) -> {
          throw new IdentityInitializationException(
              "Found invalid authorizations. Aborting identity initialization! %n- %s"
                  .formatted(String.join(System.lineSeparator() + "- ", violations)));
        });
    configuredTenants.ifLeft(
        (violations) -> {
          throw new IdentityInitializationException(
              "Found invalid tenants. Aborting identity initialization! %n- %s"
                  .formatted(String.join(System.lineSeparator() + "- ", violations)));
        });
    configuredRoles.ifLeft(
        (violations) -> {
          throw new IdentityInitializationException(
              "Found invalid roles. Aborting identity initialization! %n- %s"
                  .formatted(String.join(System.lineSeparator() + "- ", violations)));
        });
    configuredAuthorizations.ifRight(auths -> auths.forEach(setupRecord::addAuthorization));
    configuredTenants.ifRight(tenants -> tenants.forEach(setupRecord::addTenant));
    configuredRoles.ifRight(roles -> roles.forEach(setupRecord::addRole));
    initialization
        .getTenants()
        .forEach(
            tenant ->
                tenantConfigurer.configureMembers(tenant).forEach(setupRecord::addTenantMember));

    initialization
        .getRoles()
        .forEach(role -> roleConfigurer.configureMembers(role).forEach(setupRecord::addRoleMember));

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

    setupRoleMembership(initialization, setupRecord);
    return setupRecord;
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
}
