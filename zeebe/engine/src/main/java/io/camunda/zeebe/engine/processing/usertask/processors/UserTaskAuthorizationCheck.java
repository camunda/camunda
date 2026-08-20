/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationRejection;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.auth.RequiredAuthorization.Builder;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.property.UserTaskAuthorizationProperties;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

/**
 * Centralizes the UserTask authorization decision shared by the assign, claim, complete, and update
 * processors.
 *
 * <p>UserTask authorization is an OR across several alternatives ({@code PROCESS_DEFINITION.<perm>}
 * or {@code USER_TASK.<perm>} by id or by task property). CSL's {@link RequiredAuthorization} is
 * single {@code (resourceType, permissionType)}, so it cannot express this OR in one check — the
 * engine must orchestrate the alternatives itself. This class captures that orchestration once.
 *
 * <p>Callers describe the ordered alternatives as data via {@link #processDefinition} and {@link
 * #userTask} and hand them to {@link #check}; the evaluation mechanics — principal resolution,
 * protocol-to-CSL permission mapping, the first-allow short-circuit, property-constraint reason
 * suffixes, and the combined rejection reason — all live here, not at the call sites.
 */
@NullMarked
public final class UserTaskAuthorizationCheck {

  private static final String REASON_SEPARATOR = "; and ";
  private static final String PROPERTY_CONSTRAINT_SUFFIX =
      " or resource must match property constraints '[%s]'";

  private final CslAuthorizationCheck cslCheck;

  public UserTaskAuthorizationCheck(final CslAuthorizationCheck cslCheck) {
    this.cslCheck = cslCheck;
  }

  /**
   * A {@code PROCESS_DEFINITION} alternative for {@code permissionType} on the task's definition.
   */
  public static Alt processDefinition(final PermissionType permissionType) {
    return new ProcessDefinition(permissionType);
  }

  /**
   * A {@code USER_TASK} alternative for {@code permissionType}, allowed either by resource-id grant
   * or by a property-scoped grant matching one of {@code properties}.
   */
  public static Alt userTask(final PermissionType permissionType, final TaskProperties properties) {
    return new UserTask(permissionType, properties);
  }

  /**
   * Applies the shared skip-logic and, if a principal must be checked, evaluates the ordered {@code
   * alternatives} as an OR: returns {@code right(task)} on the first allowed alternative, otherwise
   * {@code left} with a {@link RejectionType#FORBIDDEN} rejection whose reason joins every failed
   * alternative's reason with {@code "; and "}.
   */
  public Either<Rejection, UserTaskRecord> check(
      final TypedRecord<?> command, final UserTaskRecord task, final Alt... alternatives) {
    final var resolved =
        cslCheck.resolveForCheck(command, AuthorizationRejectionMapper.noPrincipal());
    if (resolved.isLeft()) {
      return Either.left(resolved.getLeft());
    }
    if (resolved.get().isEmpty()) {
      return Either.right(task);
    }
    final var auth = resolved.get().get();
    final var reasons = new ArrayList<String>();
    for (final var alternative : alternatives) {
      final var reason = evaluate(auth, task, alternative);
      if (reason.isEmpty()) {
        return Either.right(task);
      }
      reasons.add(reason.get());
    }
    return Either.left(
        new Rejection(RejectionType.FORBIDDEN, String.join(REASON_SEPARATOR, reasons)));
  }

  private Optional<String> evaluate(
      final CamundaAuthentication auth, final UserTaskRecord task, final Alt alternative) {
    return switch (alternative) {
      case ProcessDefinition pd -> evaluateProcessDefinition(auth, task, pd.permissionType());
      case UserTask ut -> evaluateUserTask(auth, task, ut.permissionType(), ut.properties());
    };
  }

  private Optional<String> evaluateProcessDefinition(
      final CamundaAuthentication auth,
      final UserTaskRecord task,
      final PermissionType permission) {
    final var result =
        authorized(
            auth,
            b ->
                b.processDefinition()
                    .permissionType(AuthzModelMapper.fromProtocol(permission))
                    .resourceId(task.getBpmnProcessId()));
    return reasonIfDenied(result, "");
  }

  private Optional<String> evaluateUserTask(
      final CamundaAuthentication auth,
      final UserTaskRecord task,
      final PermissionType permission,
      final TaskProperties properties) {
    final var cslPermission = AuthzModelMapper.fromProtocol(permission);
    final var taskKey = String.valueOf(task.getUserTaskKey());
    final var byId =
        authorized(auth, b -> b.userTask().permissionType(cslPermission).resourceId(taskKey));
    if (byId.isRight()) {
      return Optional.empty();
    }
    final var byProperty =
        cslCheck.checkAuth(
            auth,
            RequiredAuthorization.<UserTaskRecord>of(
                b -> properties.declareOn(b.userTask().permissionType(cslPermission))),
            task);
    if (byProperty.isRight()) {
      return Optional.empty();
    }
    return reasonIfDenied(byId, propertyConstraintSuffix(task, properties.propertyNames()));
  }

  private io.camunda.security.api.model.Either<AuthorizationRejection, Void> authorized(
      final CamundaAuthentication auth,
      final UnaryOperator<RequiredAuthorization.Builder<UserTaskRecord>> spec) {
    return cslCheck.checkAuth(auth, RequiredAuthorization.of(spec::apply));
  }

  private static Optional<String> reasonIfDenied(
      final io.camunda.security.api.model.Either<AuthorizationRejection, Void> result,
      final String reasonSuffix) {
    if (result.isRight()) {
      return Optional.empty();
    }
    return Optional.of(
        AuthorizationRejectionMapper.toRejection(result.leftValue()).reason() + reasonSuffix);
  }

  /**
   * Builds the {@code " or resource must match property constraints '[...]'"} suffix listing the
   * declared properties that are actually populated on {@code task} (alphabetically sorted).
   * Returns an empty string when none apply, so a task without matching properties reads no suffix.
   */
  private static String propertyConstraintSuffix(
      final UserTaskRecord task, final Set<String> declaredProperties) {
    final var populated =
        UserTaskAuthorizationProperties.builder()
            .assignee(task.getAssignee())
            .candidateUsers(task.getCandidateUsersList())
            .candidateGroups(task.getCandidateGroupsList())
            .build()
            .getPropertyNames();
    final var names =
        populated.stream()
            .filter(declaredProperties::contains)
            .sorted()
            .collect(Collectors.joining(", "));
    return names.isEmpty() ? "" : PROPERTY_CONSTRAINT_SUFFIX.formatted(names);
  }

  /** The task properties an {@code USER_TASK} property-scoped grant may match. */
  public enum TaskProperties {
    /**
     * All three properties (assignee, candidate users, candidate groups). CSL honors a granted
     * property scope only when its property is declared, so the full path must declare every
     * property or it would silently under-authorize the omitted candidates.
     */
    ALL(
        b -> b.authorizedByAssignee().authorizedByCandidateUsers().authorizedByCandidateGroups(),
        Set.of(
            RequiredAuthorization.PROP_ASSIGNEE,
            RequiredAuthorization.PROP_CANDIDATE_USERS,
            RequiredAuthorization.PROP_CANDIDATE_GROUPS)),
    /** Only the {@code assignee} property; used by the self-unassign path. */
    ASSIGNEE_ONLY(Builder::authorizedByAssignee, Set.of(RequiredAuthorization.PROP_ASSIGNEE));

    private final UnaryOperator<Builder<UserTaskRecord>> declaration;
    private final Set<String> propertyNames;

    TaskProperties(
        final UnaryOperator<Builder<UserTaskRecord>> declaration, final Set<String> propertyNames) {
      this.declaration = declaration;
      this.propertyNames = propertyNames;
    }

    Builder<UserTaskRecord> declareOn(final Builder<UserTaskRecord> builder) {
      return declaration.apply(builder);
    }

    Set<String> propertyNames() {
      return propertyNames;
    }
  }

  /** A single authorization alternative, evaluated as one branch of the OR. */
  public sealed interface Alt permits ProcessDefinition, UserTask {}

  private record ProcessDefinition(PermissionType permissionType) implements Alt {}

  private record UserTask(PermissionType permissionType, TaskProperties properties)
      implements Alt {}
}
