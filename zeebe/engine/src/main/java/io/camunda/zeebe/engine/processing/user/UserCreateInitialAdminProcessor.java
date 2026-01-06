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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class UserCreateInitialAdminProcessor implements TypedRecordProcessor<UserRecord> {
  public static final String USER_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create user with username '%s', but a user with this username already exists";
  public static final String ADMIN_ROLE_NOT_FOUND_ERROR_MESSAGE =
      "Expected to create admin user, but role with id '%s' does not exist";
  public static final String ADMIN_ROLE_HAS_USERS_ERROR_MESSAGE =
      "Expected to create admin user, but role with id '%s' already has one or more user assigned to it";

  private final KeyGenerator keyGenerator;
  private final UserState userState;
  private final RoleState roleState;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final MembershipState membershipState;
  private final StateWriter stateWriter;

  public UserCreateInitialAdminProcessor(
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.keyGenerator = keyGenerator;
    userState = processingState.getUserState();
    roleState = processingState.getRoleState();
    membershipState = processingState.getMembershipState();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    stateWriter = writers.state();
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
        .flatMap(ignored -> checkAdminRoleHasNoUsers(adminRoleId))
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
              stateWriter.appendFollowUpEvent(key, UserIntent.INITIAL_ADMIN_CREATED, record);
              responseWriter.writeEventOnCommand(
                  key, UserIntent.INITIAL_ADMIN_CREATED, record, command);
            },
            message -> {
              // For this command we always want to reject with FORBIDDEN
              rejectionWriter.appendRejection(command, RejectionType.FORBIDDEN, message);
              responseWriter.writeRejectionOnCommand(command, RejectionType.FORBIDDEN, message);
            });
  }

  private Either<String, Void> checkUserCreateAuthorization(final TypedRecord<UserRecord> command) {
    final var authRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.USER, PermissionType.CREATE);
    return authCheckBehavior.isAuthorizedOrInternalCommand(authRequest).mapLeft(Rejection::reason);
  }

  private Either<String, Void> checkRoleUpdateAuthorization(final TypedRecord<UserRecord> command) {
    final var authRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.ROLE, PermissionType.UPDATE);
    return authCheckBehavior.isAuthorizedOrInternalCommand(authRequest).mapLeft(Rejection::reason);
  }

  private Either<String, Void> checkUserDoesNotExist(final String username) {
    return userState
        .getUser(username)
        .map(
            user -> {
              final var message = USER_ALREADY_EXISTS_ERROR_MESSAGE.formatted(user.getUsername());
              return Either.<String, Void>left(message);
            })
        .orElseGet(() -> Either.right(null));
  }

  private Either<String, Void> checkAdminRoleExists(final String adminRoleId) {
    return roleState
        .getRole(adminRoleId)
        .map(adminRole -> Either.<String, Void>right(null))
        .orElseGet(
            () -> {
              final var message = ADMIN_ROLE_NOT_FOUND_ERROR_MESSAGE.formatted(adminRoleId);
              return Either.left(message);
            });
  }

  private Either<String, Void> checkAdminRoleHasNoUsers(final String adminRoleId) {
    final AtomicBoolean hasUsers = new AtomicBoolean(false);
    membershipState.forEachMember(
        RelationType.ROLE,
        adminRoleId,
        (entityType, entityId) -> {
          if (entityType != EntityType.USER) {
            return true; // Continue iteration for non-user entities
          }

          hasUsers.set(true);
          return false; // Stop iteration if any member is found
        });

    if (hasUsers.get()) {
      final var message = ADMIN_ROLE_HAS_USERS_ERROR_MESSAGE.formatted(adminRoleId);
      return Either.left(message);
    } else {
      return Either.right(null);
    }
  }
}
