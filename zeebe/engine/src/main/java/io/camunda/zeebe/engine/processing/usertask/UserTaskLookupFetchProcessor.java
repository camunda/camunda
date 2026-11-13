/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAuthorizationCheck.TaskProperties.ALL;
import static io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAuthorizationCheck.processDefinition;
import static io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAuthorizationCheck.userTask;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.common.BannedInstanceCommandCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAuthorizationCheck;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskLookupRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskLookupIntent;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserTaskLifecycleState;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.ByteValue;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

/**
 * Reads an active user task and the variables visible to it from the engine state, as they are
 * committed. Pending changes (e.g. a new assignee while task listeners run) are not returned; the
 * lifecycle state tells the client that a change is in progress.
 *
 * <p>The variables come with the task, so they need the same permission as reading the task, as the
 * variables of an activated job do; no process instance read access is required.
 */
public final class UserTaskLookupFetchProcessor
    implements TypedRecordProcessor<UserTaskLookupRecord> {

  private static final String NO_USER_TASK_FOUND_MESSAGE =
      "Expected to fetch user task with key '%d', but no such user task was found";
  private static final String RESPONSE_TOO_LARGE_MESSAGE =
      "Expected to fetch user task with key '%d', but with %s the response is larger than the"
          + " max message size of %s. Narrow fetchVariables to the variables you need, or read the"
          + " variables from the user task search. Variables in the response: %s";

  private final UserTaskState userTaskState;
  private final VariableState variableState;
  private final CslTenantCheck tenantCheck;
  private final UserTaskAuthorizationCheck userTaskAuth;
  private final BannedInstanceCommandCheck bannedInstanceCheck;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final int maxResponseMessageSize;

  public UserTaskLookupFetchProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final CslTenantCheck tenantCheck,
      final UserTaskAuthorizationCheck userTaskAuth,
      final int maxResponseMessageSize) {
    userTaskState = processingState.getUserTaskState();
    variableState = processingState.getVariableState();
    bannedInstanceCheck = new BannedInstanceCommandCheck(processingState.getBannedInstanceState());
    this.tenantCheck = tenantCheck;
    this.userTaskAuth = userTaskAuth;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.maxResponseMessageSize = maxResponseMessageSize;
  }

  @Override
  public void processRecord(final TypedRecord<UserTaskLookupRecord> command) {
    findUserTask(command)
        .flatMap(userTask -> checkAuthorization(command, userTask))
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

  private Either<Rejection, UserTaskRecord> checkAuthorization(
      final TypedRecord<UserTaskLookupRecord> command, final UserTaskRecord userTask) {
    return userTaskAuth.check(
        command,
        userTask,
        processDefinition(PermissionType.READ_USER_TASK),
        userTask(PermissionType.READ, ALL));
  }

  private void acceptCommand(
      final TypedRecord<UserTaskLookupRecord> command, final UserTaskRecord userTask) {
    final long userTaskKey = command.getKey();
    final List<String> fetchVariables = command.getValue().getFetchVariables();

    final UserTaskLookupRecord response =
        new UserTaskLookupRecord()
            .setFetchVariables(fetchVariables)
            .setTenantId(userTask.getTenantId())
            .setUserTask(userTask)
            .setLifecycleState(toProtocol(userTaskState.getLifecycleState(userTaskKey)));
    // copied: the variable state reuses this buffer on its next read
    final DirectBuffer variables =
        BufferUtil.cloneBuffer(collectVariables(userTask.getElementInstanceKey(), fetchVariables));
    response.getUserTask().setVariables(variables);

    // Checked before anything is written, so a rejected read leaves only the rejection on the log.
    final int responseLength = response.getLength();
    if (responseLength > maxResponseMessageSize) {
      rejectCommand(
          command,
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              RESPONSE_TOO_LARGE_MESSAGE.formatted(
                  userTaskKey,
                  formatSize(responseLength),
                  formatSize(maxResponseMessageSize),
                  describeVariableSizes(variables))));
      return;
    }

    // built from scratch, so response fields sent with the command never reach the FETCHED event
    final var fetched =
        new UserTaskLookupRecord()
            .setFetchVariables(fetchVariables)
            .setTenantId(userTask.getTenantId());
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskLookupIntent.FETCHED, fetched);
    responseWriter.writeAcceptedResponseOnCommand(
        userTaskKey, UserTaskLookupIntent.FETCHED, response, command);
  }

  /**
   * Lists every variable of the document with the size of its value, largest first, so the caller
   * can pick the names to pass in {@code fetchVariables}. All of them, not only the largest: {@code
   * fetchVariables} names the variables to return, so narrowing the read needs every name. The
   * sizes are of the MessagePack values, close to but not the same as their JSON size.
   */
  private static String describeVariableSizes(final DirectBuffer document) {
    final var reader = new MsgPackReader().wrap(document, 0, document.capacity());
    final int variableCount = reader.readMapHeader();
    final List<VariableSize> sizes = new ArrayList<>(variableCount);
    for (int i = 0; i < variableCount; i++) {
      final String name = BufferUtil.bufferAsString(reader.readToken().getValueBuffer());
      final int valueOffset = reader.getOffset();
      reader.skipValue();
      sizes.add(new VariableSize(name, reader.getOffset() - valueOffset));
    }
    return sizes.stream()
        .sorted(Comparator.comparingInt(VariableSize::size).reversed())
        .map(variable -> "%s (~%s)".formatted(variable.name(), formatSize(variable.size())))
        .collect(Collectors.joining(", "));
  }

  /**
   * Prints a size the way the max message size is configured ("4MB"): MB and KB as Spring's
   * DataSize reads them, 1024 * 1024 and 1024 bytes, so the limit in the message reads like the
   * setting.
   */
  private static String formatSize(final long bytes) {
    return ByteValue.prettyPrint(bytes).replace("iB", "B");
  }

  private DirectBuffer collectVariables(
      final long elementInstanceKey, final List<String> fetchVariables) {
    final var names = fetchVariables.stream().map(BufferUtil::wrapString).toList();
    return names.isEmpty()
        ? variableState.getVariablesAsDocument(elementInstanceKey)
        : variableState.getVariablesAsDocument(elementInstanceKey, names);
  }

  private static UserTaskLifecycleState toProtocol(final LifecycleState lifecycleState) {
    return switch (lifecycleState) {
      case CREATING -> UserTaskLifecycleState.CREATING;
      case CREATED -> UserTaskLifecycleState.CREATED;
      // the exporters store a claim as ASSIGNING too, so the lookup agrees with the search API
      case ASSIGNING, CLAIMING -> UserTaskLifecycleState.ASSIGNING;
      case UPDATING -> UserTaskLifecycleState.UPDATING;
      case COMPLETING -> UserTaskLifecycleState.COMPLETING;
      case CANCELING -> UserTaskLifecycleState.CANCELING;
      // no lifecycle state stored for the task: still answer with the task instead of failing
      case NOT_FOUND -> UserTaskLifecycleState.UNSPECIFIED;
    };
  }

  private void rejectCommand(
      final TypedRecord<UserTaskLookupRecord> command, final Rejection rejection) {
    rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
    responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
  }

  private record VariableSize(String name, int size) {}
}
