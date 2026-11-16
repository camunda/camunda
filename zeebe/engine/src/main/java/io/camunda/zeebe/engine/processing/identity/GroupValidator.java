/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;
import org.jspecify.annotations.NullMarked;

/**
 * Checks whether a group command can be applied to the identity data, so that the group processors
 * and the validation of a policy update apply the same rules. The authorization of the requesting
 * user is not part of it; the processors check it before.
 */
@NullMarked
public final class GroupValidator {

  private static final String GROUP_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create group with ID '%s', but a group with this ID already exists.";
  private static final String UPDATE_GROUP_NOT_FOUND_ERROR_MESSAGE =
      "Expected to update group with ID '%s', but a group with this ID does not exist.";
  private static final String DELETE_GROUP_NOT_FOUND_ERROR_MESSAGE =
      "Expected to delete group with ID '%s', but a group with this ID does not exist.";
  private static final String ADD_ENTITY_NOT_FOUND_ERROR_MESSAGE =
      "Expected to add an entity with ID '%s' and type '%s' to group with ID '%s', but the entity does not exist.";
  private static final String ENTITY_ALREADY_ASSIGNED_ERROR_MESSAGE =
      "Expected to add entity with ID '%s' to group with ID '%s', but the entity is already assigned to this group.";
  private static final String REMOVE_ENTITY_NOT_FOUND_ERROR_MESSAGE =
      "Expected to remove an entity with ID '%s' and type '%s' from group with ID '%s', but the entity does not exist.";
  private static final String ENTITY_NOT_ASSIGNED_ERROR_MESSAGE =
      "Expected to remove entity with ID '%s' from group with ID '%s', but the entity is not assigned to this group.";

  private final IdentityView view;
  private final EngineSecurityConfig securityConfig;

  public GroupValidator(final IdentityView view, final EngineSecurityConfig securityConfig) {
    this.view = view;
    this.securityConfig = securityConfig;
  }

  public Either<Rejection, GroupRecord> validateCreate(final GroupRecord record) {
    if (view.groupExists(record.getGroupId())) {
      return reject(
          RejectionType.ALREADY_EXISTS, GROUP_ALREADY_EXISTS_ERROR_MESSAGE, record.getGroupId());
    }
    return Either.right(record);
  }

  public Either<Rejection, GroupRecord> validateUpdate(final GroupRecord record) {
    return groupExists(record, UPDATE_GROUP_NOT_FOUND_ERROR_MESSAGE);
  }

  public Either<Rejection, GroupRecord> validateDelete(final GroupRecord record) {
    return groupExists(record, DELETE_GROUP_NOT_FOUND_ERROR_MESSAGE);
  }

  public Either<Rejection, GroupRecord> validateAddEntity(final GroupRecord record) {
    final var groupExists = groupExists(record, UPDATE_GROUP_NOT_FOUND_ERROR_MESSAGE);
    if (groupExists.isLeft()) {
      return groupExists;
    }
    if (!isEntityPresentToAdd(record)) {
      return reject(
          RejectionType.NOT_FOUND,
          ADD_ENTITY_NOT_FOUND_ERROR_MESSAGE,
          record.getEntityId(),
          record.getEntityType(),
          record.getGroupId());
    }
    return validateEntityNotAssigned(record);
  }

  public Either<Rejection, GroupRecord> validateRemoveEntity(final GroupRecord record) {
    final var groupExists = groupExists(record, UPDATE_GROUP_NOT_FOUND_ERROR_MESSAGE);
    if (groupExists.isLeft()) {
      return groupExists;
    }
    if (!isEntityPresentToRemove(record)) {
      return reject(
          RejectionType.NOT_FOUND,
          REMOVE_ENTITY_NOT_FOUND_ERROR_MESSAGE,
          record.getEntityId(),
          record.getEntityType(),
          record.getGroupId());
    }
    return validateEntityAssigned(record);
  }

  /** The part of {@link #validateAddEntity} that the other partitions check again. */
  public Either<Rejection, GroupRecord> validateEntityNotAssigned(final GroupRecord record) {
    if (isEntityAssigned(record)) {
      return reject(
          RejectionType.ALREADY_EXISTS,
          ENTITY_ALREADY_ASSIGNED_ERROR_MESSAGE,
          record.getEntityId(),
          record.getGroupId());
    }
    return Either.right(record);
  }

  /** The part of {@link #validateRemoveEntity} that the other partitions check again. */
  public Either<Rejection, GroupRecord> validateEntityAssigned(final GroupRecord record) {
    if (!isEntityAssigned(record)) {
      return reject(
          RejectionType.NOT_FOUND,
          ENTITY_NOT_ASSIGNED_ERROR_MESSAGE,
          record.getEntityId(),
          record.getGroupId());
    }
    return Either.right(record);
  }

  private Either<Rejection, GroupRecord> groupExists(
      final GroupRecord record, final String notFoundMessage) {
    if (!view.groupExists(record.getGroupId())) {
      return reject(RejectionType.NOT_FOUND, notFoundMessage, record.getGroupId());
    }
    return Either.right(record);
  }

  private boolean isEntityPresentToAdd(final GroupRecord record) {
    final var entityId = record.getEntityId();
    return switch (record.getEntityType()) {
      case USER ->
          !securityConfig.getAuthentication().isCamundaUsersEnabled() || view.userExists(entityId);
      case CLIENT -> true;
      case MAPPING_RULE -> view.mappingRuleExists(entityId);
      default -> false;
    };
  }

  private boolean isEntityPresentToRemove(final GroupRecord record) {
    return switch (record.getEntityType()) {
      // With simple mapping rules, any username or client ID can be assigned
      case USER, CLIENT -> true;
      case MAPPING_RULE -> view.mappingRuleExists(record.getEntityId());
      default -> false;
    };
  }

  private boolean isEntityAssigned(final GroupRecord record) {
    return view.hasRelation(
        record.getEntityType(), record.getEntityId(), RelationType.GROUP, record.getGroupId());
  }

  private static Either<Rejection, GroupRecord> reject(
      final RejectionType type, final String message, final Object... args) {
    return Either.left(new Rejection(type, message.formatted(args)));
  }
}
