/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.property.evaluator;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;
import static io.camunda.zeebe.engine.processing.identity.authorization.property.evaluator.UserTaskPropertyAuthorizationEvaluator.PROP_ASSIGNEE;
import static io.camunda.zeebe.engine.processing.identity.authorization.property.evaluator.UserTaskPropertyAuthorizationEvaluator.PROP_CANDIDATE_GROUPS;
import static io.camunda.zeebe.engine.processing.identity.authorization.property.evaluator.UserTaskPropertyAuthorizationEvaluator.PROP_CANDIDATE_USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.identity.authorization.resolver.ClaimsExtractor;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.authorization.PersistedMappingRule;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class UserTaskPropertyAuthorizationEvaluatorTest {

  @Mock private MembershipState membershipState;

  @Mock private MappingRuleState mappingRuleState;

  private UserTaskPropertyAuthorizationEvaluator evaluator;

  @BeforeEach
  void setUp() {
    final ClaimsExtractor claimsExtractor = new ClaimsExtractor(membershipState);
    evaluator = new UserTaskPropertyAuthorizationEvaluator(claimsExtractor, mappingRuleState);
  }

  @Test
  void shouldReturnUserTaskResourceType() {
    assertThat(evaluator.resourceType()).isEqualTo(AuthorizationResourceType.USER_TASK);
  }

  @ParameterizedTest
  @MethodSource("emptyResourcePropertiesInputs")
  void shouldReturnEmptySetWhenEmptyResourcePropertiesProvided(
      final Map<String, Object> resourceProperties) {
    // given
    final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, "testUser");

    // when
    final var result = evaluator.matches(claims, resourceProperties);

    // then
    assertThat(result).isEmpty();
  }

  static Stream<Arguments> emptyResourcePropertiesInputs() {
    return Stream.of(Arguments.of((Map) null), Arguments.of(Collections.emptyMap()));
  }

  @Test
  void shouldMatchMultiplePropertiesWhenAllMatch() {
    // given
    final var username = "testUser";
    final var userGroup = "userGroup";
    final var claims =
        Map.of(AUTHORIZED_USERNAME, username, USER_GROUPS_CLAIMS, List.of(userGroup));
    final var resourceProperties =
        Map.of(
            PROP_ASSIGNEE, username,
            PROP_CANDIDATE_USERS, List.of(username, "otherUser"),
            PROP_CANDIDATE_GROUPS, List.of(userGroup, "otherGroup"));

    // when
    final var result = evaluator.matches(claims, resourceProperties);

    // then
    assertThat(result)
        .containsExactlyInAnyOrder(PROP_ASSIGNEE, PROP_CANDIDATE_USERS, PROP_CANDIDATE_GROUPS);
  }

  @Nested
  class AssigneeMatching {

    @Test
    void shouldMatchAssigneeWhenUsernameEqualsAssignee() {
      // given
      final var username = "testUser";
      final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);
      final var resourceProperties = Map.<String, Object>of(PROP_ASSIGNEE, username);

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).containsExactly(PROP_ASSIGNEE);
    }

    @Test
    void shouldNotMatchAssigneeWhenUsernameDoesNotEqualAssignee() {
      // given
      final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, "testUser");
      final var resourceProperties = Map.<String, Object>of(PROP_ASSIGNEE, "differentUser");

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchAssigneeWhenAssigneeIsNotString() {
      // given
      final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, "testUser");
      final var resourceProperties = Map.<String, Object>of(PROP_ASSIGNEE, 123);

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchAssigneeWhenNoUserId() {
      // given
      final var claims = Map.<String, Object>of();
      final var resourceProperties = Map.<String, Object>of(PROP_ASSIGNEE, "testUser");

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  class CandidateUsersMatching {

    @Test
    void shouldMatchCandidateUsersWhenUsernameInList() {
      // given
      final var username = "testUser";
      final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);
      final var resourceProperties =
          Map.<String, Object>of(
              PROP_CANDIDATE_USERS, List.of("otherUser", username, "anotherUser"));

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).containsExactly(PROP_CANDIDATE_USERS);
    }

    @Test
    void shouldNotMatchCandidateUsersWhenUsernameNotInList() {
      // given
      final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, "testUser");
      final var resourceProperties =
          Map.<String, Object>of(PROP_CANDIDATE_USERS, List.of("user1", "user2", "user3"));

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchCandidateUsersWhenListIsEmpty() {
      // given
      final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, "testUser");
      final var resourceProperties =
          Map.<String, Object>of(PROP_CANDIDATE_USERS, Collections.emptyList());

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchCandidateUsersWhenNotAList() {
      // given
      final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, "testUser");
      final var resourceProperties = Map.<String, Object>of(PROP_CANDIDATE_USERS, "testUser");

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchCandidateUsersWhenNoUserId() {
      // given
      final var claims = Map.<String, Object>of();
      final var resourceProperties =
          Map.<String, Object>of(PROP_CANDIDATE_USERS, List.of("user1", "user2"));

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  class CandidateGroupsMatching {

    @Test
    void shouldMatchCandidateGroupsWhenUserGroupInList() {
      // given
      final var username = "testUser";
      final var userGroup = "userGroup";
      final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);
      final var resourceProperties =
          Map.<String, Object>of(PROP_CANDIDATE_GROUPS, List.of("group1", userGroup, "group2"));

      when(membershipState.getMemberships(EntityType.USER, username, RelationType.GROUP))
          .thenReturn(List.of(userGroup));

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).containsExactly(PROP_CANDIDATE_GROUPS);
    }

    @Test
    void shouldMatchCandidateGroupsWhenClientGroupInList() {
      // given
      final var clientId = "testClient";
      final var clientGroup = "clientGroup";
      final var claims = Map.<String, Object>of(AUTHORIZED_CLIENT_ID, clientId);
      final var resourceProperties =
          Map.<String, Object>of(PROP_CANDIDATE_GROUPS, List.of("group1", clientGroup));

      when(membershipState.getMemberships(EntityType.CLIENT, clientId, RelationType.GROUP))
          .thenReturn(List.of(clientGroup));

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).containsExactly(PROP_CANDIDATE_GROUPS);
    }

    @Test
    void shouldMatchCandidateGroupsWhenMappingRuleGroupInList() {
      // given
      final var claimName = "$.role";
      final var claimValue = "admin";
      final var mappingRuleId = "mapping-rule-1";
      final var mappingGroup = "mappingGroup";
      final var claims = Map.<String, Object>of(USER_TOKEN_CLAIMS, Map.of("role", claimValue));
      final var resourceProperties =
          Map.<String, Object>of(PROP_CANDIDATE_GROUPS, List.of("group1", mappingGroup));

      final var mappingRule =
          new PersistedMappingRule()
              .setMappingRuleId(mappingRuleId)
              .setClaimName(claimName)
              .setClaimValue(claimValue);
      when(mappingRuleState.getAll()).thenReturn(List.of(mappingRule));

      when(membershipState.getMemberships(
              EntityType.MAPPING_RULE, mappingRuleId, RelationType.GROUP))
          .thenReturn(List.of(mappingGroup));

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).containsExactly(PROP_CANDIDATE_GROUPS);
    }

    @Test
    void shouldMatchCandidateGroupsWhenMultipleGroupSourcesMatch() {
      // given
      final var username = "testUser";
      final var userGroup = "userGroup";
      final var claimName = "$.role";
      final var claimValue = "admin";
      final var mappingRuleId = "mapping-rule-1";
      final var mappingGroup = "mappingGroup";
      final var claims =
          Map.<String, Object>of(
              AUTHORIZED_USERNAME, username, USER_TOKEN_CLAIMS, Map.of("role", claimValue));
      final var resourceProperties =
          Map.<String, Object>of(PROP_CANDIDATE_GROUPS, List.of(mappingGroup, userGroup));

      when(membershipState.getMemberships(EntityType.USER, username, RelationType.GROUP))
          .thenReturn(List.of(userGroup));

      final var mappingRule =
          new PersistedMappingRule()
              .setMappingRuleId(mappingRuleId)
              .setClaimName(claimName)
              .setClaimValue(claimValue);
      when(mappingRuleState.getAll()).thenReturn(List.of(mappingRule));

      when(membershipState.getMemberships(
              EntityType.MAPPING_RULE, mappingRuleId, RelationType.GROUP))
          .thenReturn(List.of(mappingGroup));

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).containsExactly(PROP_CANDIDATE_GROUPS);
    }

    @Test
    void shouldNotMatchCandidateGroupsWhenUserGroupNotInList() {
      // given
      final var username = "testUser";
      final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);
      final var resourceProperties =
          Map.<String, Object>of(PROP_CANDIDATE_GROUPS, List.of("group1", "group2"));

      when(membershipState.getMemberships(EntityType.USER, username, RelationType.GROUP))
          .thenReturn(List.of("differentGroup"));

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchCandidateGroupsWhenUserHasNoGroups() {
      // given
      final var username = "testUser";
      final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);
      final var resourceProperties =
          Map.<String, Object>of(PROP_CANDIDATE_GROUPS, List.of("group1", "group2"));

      when(membershipState.getMemberships(EntityType.USER, username, RelationType.GROUP))
          .thenReturn(Collections.emptyList());

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchCandidateGroupsWhenNotAList() {
      // given
      final var username = "testUser";
      final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);
      final var resourceProperties = Map.<String, Object>of(PROP_CANDIDATE_GROUPS, "group1");

      when(membershipState.getMemberships(EntityType.USER, username, RelationType.GROUP))
          .thenReturn(List.of("group1"));

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchCandidateGroupsWhenCandidateGroupsListIsEmpty() {
      // given
      final var username = "testUser";
      final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);
      final var resourceProperties =
          Map.<String, Object>of(PROP_CANDIDATE_GROUPS, Collections.emptyList());

      when(membershipState.getMemberships(EntityType.USER, username, RelationType.GROUP))
          .thenReturn(List.of("group1"));

      // when
      final var result = evaluator.matches(claims, resourceProperties);

      // then
      assertThat(result).isEmpty();
    }
  }
}
