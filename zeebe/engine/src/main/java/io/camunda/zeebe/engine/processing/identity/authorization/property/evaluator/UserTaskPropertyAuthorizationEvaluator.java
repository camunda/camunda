/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.property.evaluator;

import io.camunda.security.auth.MappingRuleMatcher;
import io.camunda.zeebe.engine.processing.identity.authorization.resolver.ClaimsExtractor;
import io.camunda.zeebe.engine.state.authorization.PersistedMappingRule;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class UserTaskPropertyAuthorizationEvaluator
    implements PropertyAuthorizationEvaluator {

  public static final String PROP_ASSIGNEE = "assignee";
  public static final String PROP_CANDIDATE_USERS = "candidateUsers";
  public static final String PROP_CANDIDATE_GROUPS = "candidateGroups";

  private final ClaimsExtractor claimsExtractor;
  private final MappingRuleState mappingRuleState;

  public UserTaskPropertyAuthorizationEvaluator(
      final ClaimsExtractor claimsExtractor, final MappingRuleState mappingRuleState) {
    this.claimsExtractor = claimsExtractor;
    this.mappingRuleState = mappingRuleState;
  }

  @Override
  public AuthorizationResourceType resourceType() {
    return AuthorizationResourceType.USER_TASK;
  }

  @Override
  public Set<String> matches(
      final Map<String, Object> claims, final Map<String, Object> resourceProperties) {
    if (resourceProperties == null || resourceProperties.isEmpty()) {
      return Collections.emptySet();
    }

    final Set<String> matched = new HashSet<>();

    // only users can be assigned to user tasks or be part of candidateUsers
    claimsExtractor
        .getUsername(claims)
        .ifPresent(
            username -> {
              if (matchesAssignee(username, resourceProperties.get(PROP_ASSIGNEE))) {
                matched.add(PROP_ASSIGNEE);
              }

              if (matchesCandidateUsers(username, resourceProperties.get(PROP_CANDIDATE_USERS))) {
                matched.add(PROP_CANDIDATE_USERS);
              }
            });

    if (resourceProperties.containsKey(PROP_CANDIDATE_GROUPS)) {
      final var allGroups = collectAllGroups(claims);
      if (matchesCandidateGroups(allGroups, resourceProperties.get(PROP_CANDIDATE_GROUPS))) {
        matched.add(PROP_CANDIDATE_GROUPS);
      }
    }

    return matched;
  }

  /**
   * Collects all groups the principal belongs to from all possible sources:
   *
   * <ul>
   *   <li>Groups from client or user membership
   *   <li>Groups from matching mapping rules
   * </ul>
   */
  private Set<String> collectAllGroups(final Map<String, Object> claims) {

    final Set<String> allGroups = new HashSet<>();

    claimsExtractor
        .getUsername(claims)
        .ifPresentOrElse(
            username ->
                allGroups.addAll(claimsExtractor.getGroups(claims, EntityType.USER, username)),
            () ->
                claimsExtractor
                    .getClientId(claims)
                    .ifPresent(
                        clientId ->
                            allGroups.addAll(
                                claimsExtractor.getGroups(claims, EntityType.CLIENT, clientId))));

    getMatchingMappingRuleIds(claims)
        .forEach(
            mappingRuleId ->
                allGroups.addAll(
                    claimsExtractor.getGroups(claims, EntityType.MAPPING_RULE, mappingRuleId)));

    return allGroups;
  }

  private Stream<String> getMatchingMappingRuleIds(final Map<String, Object> claims) {
    final var tokenClaims = claimsExtractor.getTokenClaims(claims);
    return MappingRuleMatcher.matchingRules(mappingRuleState.getAll().stream(), tokenClaims)
        .map(PersistedMappingRule::getMappingRuleId);
  }

  private boolean matchesAssignee(final String userId, final Object assigneeValue) {
    return userId != null
        && assigneeValue instanceof String assignee
        && !assignee.isEmpty()
        && userId.equals(assignee);
  }

  private boolean matchesCandidateUsers(final String userId, final Object candidateUsersValue) {
    if (userId == null || !(candidateUsersValue instanceof List<?> candidateUsers)) {
      return false;
    }
    return candidateUsers.contains(userId);
  }

  private boolean matchesCandidateGroups(
      final Set<String> groups, final Object candidateGroupsValue) {
    if (groups.isEmpty() || !(candidateGroupsValue instanceof List<?> candidateGroups)) {
      return false;
    }
    return candidateGroups.stream().anyMatch(g -> g instanceof String && groups.contains(g));
  }
}
