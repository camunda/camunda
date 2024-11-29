/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.value.UserType;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.slf4j.Logger;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class IdentitySetupInitializer implements StreamProcessorLifecycleAware, Task {
  public static final String DEFAULT_USER_USERNAME = "demo";
  public static final String DEFAULT_USER_PASSWORD = "demo";
  public static final String DEFAULT_USER_EMAIL = "demo@demo.com";
  public static final String DEFAULT_ROLE_NAME = "Admin";
  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  private final KeyGenerator keyGenerator;
  private final EngineConfiguration config;
  private final PasswordEncoder passwordEncoder;
  private final RoleState roleState;
  private final UserState userState;

  public IdentitySetupInitializer(
      final KeyGenerator keyGenerator,
      final EngineConfiguration config,
      final MutableProcessingState processingState) {
    this.keyGenerator = keyGenerator;
    this.config = config;
    passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    userState = processingState.getUserState();
    roleState = processingState.getRoleState();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (!config.isEnableAuthorization()) {
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
    final var userExists = userState.getUser(DEFAULT_USER_USERNAME).isPresent();
    if (roleExists && userExists) {
      LOG.debug("Skipping identity setup as default user and role already exist");
      return;
    }

    // We use a timestamp of 0L to ensure this is runs immediately once the stream processor is
    // started,
    context.getScheduleService().runAtAsync(0L, this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final var defaultRole =
        new RoleRecord().setRoleKey(keyGenerator.nextKey()).setName(DEFAULT_ROLE_NAME);
    final var defaultUser =
        new UserRecord()
            .setUserKey(keyGenerator.nextKey())
            .setUsername(DEFAULT_USER_USERNAME)
            .setName(DEFAULT_USER_USERNAME)
            .setEmail(DEFAULT_USER_EMAIL)
            .setPassword(passwordEncoder.encode(DEFAULT_USER_PASSWORD))
            .setUserType(UserType.DEFAULT);

    final var setupRecord =
        new IdentitySetupRecord().setDefaultRole(defaultRole).setDefaultUser(defaultUser);

    taskResultBuilder.appendCommandRecord(IdentitySetupIntent.INITIALIZE, setupRecord);
    return taskResultBuilder.build();
  }
}
