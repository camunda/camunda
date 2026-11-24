/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationEntityValidator;
import io.camunda.zeebe.engine.processing.identity.StateIdentityView;
import io.camunda.zeebe.engine.processing.policy.PolicyUpdateCommandMapper.IdentityCommand;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * Validates a whole policy update, all its chunks together, before any of its identity commands is
 * written, with the same rules as the identity processors, so that an invalid update changes
 * nothing. The chunks are mapped to identity commands, which are validated in apply order against
 * the state plus the commands before them, including those of earlier chunks.
 *
 * <p>Only the creates and updates of authorizations are validated so far; the other commands pass
 * unchecked and can still be rejected by their processors. The check for permissions that already
 * exist is left out, since the update maps an authorization to a create only if no authorization
 * with the same owner and resource exists, and the duplicate check covers the update itself.
 */
@NullMarked
public final class PolicyUpdateValidator {

  private final ProcessingState state;
  private final PolicyUpdateCommandMapper commandMapper;
  private final EngineSecurityConfig securityConfig;

  public PolicyUpdateValidator(
      final ProcessingState state,
      final PolicyUpdateCommandMapper commandMapper,
      final EngineSecurityConfig securityConfig) {
    this.state = state;
    this.commandMapper = commandMapper;
    this.securityConfig = securityConfig;
  }

  /**
   * Returns the reasons why the update can't be applied, or an empty list if it can.
   *
   * @param chunks all chunks of the update, in their order
   */
  public List<String> validate(final List<? extends PolicyUpdateRecordValue> chunks) {
    final var commands = new ArrayList<IdentityCommand>();
    final var unmappable = new ArrayList<String>();
    for (final var chunk : chunks) {
      commandMapper.toCommands(chunk).ifRightOrLeft(commands::addAll, unmappable::addAll);
    }
    if (!unmappable.isEmpty()) {
      return unmappable;
    }

    final var duplicates = findDuplicateUpserts(chunks);
    if (!duplicates.isEmpty()) {
      // a duplicate is mapped to two creates, so validating the commands adds nothing
      return duplicates;
    }

    final var view = new PolicyUpdateIdentityView(new StateIdentityView(state));
    final var authorizationValidator = new AuthorizationEntityValidator(view, securityConfig);
    final var errors = new ArrayList<String>();
    for (final var command : commands) {
      validate(command, authorizationValidator)
          .ifRightOrLeft(valid -> view.apply(command), rejection -> errors.add(rejection.reason()));
    }
    return errors;
  }

  private static Either<Rejection, ?> validate(
      final IdentityCommand command, final AuthorizationEntityValidator authorizationValidator) {
    if (command.value() instanceof final AuthorizationRecord authorization) {
      return switch ((AuthorizationIntent) command.intent()) {
        case CREATE -> validateUpsert(authorization, "create", authorizationValidator);
        case UPDATE -> validateUpsert(authorization, "update", authorizationValidator);
        default -> Either.right(authorization);
      };
    }
    return Either.right(command);
  }

  /** The checks of the authorization processors that don't read the stored authorizations. */
  private static Either<Rejection, AuthorizationRecord> validateUpsert(
      final AuthorizationRecord authorization,
      final String operation,
      final AuthorizationEntityValidator authorizationValidator) {
    return authorizationValidator
        .validatePermissionTypes(authorization, authorization.getResourceType(), operation)
        .flatMap(record -> authorizationValidator.validateResourceMatcher(record, operation))
        .flatMap(authorizationValidator::ownerAndResourceExists);
  }

  private static List<String> findDuplicateUpserts(
      final List<? extends PolicyUpdateRecordValue> chunks) {
    final var errors = new ArrayList<String>();
    findDuplicates(
        errors,
        "tenant",
        chunks,
        PolicyUpdateRecordValue::getTenants,
        TenantRecordValue::getTenantId,
        tenant -> "tenant with ID '%s'".formatted(tenant.getTenantId()));
    findDuplicates(
        errors,
        "role",
        chunks,
        PolicyUpdateRecordValue::getRoles,
        RoleRecordValue::getRoleId,
        role -> "role with ID '%s'".formatted(role.getRoleId()));
    findDuplicates(
        errors,
        "group",
        chunks,
        PolicyUpdateRecordValue::getGroups,
        GroupRecordValue::getGroupId,
        group -> "group with ID '%s'".formatted(group.getGroupId()));
    findDuplicates(
        errors,
        "mapping rule",
        chunks,
        PolicyUpdateRecordValue::getMappingRules,
        MappingRuleRecordValue::getMappingRuleId,
        rule -> "mapping rule with ID '%s'".formatted(rule.getMappingRuleId()));
    findDuplicates(
        errors,
        "authorization",
        chunks,
        PolicyUpdateRecordValue::getAuthorizations,
        PolicyUpdateValidator::authorizationKey,
        PolicyUpdateValidator::describeAuthorization);
    return errors;
  }

  /** The owner and resource that identify an authorization, as the update matches it. */
  private static List<?> authorizationKey(final AuthorizationRecordValue authorization) {
    return List.of(
        authorization.getOwnerType(),
        authorization.getOwnerId(),
        authorization.getResourceType(),
        authorization.getResourceMatcher(),
        authorization.getResourceId(),
        authorization.getResourcePropertyName());
  }

  private static String describeAuthorization(final AuthorizationRecordValue authorization) {
    return "authorization of %s '%s' on %s '%s'"
        .formatted(
            authorization.getOwnerType(),
            authorization.getOwnerId(),
            authorization.getResourceType(),
            authorization.getResourceMatcher() == AuthorizationResourceMatcher.PROPERTY
                ? authorization.getResourcePropertyName()
                : authorization.getResourceId());
  }

  private static <T> void findDuplicates(
      final List<String> errors,
      final String entityName,
      final List<? extends PolicyUpdateRecordValue> chunks,
      final Function<PolicyUpdateRecordValue, List<T>> section,
      final Function<T, ?> key,
      final Function<T, String> describe) {
    final var seen = new HashSet<Object>();
    final var reported = new HashSet<Object>();
    final var entities = chunks.stream().flatMap(chunk -> section.apply(chunk).stream()).toList();
    for (final var entity : entities) {
      final var entityKey = key.apply(entity);
      if (!seen.add(entityKey) && reported.add(entityKey)) {
        errors.add(
            "Expected each %s to be upserted at most once, but the %s appears more than once"
                .formatted(entityName, describe.apply(entity)));
      }
    }
  }
}
