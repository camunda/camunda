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
import io.camunda.zeebe.engine.processing.identity.RoleValidator;
import io.camunda.zeebe.engine.processing.identity.StateIdentityView;
import io.camunda.zeebe.engine.processing.policy.PolicyUpdateCommandMapper.IdentityCommand;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
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
 * Validates all identity commands of a policy update before any of them is written, with the same
 * rules as the identity processors, so that an invalid update changes nothing. The commands are
 * validated in apply order against the state plus the commands before them.
 *
 * <p>Only role commands are validated so far; the commands of the other entities pass unchecked and
 * can still be rejected by their processors.
 */
@NullMarked
public final class PolicyUpdateValidator {

  private final ProcessingState state;
  private final EngineSecurityConfig securityConfig;

  public PolicyUpdateValidator(
      final ProcessingState state, final EngineSecurityConfig securityConfig) {
    this.state = state;
    this.securityConfig = securityConfig;
  }

  /** Returns the reasons why the update can't be applied, or an empty list if it can. */
  public List<String> validate(
      final PolicyUpdateRecordValue chunk, final List<IdentityCommand> commands) {
    final var duplicates = findDuplicateUpserts(chunk);
    if (!duplicates.isEmpty()) {
      // a duplicate is mapped to two creates, so validating the commands adds nothing
      return duplicates;
    }

    final var view = new PolicyUpdateIdentityView(new StateIdentityView(state));
    final var roleValidator = new RoleValidator(view, securityConfig);
    final var errors = new ArrayList<String>();
    for (final var command : commands) {
      validate(command, roleValidator)
          .ifRightOrLeft(valid -> view.apply(command), rejection -> errors.add(rejection.reason()));
    }
    return errors;
  }

  private static Either<Rejection, ?> validate(
      final IdentityCommand command, final RoleValidator roleValidator) {
    if (command.value() instanceof final RoleRecord role) {
      return switch ((RoleIntent) command.intent()) {
        case CREATE -> roleValidator.validateCreate(role);
        case UPDATE -> roleValidator.validateUpdate(role);
        case DELETE -> roleValidator.validateDelete(role);
        case ADD_ENTITY -> roleValidator.validateAddEntity(role);
        case REMOVE_ENTITY -> roleValidator.validateRemoveEntity(role);
        default -> Either.right(role);
      };
    }
    return Either.right(command);
  }

  private static List<String> findDuplicateUpserts(final PolicyUpdateRecordValue chunk) {
    final var errors = new ArrayList<String>();
    findDuplicates(errors, "tenant", chunk.getTenants(), TenantRecordValue::getTenantId);
    findDuplicates(errors, "role", chunk.getRoles(), RoleRecordValue::getRoleId);
    findDuplicates(errors, "group", chunk.getGroups(), GroupRecordValue::getGroupId);
    findDuplicates(
        errors, "mapping rule", chunk.getMappingRules(), MappingRuleRecordValue::getMappingRuleId);
    return errors;
  }

  private static <T> void findDuplicates(
      final List<String> errors,
      final String entityName,
      final List<T> entities,
      final Function<T, String> id) {
    final var seen = new HashSet<String>();
    final var reported = new HashSet<String>();
    for (final var entity : entities) {
      final var entityId = id.apply(entity);
      if (!seen.add(entityId) && reported.add(entityId)) {
        errors.add(
            "Expected each %s to be upserted at most once, but %s with ID '%s' appears more than once"
                .formatted(entityName, entityName, entityId));
      }
    }
  }
}
