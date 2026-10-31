/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.common.BannedInstanceCommandCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskLookupRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskLookupIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskLifecycleState;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

/**
 * Reads an active user task and the variables visible to it from the engine state, as they are
 * committed. Pending changes (e.g. a new assignee while task listeners run) are not returned; the
 * lifecycle state tells the client that a change is in progress.
 */
// TODO: authorize reading the user task and remove this exclusion,
// https://github.com/camunda/camunda/issues/66845
@ExcludeAuthorizationCheck
public final class UserTaskLookupFetchProcessor
    implements TypedRecordProcessor<UserTaskLookupRecord> {

  private static final String NO_USER_TASK_FOUND_MESSAGE =
      "Expected to fetch user task with key '%d', but no such user task was found";

  private final UserTaskState userTaskState;
  private final VariableState variableState;
  private final CslTenantCheck tenantCheck;
  private final BannedInstanceCommandCheck bannedInstanceCheck;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public UserTaskLookupFetchProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final CslTenantCheck tenantCheck) {
    userTaskState = processingState.getUserTaskState();
    variableState = processingState.getVariableState();
    bannedInstanceCheck = new BannedInstanceCommandCheck(processingState.getBannedInstanceState());
    this.tenantCheck = tenantCheck;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<UserTaskLookupRecord> command) {
    findUserTask(command)
        // A banned instance hit an unrecoverable error and was given up to keep the partition
        // healthy; it can only be canceled. Reject the read like every other user task command, so
        // the client learns the instance is banned instead of getting a task nobody can act on.
        .flatMap(bannedInstanceCheck::check)
        .ifRightOrLeft(
            userTask -> acceptCommand(command, userTask),
            rejection -> rejectCommand(command, rejection));
  }

  private Either<Rejection, UserTaskRecord> findUserTask(
      final TypedRecord<UserTaskLookupRecord> command) {
    final long userTaskKey = command.getKey();
    final var authorizedTenants = tenantCheck.resolveAuthorizedTenants(command.getAuthInfo());
    final var userTask = userTaskState.getUserTask(userTaskKey, authorizedTenants);
    if (userTask == null) {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND, NO_USER_TASK_FOUND_MESSAGE.formatted(userTaskKey)));
    }
    return Either.right(userTask);
  }

  private void acceptCommand(
      final TypedRecord<UserTaskLookupRecord> command, final UserTaskRecord userTask) {
    final long userTaskKey = command.getKey();
    final var request = command.getValue();

    // built from scratch, so response fields sent with the command never reach the FETCHED event
    final var fetched =
        new UserTaskLookupRecord()
            .setFetchVariables(request.getFetchVariables())
            .setTenantId(userTask.getTenantId());
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskLookupIntent.FETCHED, fetched);

    final UserTaskLookupRecord response =
        new UserTaskLookupRecord()
            .setFetchVariables(request.getFetchVariables())
            .setTenantId(userTask.getTenantId())
            .setUserTask(userTask)
            .setLifecycleState(toProtocol(userTaskState.getLifecycleState(userTaskKey)));
    response
        .getUserTask()
        .setVariables(collectVariables(userTask.getElementInstanceKey(), request));
    responseWriter.writeAcceptedResponseOnCommand(
        userTaskKey, UserTaskLookupIntent.FETCHED, response, command);
  }

  private DirectBuffer collectVariables(
      final long elementInstanceKey, final UserTaskLookupRecord request) {
    final var names = request.getFetchVariables().stream().map(BufferUtil::wrapString).toList();
    return names.isEmpty()
        ? variableState.getVariablesAsDocument(elementInstanceKey)
        : variableState.getVariablesAsDocument(elementInstanceKey, names);
  }

  private static UserTaskLifecycleState toProtocol(final LifecycleState lifecycleState) {
    return switch (lifecycleState) {
      case CREATING -> UserTaskLifecycleState.CREATING;
      case CREATED -> UserTaskLifecycleState.CREATED;
      case ASSIGNING -> UserTaskLifecycleState.ASSIGNING;
      case CLAIMING -> UserTaskLifecycleState.CLAIMING;
      case UPDATING -> UserTaskLifecycleState.UPDATING;
      case COMPLETING -> UserTaskLifecycleState.COMPLETING;
      case CANCELING -> UserTaskLifecycleState.CANCELING;
      case NOT_FOUND ->
          throw new IllegalStateException(
              "Expected a stored user task to have a lifecycle state, but got NOT_FOUND");
    };
  }

  private void rejectCommand(
      final TypedRecord<UserTaskLookupRecord> command, final Rejection rejection) {
    rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
    responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
  }
}
