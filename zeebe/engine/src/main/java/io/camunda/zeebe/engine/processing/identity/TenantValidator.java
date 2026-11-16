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
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.util.Either;
import org.jspecify.annotations.NullMarked;

/**
 * Checks whether a tenant command can be applied to the identity data, so that the tenant
 * processors and the validation of a policy update apply the same rules. The authorization of the
 * requesting user is not part of it; the processors check it themselves.
 */
@NullMarked
public final class TenantValidator {

  private static final String TENANT_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create tenant with ID '%s', but a tenant with this ID already exists";
  private static final String UPDATE_TENANT_NOT_FOUND_ERROR_MESSAGE =
      "Expected to update tenant with id '%s', but no tenant with this id exists.";
  private static final String DELETE_TENANT_NOT_FOUND_ERROR_MESSAGE =
      "Expected to delete tenant with id '%s', but no tenant with this id exists.";
  private static final String ADD_ENTITY_TENANT_NOT_FOUND_ERROR_MESSAGE =
      "Expected to add entity to tenant with ID '%s', but no tenant with this ID exists.";
  private static final String REMOVE_ENTITY_TENANT_NOT_FOUND_ERROR_MESSAGE =
      "Expected to remove entity from tenant '%s', but no tenant with this ID exists.";
  private static final String ADD_ENTITY_ERROR_MESSAGE =
      "Expected to add %s with ID '%s' to tenant with ID '%s', but the %s %s.";
  private static final String REMOVE_ENTITY_ERROR_MESSAGE =
      "Expected to remove %s with ID '%s' from tenant with ID '%s', but the %s %s.";

  private final IdentityView view;
  private final EngineSecurityConfig securityConfig;

  public TenantValidator(final IdentityView view, final EngineSecurityConfig securityConfig) {
    this.view = view;
    this.securityConfig = securityConfig;
  }

  public Either<Rejection, TenantRecord> validateCreate(final TenantRecord record) {
    if (view.tenantExists(record.getTenantId())) {
      return reject(
          RejectionType.ALREADY_EXISTS,
          TENANT_ALREADY_EXISTS_ERROR_MESSAGE.formatted(record.getTenantId()));
    }
    return Either.right(record);
  }

  public Either<Rejection, TenantRecord> validateUpdate(final TenantRecord record) {
    return tenantExists(record, UPDATE_TENANT_NOT_FOUND_ERROR_MESSAGE);
  }

  public Either<Rejection, TenantRecord> validateDelete(final TenantRecord record) {
    return tenantExists(record, DELETE_TENANT_NOT_FOUND_ERROR_MESSAGE);
  }

  public Either<Rejection, TenantRecord> validateAddEntity(final TenantRecord record) {
    final var tenantExists = tenantExists(record, ADD_ENTITY_TENANT_NOT_FOUND_ERROR_MESSAGE);
    if (tenantExists.isLeft()) {
      return tenantExists;
    }
    if (!isEntityPresentToAdd(record)) {
      return reject(RejectionType.NOT_FOUND, addEntityError(record, "doesn't exist"));
    }
    return validateEntityNotAssigned(record);
  }

  public Either<Rejection, TenantRecord> validateRemoveEntity(final TenantRecord record) {
    final var tenantExists = tenantExists(record, REMOVE_ENTITY_TENANT_NOT_FOUND_ERROR_MESSAGE);
    if (tenantExists.isLeft()) {
      return tenantExists;
    }
    return validateEntityAssigned(record);
  }

  /** The part of {@link #validateAddEntity} that the other partitions check again. */
  public Either<Rejection, TenantRecord> validateEntityNotAssigned(final TenantRecord record) {
    if (isEntityAssigned(record)) {
      return reject(
          RejectionType.ALREADY_EXISTS,
          addEntityError(record, "is already assigned to the tenant"));
    }
    return Either.right(record);
  }

  /**
   * The part of {@link #validateRemoveEntity} that the other partitions check again: the entity
   * exists and is assigned to the tenant.
   */
  public Either<Rejection, TenantRecord> validateEntityAssigned(final TenantRecord record) {
    if (!isEntityPresentToRemove(record)) {
      return reject(RejectionType.NOT_FOUND, removeEntityError(record, "doesn't exists"));
    }
    if (!isEntityAssigned(record)) {
      return reject(
          RejectionType.NOT_FOUND, removeEntityError(record, "is not assigned to this tenant"));
    }
    return Either.right(record);
  }

  private Either<Rejection, TenantRecord> tenantExists(
      final TenantRecord record, final String notFoundMessage) {
    if (!view.tenantExists(record.getTenantId())) {
      return reject(RejectionType.NOT_FOUND, notFoundMessage.formatted(record.getTenantId()));
    }
    return Either.right(record);
  }

  private boolean isEntityPresentToAdd(final TenantRecord record) {
    final var authentication = securityConfig.getAuthentication();
    final var entityId = record.getEntityId();
    return switch (record.getEntityType()) {
      case GROUP -> !authentication.isCamundaGroupsEnabled() || view.groupExists(entityId);
      case USER -> !authentication.isCamundaUsersEnabled() || view.userExists(entityId);
      case CLIENT -> true;
      case MAPPING_RULE -> view.mappingRuleExists(entityId);
      case ROLE -> view.roleExists(entityId);
      default -> false;
    };
  }

  private boolean isEntityPresentToRemove(final TenantRecord record) {
    return record.getEntityType() != EntityType.MAPPING_RULE
        || view.mappingRuleExists(record.getEntityId());
  }

  private boolean isEntityAssigned(final TenantRecord record) {
    return view.hasRelation(
        record.getEntityType(), record.getEntityId(), RelationType.TENANT, record.getTenantId());
  }

  private static String addEntityError(final TenantRecord record, final String reason) {
    return entityError(ADD_ENTITY_ERROR_MESSAGE, record, reason);
  }

  private static String removeEntityError(final TenantRecord record, final String reason) {
    return entityError(REMOVE_ENTITY_ERROR_MESSAGE, record, reason);
  }

  private static String entityError(
      final String message, final TenantRecord record, final String reason) {
    final var entityName = record.getEntityType().name().toLowerCase();
    return message.formatted(
        entityName, record.getEntityId(), record.getTenantId(), entityName, reason);
  }

  private static Either<Rejection, TenantRecord> reject(
      final RejectionType type, final String reason) {
    return Either.left(new Rejection(type, reason));
  }
}
