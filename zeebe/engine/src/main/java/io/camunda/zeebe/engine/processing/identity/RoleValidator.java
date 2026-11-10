/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.security.api.model.authz.DefaultRole;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;
import org.jspecify.annotations.NullMarked;

/**
 * Checks whether a role command can be applied to the identity data, so that the role processors
 * and the validation of a policy update apply the same rules. The authorization of the requesting
 * user is not part of it; the processors check it before.
 */
@NullMarked
public final class RoleValidator {

  private static final String ROLE_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create role with ID '%s', but a role with this ID already exists";
  private static final String UPDATE_ROLE_NOT_FOUND_ERROR_MESSAGE =
      "Expected to update role with ID '%s', but a role with this ID does not exist.";
  private static final String UPDATE_ROLE_PROTECTED_ERROR_MESSAGE =
      "Expected to update role with ID '%s', which is a default role and cannot be modified.";
  private static final String DELETE_ROLE_NOT_FOUND_ERROR_MESSAGE =
      "Expected to delete role with ID '%s', but a role with this ID doesn't exist.";
  private static final String DELETE_ROLE_PROTECTED_ERROR_MESSAGE =
      "Expected to delete role with ID '%s', which is a default role and cannot be deleted.";
  private static final String ADD_ENTITY_NOT_FOUND_ERROR_MESSAGE =
      "Expected to add an entity with ID '%s' and type '%s' to role with ID '%s', but the entity doesn't exist.";
  private static final String ENTITY_ALREADY_ASSIGNED_ERROR_MESSAGE =
      "Expected to add entity with ID '%s' to role with ID '%s', but the entity is already assigned to this role.";
  private static final String REMOVE_ENTITY_NOT_FOUND_ERROR_MESSAGE =
      "Expected to remove an entity with ID '%s' and type '%s' from role with ID '%s', but the entity doesn't exist.";
  private static final String ENTITY_NOT_ASSIGNED_ERROR_MESSAGE =
      "Expected to remove entity with ID '%s' from role with ID '%s', but the entity is not assigned to this role.";

  private final IdentityView view;
  private final EngineSecurityConfig securityConfig;

  public RoleValidator(final IdentityView view, final EngineSecurityConfig securityConfig) {
    this.view = view;
    this.securityConfig = securityConfig;
  }

  public Either<Rejection, RoleRecord> validateCreate(final RoleRecord record) {
    if (view.roleExists(record.getRoleId())) {
      return reject(
          RejectionType.ALREADY_EXISTS, ROLE_ALREADY_EXISTS_ERROR_MESSAGE, record.getRoleId());
    }
    return Either.right(record);
  }

  public Either<Rejection, RoleRecord> validateUpdate(final RoleRecord record) {
    final var roleId = record.getRoleId();
    if (isDefaultRole(roleId)) {
      return reject(RejectionType.INVALID_STATE, UPDATE_ROLE_PROTECTED_ERROR_MESSAGE, roleId);
    }
    return roleExists(record);
  }

  public Either<Rejection, RoleRecord> validateDelete(final RoleRecord record) {
    final var roleId = record.getRoleId();
    if (isDefaultRole(roleId)) {
      return reject(RejectionType.INVALID_STATE, DELETE_ROLE_PROTECTED_ERROR_MESSAGE, roleId);
    }
    if (!view.roleExists(roleId)) {
      return reject(RejectionType.NOT_FOUND, DELETE_ROLE_NOT_FOUND_ERROR_MESSAGE, roleId);
    }
    return Either.right(record);
  }

  public Either<Rejection, RoleRecord> validateAddEntity(final RoleRecord record) {
    final var roleExists = roleExists(record);
    if (roleExists.isLeft()) {
      return roleExists;
    }
    if (!isEntityPresentToAdd(record)) {
      return reject(
          RejectionType.NOT_FOUND,
          ADD_ENTITY_NOT_FOUND_ERROR_MESSAGE,
          record.getEntityId(),
          record.getEntityType(),
          record.getRoleId());
    }
    return validateEntityNotAssigned(record);
  }

  public Either<Rejection, RoleRecord> validateRemoveEntity(final RoleRecord record) {
    final var roleExists = roleExists(record);
    if (roleExists.isLeft()) {
      return roleExists;
    }
    if (!isEntityPresentToRemove(record)) {
      return reject(
          RejectionType.NOT_FOUND,
          REMOVE_ENTITY_NOT_FOUND_ERROR_MESSAGE,
          record.getEntityId(),
          record.getEntityType(),
          record.getRoleId());
    }
    return validateEntityAssigned(record);
  }

  /** The part of {@link #validateAddEntity} that the other partitions check again. */
  public Either<Rejection, RoleRecord> validateEntityNotAssigned(final RoleRecord record) {
    if (isEntityAssigned(record)) {
      return reject(
          RejectionType.ALREADY_EXISTS,
          ENTITY_ALREADY_ASSIGNED_ERROR_MESSAGE,
          record.getEntityId(),
          record.getRoleId());
    }
    return Either.right(record);
  }

  /** The part of {@link #validateRemoveEntity} that the other partitions check again. */
  public Either<Rejection, RoleRecord> validateEntityAssigned(final RoleRecord record) {
    if (!isEntityAssigned(record)) {
      return reject(
          RejectionType.NOT_FOUND,
          ENTITY_NOT_ASSIGNED_ERROR_MESSAGE,
          record.getEntityId(),
          record.getRoleId());
    }
    return Either.right(record);
  }

  private Either<Rejection, RoleRecord> roleExists(final RoleRecord record) {
    if (!view.roleExists(record.getRoleId())) {
      return reject(
          RejectionType.NOT_FOUND, UPDATE_ROLE_NOT_FOUND_ERROR_MESSAGE, record.getRoleId());
    }
    return Either.right(record);
  }

  // TODO: in hub mode, Hub owns all roles and the default roles don't exist, so they must not be
  //  protected there. Until the engine knows whether it runs in hub mode, a policy update can't
  //  update or delete a role with the ID of a default role.
  private static boolean isDefaultRole(final String roleId) {
    return DefaultRole.ids().contains(roleId);
  }

  private boolean isEntityPresentToAdd(final RoleRecord record) {
    final var authentication = securityConfig.getAuthentication();
    final var entityId = record.getEntityId();
    return switch (record.getEntityType()) {
      case GROUP -> !authentication.isCamundaGroupsEnabled() || view.groupExists(entityId);
      case USER -> !authentication.isCamundaUsersEnabled() || view.userExists(entityId);
      case CLIENT -> true;
      case MAPPING_RULE -> view.mappingRuleExists(entityId);
      default -> false;
    };
  }

  private boolean isEntityPresentToRemove(final RoleRecord record) {
    return switch (record.getEntityType()) {
      // With simple mapping rules, any username, client ID or group can be assigned
      case USER, CLIENT, GROUP -> true;
      case MAPPING_RULE -> view.mappingRuleExists(record.getEntityId());
      default -> false;
    };
  }

  private boolean isEntityAssigned(final RoleRecord record) {
    return view.hasRelation(
        record.getEntityType(), record.getEntityId(), RelationType.ROLE, record.getRoleId());
  }

  private static Either<Rejection, RoleRecord> reject(
      final RejectionType type, final String message, final Object... args) {
    return Either.left(new Rejection(type, message.formatted(args)));
  }
}
