/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.property;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UserTaskPropertyAuthorizationEvaluator
    implements PropertyAuthorizationEvaluator {

  public static final String PROP_ASSIGNEE = "assignee";
  public static final String PROP_CANDIDATE_USERS = "candidateUsers";
  public static final String PROP_CANDIDATE_GROUPS = "candidateGroups";

  @Override
  public AuthorizationResourceType resourceType() {
    return AuthorizationResourceType.USER_TASK;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Set<String> matches(
      final Map<String, Object> claims, final Map<String, Object> resourceProperties) {
    if (resourceProperties == null || resourceProperties.isEmpty()) {
      return Collections.emptySet();
    }
    final var userId = (String) claims.get(Authorization.AUTHORIZED_USERNAME);
    final var groups =
        (List<String>) claims.getOrDefault(Authorization.USER_GROUPS_CLAIMS, List.of());

    final Set<String> matched = new HashSet<>();

    final Object assigneeObj = resourceProperties.get(PROP_ASSIGNEE);
    if (assigneeObj instanceof String assignee && userId != null && userId.equals(assignee)) {
      matched.add(PROP_ASSIGNEE);
    }

    final Object candUsersObj = resourceProperties.get(PROP_CANDIDATE_USERS);
    if (candUsersObj instanceof List<?> userList
        && userId != null
        && userList.stream().anyMatch(userId::equals)) {
      matched.add(PROP_CANDIDATE_USERS);
    }

    final Object candGroupsObj = resourceProperties.get(PROP_CANDIDATE_GROUPS);
    if (candGroupsObj instanceof List<?> groupList && !groups.isEmpty()) {
      if (groupList.stream().anyMatch(groups::contains)) {
        matched.add(PROP_CANDIDATE_GROUPS);
      }
    }

    return matched;
  }
}
