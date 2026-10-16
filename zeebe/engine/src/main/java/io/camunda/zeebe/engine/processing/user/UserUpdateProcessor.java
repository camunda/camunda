/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.identity.SourceOwnership;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord.UserAttribute;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class UserUpdateProcessor implements DistributedTypedRecordProcessor<UserRecord> {

  private final UserState userState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior distributionBehavior;
  private final PermissionsBehavior permissionsBehavior;

  public UserUpdateProcessor(
      final KeyGenerator keyGenerator,
      final ProcessingState state,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final PermissionsBehavior permissionsBehavior) {
    this.keyGenerator = keyGenerator;
    userState = state.getUserState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.distributionBehavior = distributionBehavior;
    this.permissionsBehavior = permissionsBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<UserRecord> command) {
    final var record = command.getValue();
    final String username = record.getUsername();
    final var persistedUserOptional = userState.getUser(username);

    if (persistedUserOptional.isEmpty()) {
      final var rejectionMessage =
          "Expected to update user with username %s, but a user with this username does not exist"
              .formatted(username);

      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, rejectionMessage);
      responseWriter.writeRejectedResponseOnCommand(
          command, RejectionType.NOT_FOUND, rejectionMessage);
      return;
    }

    final var persistedUser = persistedUserOptional.get();

    final var authResult =
        permissionsBehavior.isAuthorized(
            command, AuthorizationResourceType.USER, PermissionType.UPDATE, username);
    if (authResult.isLeft()) {
      final var rejection = authResult.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var ownership =
        SourceOwnership.check(
            "update",
            "user",
            username,
            persistedUser.getUser().getSource(),
            record.getSource(),
            record.getChangedAttributes().contains(UserAttribute.SOURCE));
    if (ownership.isPresent()) {
      final var rejection = ownership.get();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var updatedUser = overlayUser(persistedUser.getUser(), record).clearChangedAttributes();

    stateWriter.appendFollowUpEvent(persistedUser.getUserKey(), UserIntent.UPDATED, updatedUser);
    responseWriter.writeAcceptedResponseOnCommand(
        persistedUser.getUserKey(), UserIntent.UPDATED, updatedUser, command);

    final long distributionKey = keyGenerator.nextKey();
    distributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(
            command.getValueType(), command.getIntent(), updatedUser, command.getAuthInfo());
  }

  @Override
  public void processDistributedCommand(final TypedRecord<UserRecord> command) {
    stateWriter.appendFollowUpEvent(
        command.getValue().getUserKey(), UserIntent.UPDATED, command.getValue());

    distributionBehavior.acknowledgeCommand(command);
  }

  private UserRecord overlayUser(final UserRecord persistedUser, final UserRecord updatedUser) {
    final var changedAttributes = updatedUser.getChangedAttributes();

    if (changedAttributes.isEmpty()) {
      // legacy full update: it knows nothing about custom attributes, so the stored ones are kept
      persistedUser.setName(updatedUser.getName());
      persistedUser.setEmail(updatedUser.getEmail());

      if (!updatedUser.getPassword().isEmpty()) {
        persistedUser.setPassword(updatedUser.getPassword());
      }

      return persistedUser;
    }

    for (final var attribute : changedAttributes) {
      switch (attribute) {
        case NAME -> persistedUser.setName(updatedUser.getName());
        case EMAIL -> persistedUser.setEmail(updatedUser.getEmail());
        case PASSWORD -> persistedUser.setPassword(updatedUser.getPassword());
        case EXTERNAL_ID -> persistedUser.setExternalId(updatedUser.getExternalId());
        case ACTIVE -> persistedUser.setActive(updatedUser.isActive());
        case SOURCE -> persistedUser.setSource(updatedUser.getSource());
        case ATTRIBUTES ->
            persistedUser.setAttributes(withoutNullValues(updatedUser.getAttributes()));
        case ATTRIBUTES_MERGE ->
            persistedUser.setAttributes(
                mergeAttributes(persistedUser.getAttributes(), updatedUser.getAttributes()));
      }
    }

    return persistedUser;
  }

  private static Map<String, Object> withoutNullValues(final Map<String, Object> attributes) {
    final Map<String, Object> result = new HashMap<>(attributes);
    result.values().removeIf(Objects::isNull);
    return result;
  }

  /** Top-level JSON merge patch: a null value removes the key, any other value sets it. */
  private static Map<String, Object> mergeAttributes(
      final Map<String, Object> stored, final Map<String, Object> patch) {
    final Map<String, Object> result = new HashMap<>(stored);
    patch.forEach(
        (key, value) -> {
          if (value == null) {
            result.remove(key);
          } else {
            result.put(key, value);
          }
        });
    return result;
  }
}
