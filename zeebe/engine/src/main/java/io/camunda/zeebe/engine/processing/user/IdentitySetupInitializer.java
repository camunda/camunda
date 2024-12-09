/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserType;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import org.slf4j.Logger;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class IdentitySetupInitializer implements StreamProcessorLifecycleAware, Task {
  public static final String DEFAULT_ROLE_NAME = "Admin";
  public static final String DEFAULT_TENANT_ID = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  public static final String DEFAULT_TENANT_NAME = "Default";
  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  private final SecurityConfiguration securityConfig;
  private final PasswordEncoder passwordEncoder;
  private final RoleState roleState;
  private final UserState userState;
  private final TenantState tenantState;

  public IdentitySetupInitializer(
      final SecurityConfiguration securityConfig, final MutableProcessingState processingState) {
    this.securityConfig = securityConfig;
    passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    userState = processingState.getUserState();
    roleState = processingState.getRoleState();
    tenantState = processingState.getTenantState();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (!securityConfig.getAuthorizations().isEnabled()) {
      // If authorization is disabled we don't need to setup identity.
      LOG.debug("Skipping identity setup as authorization is disabled");
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

    final var roleExists = roleState.getRoleKeyByName(DEFAULT_ROLE_NAME).isPresent();
    final var userExists =
        userState
            .getUser(securityConfig.getInitialization().getDefaultUser().getUsername())
            .isPresent();
    final var tenantExists = tenantState.getTenantKeyById(DEFAULT_TENANT_ID).isPresent();
    if (roleExists && userExists && tenantExists) {
      LOG.debug("Skipping identity setup as default user, role, and tenant already exist");
      return;
    }

    // We use a timestamp of 0L to ensure this is runs immediately once the stream processor is
    // started,
    context.getScheduleService().runAtAsync(0L, this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final var defaultRole = new RoleRecord().setName(DEFAULT_ROLE_NAME);
    final var defaultUserConfig = securityConfig.getInitialization().getDefaultUser();
    final var defaultUser =
        new UserRecord()
            .setUsername(defaultUserConfig.getUsername())
            .setName(defaultUserConfig.getName())
            .setEmail(defaultUserConfig.getEmail())
            .setPassword(passwordEncoder.encode(defaultUserConfig.getPassword()))
            .setUserType(UserType.DEFAULT);
    final var defaultTenant =
        new TenantRecord().setTenantId(DEFAULT_TENANT_ID).setName(DEFAULT_TENANT_NAME);

    final var setupRecord =
        new IdentitySetupRecord()
            .setDefaultRole(defaultRole)
            .setDefaultUser(defaultUser)
            .setDefaultTenant(defaultTenant);

    taskResultBuilder.appendCommandRecord(IdentitySetupIntent.INITIALIZE, setupRecord);
    return taskResultBuilder.build();
  }
}
