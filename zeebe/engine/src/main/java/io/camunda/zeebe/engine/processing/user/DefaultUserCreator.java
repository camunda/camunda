/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import static java.util.Optional.*;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.UserType;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import org.slf4j.Logger;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class DefaultUserCreator implements StreamProcessorLifecycleAware, Task {
  public static final String DEFAULT_USER_USERNAME = "demo";
  public static final String DEFAULT_USER_PASSWORD = "demo";
  public static final String DEFAULT_USER_EMAIL = "demo@demo.com";
  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  private final UserState userState;
  private final EngineConfiguration config;
  private final PasswordEncoder passwordEncoder;

  public DefaultUserCreator(
      final MutableProcessingState processingState, final EngineConfiguration config) {
    userState = processingState.getUserState();
    this.config = config;
    passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (!config.isEnableAuthorization()) {
      // If authorization is disabled we don't need to create the default user.
      LOG.debug("Skipping default user creation as authorization is disabled");
      return;
    }

    if (context.getPartitionId() != Protocol.DEPLOYMENT_PARTITION) {
      // We should only create users on the deployment partition. The command will be distributed to
      // the other partitions using our command distribution mechanism.
      LOG.debug(
          "Skipping default user creation on partition {} as it is not the deployment partition",
          context.getPartitionId());
      return;
    }

    // We use a timestamp of 0L to ensure this is runs immediately once the stream processor is
    // started,
    context.getScheduleService().runAtAsync(0L, this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    userState
        .getUser(DEFAULT_USER_USERNAME)
        .ifPresentOrElse(
            user -> {
              LOG.debug("Default user already exists, skipping creation");
            },
            () -> {
              final var defaultUser =
                  new UserRecord()
                      .setUsername(DEFAULT_USER_USERNAME)
                      .setName(DEFAULT_USER_USERNAME)
                      .setEmail(DEFAULT_USER_EMAIL)
                      .setPassword(passwordEncoder.encode(DEFAULT_USER_PASSWORD))
                      .setUserType(UserType.DEFAULT);

              taskResultBuilder.appendCommandRecord(UserIntent.CREATE, defaultUser);
              LOG.info(
                  "Created default user with username '{}' and password '{}'",
                  DEFAULT_USER_USERNAME,
                  DEFAULT_USER_PASSWORD);
            });

    return taskResultBuilder.build();
  }
}
