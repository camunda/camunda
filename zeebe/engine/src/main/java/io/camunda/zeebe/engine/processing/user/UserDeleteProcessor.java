/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.user.PersistedUser;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class UserDeleteProcessor implements DistributedTypedRecordProcessor<UserRecord> {

  private static final String USER_DOES_NOT_EXIST_ERROR_MESSAGE =
      "Expected to delete user with key %s, but a user with this key does not exist";
  private final UserState userState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior distributionBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final TenantState tenantState;

  public UserDeleteProcessor(
      final KeyGenerator keyGenerator,
      final ProcessingState state,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.keyGenerator = keyGenerator;
    userState = state.getUserState();
    tenantState = state.getTenantState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.distributionBehavior = distributionBehavior;
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<UserRecord> command) {
    final long userKey = command.getValue().getUserKey();
    final var persistedUser = userState.getUser(userKey);

    if (persistedUser.isEmpty()) {
      final var rejectionMessage =
          USER_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(command.getValue().getUserKey());

      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, rejectionMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, rejectionMessage);
      return;
    }

    final var authRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.USER, PermissionType.DELETE)
            .addResourceId(persistedUser.get().getUsername());
    final var isAuthorized = authCheckBehavior.isAuthorized(authRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    deleteUser(persistedUser.get());
    responseWriter.writeEventOnCommand(userKey, UserIntent.DELETED, command.getValue(), command);

    final long distributionKey = keyGenerator.nextKey();
    distributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<UserRecord> command) {
    final var record = command.getValue();

    userState
        .getUser(record.getUserKey())
        .ifPresentOrElse(
            this::deleteUser,
            () -> {
              final var message = USER_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(record.getUserKey());
              rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, message);
            });

    distributionBehavior.acknowledgeCommand(command);
  }

  private void deleteUser(final PersistedUser user) {
    final var userKey = user.getUserKey();
    for (final var tenantId : user.getTenantIdsList()) {
      final long tenantKey = tenantState.getTenantKeyById(tenantId).orElseThrow();
      stateWriter.appendFollowUpEvent(
          tenantKey,
          TenantIntent.ENTITY_REMOVED,
          new TenantRecord()
              .setTenantKey(tenantKey)
              .setEntityKey(userKey)
              .setEntityType(EntityType.USER));
    }

    for (final var roleKey : user.getRoleKeysList()) {
      stateWriter.appendFollowUpEvent(
          keyGenerator.nextKey(),
          RoleIntent.ENTITY_REMOVED,
          new RoleRecord()
              .setEntityId(roleKey)
              .setEntityKey(userKey)
              .setEntityType(EntityType.USER));
    }
    for (final var groupKey : user.getGroupKeysList()) {
      stateWriter.appendFollowUpEvent(
          keyGenerator.nextKey(),
          GroupIntent.ENTITY_REMOVED,
          new GroupRecord()
              .setEntityId(groupKey)
              .setEntityKey(userKey)
              .setEntityType(EntityType.USER));
    }

    stateWriter.appendFollowUpEvent(
        userKey, UserIntent.DELETED, new UserRecord().setUserKey(userKey));
  }
}
