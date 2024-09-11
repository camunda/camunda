/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.Duration;
import java.util.Optional;

public final class DefaultUserCreator implements StreamProcessorLifecycleAware, Task {

  public static final String DEFAULT_USER_USERNAME = "demo";
  public static final String DEFAULT_USER_PASSWORD = "demo";
  private final KeyGenerator keyGenerator;
  private final MutableProcessingState processingState;
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;

  public DefaultUserCreator(
      final KeyGenerator keyGenerator,
      final MutableProcessingState processingState,
      final Writers writers) {
    this.keyGenerator = keyGenerator;
    this.processingState = processingState;
    commandWriter = writers.command();
    stateWriter = writers.state();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (context.getPartitionId() != Protocol.DEPLOYMENT_PARTITION) {
      // We should only create users on the deployment partition. The command will be distributed to
      // the other partitions using our command distribution mechanism.
      return;
    }

    context.getScheduleService().runDelayed(Duration.ofSeconds(1), this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    Optional.ofNullable(processingState.getUserState().getUser(DEFAULT_USER_USERNAME))
        .ifPresentOrElse(
            user -> {
              // The user already exists, so we don't need to create it again
            },
            () -> {
              final var defaultUser =
                  new UserRecord()
                      .setUsername(DEFAULT_USER_USERNAME)
                      .setName(DEFAULT_USER_USERNAME)
                      .setEmail("demo@demo.com")
                      .setPassword(DEFAULT_USER_PASSWORD)
                      .setUserType(AuthorizationOwnerType.DEFAULT_USER);

              //              commandWriter.appendNewCommand(UserIntent.CREATED, defaultUser);
              //              stateWriter.appendFollowUpEvent(
              //                  keyGenerator.nextKey(), UserIntent.CREATED, defaultUser);
              taskResultBuilder.appendCommandRecord(UserIntent.CREATE, defaultUser);
            });

    return taskResultBuilder.build();
  }
}
