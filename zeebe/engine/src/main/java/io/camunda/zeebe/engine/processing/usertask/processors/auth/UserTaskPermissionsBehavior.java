/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors.auth;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.micrometer.common.util.StringUtils;
import java.util.Set;

public final class UserTaskPermissionsBehavior {

  private final KeyGenerator keyGenerator;
  private final TypedCommandWriter commandWriter;
  private final PermissionsBehavior permissionsBehavior;
  private final SecurityConfiguration securityConfig;

  public UserTaskPermissionsBehavior(
      final KeyGenerator keyGenerator,
      final TypedCommandWriter commandWriter,
      final PermissionsBehavior permissionsBehavior,
      final SecurityConfiguration securityConfig) {
    this.keyGenerator = keyGenerator;
    this.commandWriter = commandWriter;
    this.permissionsBehavior = permissionsBehavior;
    this.securityConfig = securityConfig;
  }

  /**
   * Grants permissions for the given user task. Currently, grants READ & UPDATE permissions only to
   * the assignee on USER_TASK.CREATED. If the assignee is empty/blank, no permissions will be
   * created. Later this will be extended to also add permissions for candidateUsers and
   * candidateGroups.
   */
  public void grantTaskPermissions(final UserTaskRecord userTask) {
    if (!securityConfig.getAuthorizations().isEnabled()) {
      return; // authorizations are disabled
    }

    if (!userTask.getChangedAttributes().contains(UserTaskRecord.ASSIGNEE)) {
      return; // assignee was not set or changed, so no need to add permissions
    }

    final String assignee = userTask.getAssignee();
    if (StringUtils.isBlank(assignee)) {
      return; // no owner to add permissions for
    }

    final long userTaskKey = userTask.getUserTaskKey();
    appendCreateAuthorization(AuthorizationOwnerType.USER, assignee, userTaskKey);
  }

  private void appendCreateAuthorization(
      final AuthorizationOwnerType ownerType, final String ownerId, final long resourceId) {

    final var authorizationRecord =
        new AuthorizationRecord()
            .setOwnerType(ownerType)
            .setOwnerId(ownerId)
            .setResourceType(AuthorizationResourceType.USER_TASK)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId(Long.toString(resourceId))
            .setPermissionTypes(Set.of(PermissionType.READ, PermissionType.UPDATE));

    if (permissionsBehavior.permissionsAlreadyExist(authorizationRecord).isLeft()) {
      return; // permissions already exist
    }

    final long key = keyGenerator.nextKey();
    commandWriter.appendFollowUpCommand(key, AuthorizationIntent.CREATE, authorizationRecord);
  }
}
