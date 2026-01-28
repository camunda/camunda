/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth.matcher;

import io.camunda.search.entities.UserTaskEntity;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Matches user task properties against authentication context.
 *
 * <p>Supports matching:
 *
 * <ul>
 *   <li>assignee - user is the task assignee
 *   <li>candidateUsers - user is in the task candidate users list
 *   <li>candidateGroups - any of user's groups is in the task candidate groups list
 * </ul>
 */
public class UserTaskPropertyMatcher implements ResourcePropertyMatcher<UserTaskEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(UserTaskPropertyMatcher.class);

  @Override
  public boolean matches(
      final UserTaskEntity resource,
      final Set<String> authorizedPropertyNames,
      final CamundaAuthentication authentication) {
    final var username = authentication.authenticatedUsername();
    final var userGroups = authentication.authenticatedGroupIds();

    for (final var propertyName : authorizedPropertyNames) {
      if (matchesProperty(resource, propertyName, username, userGroups)) {
        return true;
      }
    }

    return false;
  }

  private boolean matchesProperty(
      final UserTaskEntity resource,
      final String propertyName,
      final String username,
      final List<String> userGroups) {

    return switch (propertyName) {
      case Authorization.PROP_ASSIGNEE -> matchesAssignee(resource, username);
      case Authorization.PROP_CANDIDATE_USERS -> matchesCandidateUsers(resource, username);
      case Authorization.PROP_CANDIDATE_GROUPS -> matchesCandidateGroups(resource, userGroups);
      default -> {
        LOG.warn("Unknown property name '{}' for UserTaskEntity matching; ignoring.", propertyName);
        yield false;
      }
    };
  }

  private boolean matchesAssignee(final UserTaskEntity resource, final String username) {
    if (username == null || username.isEmpty()) {
      return false;
    }
    final var assignee = resource.assignee();
    return assignee != null && assignee.equals(username);
  }

  private boolean matchesCandidateUsers(final UserTaskEntity resource, final String username) {
    if (username == null || username.isEmpty()) {
      return false;
    }
    final var candidateUsers = resource.candidateUsers();
    return candidateUsers != null && candidateUsers.contains(username);
  }

  private boolean matchesCandidateGroups(
      final UserTaskEntity resource, final List<String> userGroups) {
    if (userGroups == null || userGroups.isEmpty()) {
      return false;
    }
    final var candidateGroups = resource.candidateGroups();
    if (candidateGroups == null || candidateGroups.isEmpty()) {
      return false;
    }

    return userGroups.stream().anyMatch(candidateGroups::contains);
  }

  @Override
  public Class<UserTaskEntity> getResourceClass() {
    return UserTaskEntity.class;
  }
}
