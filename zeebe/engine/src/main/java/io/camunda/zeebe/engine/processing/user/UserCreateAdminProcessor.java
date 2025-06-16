/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;

public class UserCreateAdminProcessor implements TypedRecordProcessor<UserRecord> {
  private static final String USER_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create user with username '%s', but a user with this username already exists";
  private static final String ADMIN_ROLE_NOT_FOUND_ERROR_MESSAGE =
      "Expected to create admin user, but role with id '%s' does not exist";

  private final KeyGenerator keyGenerator;
  private final UserState userState;
  private final RoleState roleState;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public UserCreateAdminProcessor(
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.keyGenerator = keyGenerator;
    userState = processingState.getUserState();
    roleState = processingState.getRoleState();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<UserRecord> command) {
    final var record = command.getValue();
    final var adminRoleId = DefaultRole.ADMIN.getId();

    checkUserCreateAuthorization(command)
        .flatMap(ignored -> checkRoleUpdateAuthorization(command))
        .flatMap(ignored -> checkUserDoesNotExist(record.getUsername()))
        .flatMap(ignored -> checkAdminRoleExists(adminRoleId))
        .ifRightOrLeft(
            ignored -> {
              final var key = keyGenerator.nextKey();
              commandWriter.appendFollowUpCommand(key, UserIntent.CREATE, record);
              commandWriter.appendFollowUpCommand(
                  key,
                  RoleIntent.ADD_ENTITY,
                  new RoleRecord()
                      .setRoleId(adminRoleId)
                      .setEntityId(record.getUsername())
                      .setEntityType(EntityType.USER));
            },
            rejection -> {
              rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
              responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
            });
  }

  private Either<Rejection, Void> checkUserCreateAuthorization(
      final TypedRecord<UserRecord> command) {
    final var authRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.USER, PermissionType.CREATE);
    return authCheckBehavior.isAuthorized(authRequest);
  }

  private Either<Rejection, Void> checkRoleUpdateAuthorization(
      final TypedRecord<UserRecord> command) {
    final var authRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.ROLE, PermissionType.UPDATE);
    return authCheckBehavior.isAuthorized(authRequest);
  }

  private Either<Rejection, Void> checkUserDoesNotExist(final String username) {
    return userState
        .getUser(username)
        .map(
            user -> {
              final var message = USER_ALREADY_EXISTS_ERROR_MESSAGE.formatted(user.getUsername());
              return Either.<Rejection, Void>left(
                  new Rejection(RejectionType.ALREADY_EXISTS, message));
            })
        .orElseGet(() -> Either.right(null));
  }

  private Either<Rejection, Void> checkAdminRoleExists(final String adminRoleId) {
    return roleState
        .getRole(adminRoleId)
        .map(
            adminRole -> {
              return Either.<Rejection, Void>right(null);
            })
        .orElseGet(
            () -> {
              final var message = ADMIN_ROLE_NOT_FOUND_ERROR_MESSAGE.formatted(adminRoleId);
              return Either.left(new Rejection(RejectionType.NOT_FOUND, message));
            });
  }
}
