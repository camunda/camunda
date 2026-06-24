/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.property;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Type-safe container for user task authorization properties.
 *
 * <p>These properties are evaluated at runtime to determine if a principal has access to a specific
 * user task based on assignment or candidacy.
 *
 * @param assignee the user assigned to this task (may be null or empty)
 * @param candidateUsers list of users who are candidates for this task (may be null or empty)
 * @param candidateGroups list of groups whose members are candidates for this task (may be null or
 *     empty)
 */
public record UserTaskAuthorizationProperties(
    String assignee, List<String> candidateUsers, List<String> candidateGroups)
    implements ResourceAuthorizationProperties {

  public static final String PROP_ASSIGNEE = "assignee";
  public static final String PROP_CANDIDATE_USERS = "candidateUsers";
  public static final String PROP_CANDIDATE_GROUPS = "candidateGroups";

  public UserTaskAuthorizationProperties {
    candidateUsers = candidateUsers != null ? List.copyOf(candidateUsers) : null;
    candidateGroups = candidateGroups != null ? List.copyOf(candidateGroups) : null;
  }

  @Override
  public boolean hasProperties() {
    return hasAssignee() || hasCandidateUsers() || hasCandidateGroups();
  }

  @Override
  public Set<String> getPropertyNames() {
    final Set<String> names = new HashSet<>();
    if (hasAssignee()) {
      names.add(PROP_ASSIGNEE);
    }
    if (hasCandidateUsers()) {
      names.add(PROP_CANDIDATE_USERS);
    }
    if (hasCandidateGroups()) {
      names.add(PROP_CANDIDATE_GROUPS);
    }
    return names;
  }

  public boolean hasAssignee() {
    return assignee != null && !assignee.isEmpty();
  }

  public boolean hasCandidateUsers() {
    return candidateUsers != null && !candidateUsers.isEmpty();
  }

  public boolean hasCandidateGroups() {
    return candidateGroups != null && !candidateGroups.isEmpty();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String assignee;
    private List<String> candidateUsers;
    private List<String> candidateGroups;

    private Builder() {}

    public Builder assignee(final String assignee) {
      this.assignee = assignee;
      return this;
    }

    public Builder candidateUsers(final List<String> candidateUsers) {
      this.candidateUsers = candidateUsers;
      return this;
    }

    public Builder candidateGroups(final List<String> candidateGroups) {
      this.candidateGroups = candidateGroups;
      return this;
    }

    public UserTaskAuthorizationProperties build() {
      return new UserTaskAuthorizationProperties(assignee, candidateUsers, candidateGroups);
    }
  }
}
