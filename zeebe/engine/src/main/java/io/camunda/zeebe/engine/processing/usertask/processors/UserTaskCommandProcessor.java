/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.collection.Tuple;

/**
 * Interface for processing user task commands.
 *
 * <p>This interface defines the structure for handling various commands related to user tasks in
 * the system. Each command type (e.g., COMPLETE, ASSIGN, CLAIM, etc.) will have its own
 * implementation of this interface. It supports validation (through the `check` method), command
 * processing (via `onCommand`), and finalization of the command processing (via
 * `onFinalizeCommand`).
 *
 * <p>Implementations of this interface are used by the `UserTaskProcessor` to process commands in a
 * structured and consistent manner.
 */
public interface UserTaskCommandProcessor {

  /**
   * Validates the command before processing.
   *
   * <p>This method can be used to ensure that the command is valid and can proceed. For example, it
   * can check if the task is in the correct lifecycle state or if necessary conditions are met. If
   * the validation fails, it returns a rejection with an appropriate error message; otherwise, it
   * proceeds with processing.
   *
   * @param command the user task command that needs validation
   * @return Either a tuple of rejection type and error message if validation fails, or the user
   *     task record if validation succeeds
   */
  default Either<Tuple<RejectionType, String>, UserTaskRecord> check(
      final TypedRecord<UserTaskRecord> command) {
    return Either.right(command.getValue());
  }

  /**
   * Executes the main logic of the command.
   *
   * <p>This method is responsible for executing the core business logic of the user task command,
   * such as updating the state, writing events, or modifying variables. This step occurs after the
   * command passes validation.
   *
   * <p>Implementations should define the necessary steps to process the command and move the task
   * through its lifecycle.
   *
   * @param command the original command record to be processed
   * @param userTaskRecord the validated user task record that the command acts upon
   */
  default void onCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {}

  /**
   * Finalizes the processing of the command.
   *
   * <p>This method is called after the main command processing is complete. It can be used for
   * cleanup tasks, such as finalizing state transitions, triggering follow-up commands or events,
   * and writing final state updates.
   *
   * <p>This is typically the last step in the command processing lifecycle and ensures that the
   * task has reached its final state.
   *
   * @param command the original command record to be processed
   * @param userTaskRecord the validated user task record that the command acts upon
   */
  default void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {}
}
