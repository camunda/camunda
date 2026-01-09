/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.property.evaluator;

import static io.camunda.zeebe.engine.processing.identity.authorization.property.UserTaskAuthorizationProperties.PROP_ASSIGNEE;
import static io.camunda.zeebe.engine.processing.identity.authorization.property.UserTaskAuthorizationProperties.PROP_CANDIDATE_GROUPS;
import static io.camunda.zeebe.engine.processing.identity.authorization.property.UserTaskAuthorizationProperties.PROP_CANDIDATE_USERS;

import io.camunda.security.auth.MappingRuleMatcher;
import io.camunda.zeebe.engine.processing.identity.authorization.property.UserTaskAuthorizationProperties;
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

/**
 * Evaluates property-based authorization for user tasks.
 *
 * <p>Supports matching against:
 *
 * <ul>
 *   <li>{@code assignee} - matches when the requesting user is the task assignee
 *   <li>{@code candidateUsers} - matches when the requesting user is in the candidate users list
 *   <li>{@code candidateGroups} - matches when any of the requesting principal's groups overlap
 *       with candidate groups
 * </ul>
 */
public final class UserTaskPropertyAuthorizationEvaluator
    implements PropertyAuthorizationEvaluator<UserTaskAuthorizationProperties> {

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
  public Class<UserTaskAuthorizationProperties> propertiesType() {
    return UserTaskAuthorizationProperties.class;
  }

  @Override
  public Set<String> matches(
      final Map<String, Object> claims, final UserTaskAuthorizationProperties properties) {
    if (properties == null || !properties.hasProperties()) {
      return Collections.emptySet();
    }

    final Set<String> matched = new HashSet<>();

    // only users can be assigned to user tasks or be part of candidateUsers
    claimsExtractor
        .getUsername(claims)
        .ifPresent(
            username -> {
              if (matchesAssignee(username, properties)) {
                matched.add(PROP_ASSIGNEE);
              }

              if (matchesCandidateUsers(username, properties)) {
                matched.add(PROP_CANDIDATE_USERS);
              }
            });

    if (properties.hasCandidateGroups()) {
      final var allGroups = collectAllGroups(claims);
      if (matchesCandidateGroups(allGroups, properties.candidateGroups())) {
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

  private boolean matchesAssignee(
      final String userId, final UserTaskAuthorizationProperties properties) {
    return properties.hasAssignee() && userId.equals(properties.assignee());
  }

  private boolean matchesCandidateUsers(
      final String userId, final UserTaskAuthorizationProperties properties) {
    return properties.hasCandidateUsers() && properties.candidateUsers().contains(userId);
  }

  private boolean matchesCandidateGroups(
      final Set<String> groups, final List<String> candidateGroups) {
    if (groups.isEmpty()) {
      return false;
    }
    return candidateGroups.stream().anyMatch(groups::contains);
  }
}
