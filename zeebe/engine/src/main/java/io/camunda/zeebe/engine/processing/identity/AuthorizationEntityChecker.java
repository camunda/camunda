/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

public class AuthorizationEntityChecker {
  public static final String MAPPING_RULE_DOES_NOT_EXIST_ERROR_MESSAGE =
      "Expected to create or update authorization with ownerId or resourceId '%s', but a mapping rule with this ID does not exist.";
  public static final String ROLE_DOES_NOT_EXIST_ERROR_MESSAGE =
      "Expected to create or update authorization with ownerId or resourceId '%s', but a role with this ID does not exist.";
  public static final String USER_DOES_NOT_EXIST_ERROR_MESSAGE =
      "Expected to create or update authorization with ownerId or resourceId '%s', but a user with this ID does not exist.";
  public static final String GROUP_DOES_NOT_EXIST_ERROR_MESSAGE =
      "Expected to create or update authorization with ownerId or resourceId '%s', but a group with this ID does not exist.";
  public static final String IS_CAMUNDA_USERS_ENABLED = "is_camunda_users_enabled";
  public static final String IS_CAMUNDA_GROUPS_ENABLED = "is_camunda_groups_enabled";

  private final UserState userState;
  private final MappingRuleState mappingRuleState;
  private final GroupState groupState;
  private final RoleState roleState;

  public AuthorizationEntityChecker(final ProcessingState processingState) {
    userState = processingState.getUserState();
    mappingRuleState = processingState.getMappingRuleState();
    groupState = processingState.getGroupState();
    roleState = processingState.getRoleState();
  }

  public Either<Rejection, AuthorizationRecord> ownerAndResourceExists(
      final TypedRecord<AuthorizationRecord> command) {
    final var record = command.getValue();
    final boolean localUserEnabled =
        (boolean) command.getAuthorizations().getOrDefault(IS_CAMUNDA_USERS_ENABLED, false);
    final boolean localGroupEnabled =
        (boolean) command.getAuthorizations().getOrDefault(IS_CAMUNDA_GROUPS_ENABLED, false);
    switch (record.getOwnerType()) {
      case GROUP:
        if (localGroupEnabled && groupState.get(record.getOwnerId()).isEmpty()) {
          return Either.left(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  GROUP_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(record.getOwnerId())));
        }
        break;
      case MAPPING_RULE:
        if (mappingRuleState.get(record.getOwnerId()).isEmpty()) {
          return Either.left(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  MAPPING_RULE_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(record.getOwnerId())));
        }
        break;
      case ROLE:
        if (roleState.getRole(record.getOwnerId()).isEmpty()) {
          return Either.left(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  ROLE_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(record.getOwnerId())));
        }
        break;
      case USER:
        if (localUserEnabled && userState.getUser(record.getOwnerId()).isEmpty()) {
          return Either.left(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  USER_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(record.getOwnerId())));
        }
        break;
      default:
        break;
    }
    if (!record.getResourceId().equals("*")) {
      switch (record.getResourceType()) {
        case GROUP:
          if (localGroupEnabled && groupState.get(record.getResourceId()).isEmpty()) {
            return Either.left(
                new Rejection(
                    RejectionType.NOT_FOUND,
                    GROUP_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(record.getResourceId())));
          }
          break;
        case MAPPING_RULE:
          if (mappingRuleState.get(record.getResourceId()).isEmpty()) {
            return Either.left(
                new Rejection(
                    RejectionType.NOT_FOUND,
                    MAPPING_RULE_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(record.getResourceId())));
          }
          break;
        case ROLE:
          if (roleState.getRole(record.getResourceId()).isEmpty()) {
            return Either.left(
                new Rejection(
                    RejectionType.NOT_FOUND,
                    ROLE_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(record.getResourceId())));
          }
          break;
        case USER:
          if (localUserEnabled && userState.getUser(record.getResourceId()).isEmpty()) {
            return Either.left(
                new Rejection(
                    RejectionType.NOT_FOUND,
                    USER_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(record.getResourceId())));
          }
          break;
        default:
          break;
      }
    }
    return Either.right(record);
  }
}
