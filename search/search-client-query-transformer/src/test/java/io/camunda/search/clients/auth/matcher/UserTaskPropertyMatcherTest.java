/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.UserTaskEntity;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UserTaskPropertyMatcherTest {

  private UserTaskPropertyMatcher matcher;

  @BeforeEach
  void setUp() {
    matcher = new UserTaskPropertyMatcher();
  }

  @Test
  void shouldReturnUserTaskEntityClass() {
    // when
    final var resourceClass = matcher.getResourceClass();

    // then
    assertThat(resourceClass).isEqualTo(UserTaskEntity.class);
  }

  @Test
  void shouldMatchWhenUserIsAssignee() {
    // given
    final var userTask = createUserTask("frodo", List.of(), List.of());
    final var authentication = CamundaAuthentication.of(a -> a.user("frodo"));
    final var propertyNames = Set.of(Authorization.PROP_ASSIGNEE);

    // when
    final var matches = matcher.matches(userTask, propertyNames, authentication);

    // then
    assertThat(matches).isTrue();
  }

  @ParameterizedTest(name = "assignee=''{0}'', username=''{1}'' should not match")
  @MethodSource("provideAssigneeNegativeCases")
  void shouldNotMatchForInvalidAssigneeCases(final String assignee, final String username) {
    // given
    final var userTask = createUserTask(assignee, List.of(), List.of());
    final var authentication = CamundaAuthentication.of(a -> a.user(username));
    final var propertyNames = Set.of(Authorization.PROP_ASSIGNEE);

    // when
    final var matches = matcher.matches(userTask, propertyNames, authentication);

    // then
    assertThat(matches).isFalse();
  }

  static Stream<Arguments> provideAssigneeNegativeCases() {
    return Stream.of(
        Arguments.of("gandalf", "frodo"), // different assignee
        Arguments.of(null, "frodo"), // null assignee
        Arguments.of("frodo", null), // null username
        Arguments.of("frodo", "") // empty username
        );
  }

  @Test
  void shouldMatchWhenUserIsInCandidateUsers() {
    // given
    final var userTask = createUserTask("gandalf", List.of("frodo", "aragorn"), List.of());
    final var authentication = CamundaAuthentication.of(a -> a.user("frodo"));
    final var propertyNames = Set.of(Authorization.PROP_CANDIDATE_USERS);

    // when
    final var matches = matcher.matches(userTask, propertyNames, authentication);

    // then
    assertThat(matches).isTrue();
  }

  @ParameterizedTest(name = "candidateUsers={0}, username=''{1}'' should not match")
  @MethodSource("provideCandidateUsersNegativeCases")
  void shouldNotMatchForInvalidCandidateUsersCases(
      final List<String> candidateUsers, final String username) {
    // given
    final var userTask = createUserTask("gandalf", candidateUsers, List.of());
    final var authentication = CamundaAuthentication.of(a -> a.user(username));
    final var propertyNames = Set.of(Authorization.PROP_CANDIDATE_USERS);

    // when
    final var matches = matcher.matches(userTask, propertyNames, authentication);

    // then
    assertThat(matches).isFalse();
  }

  static Stream<Arguments> provideCandidateUsersNegativeCases() {
    return Stream.of(
        Arguments.of(List.of("aragorn", "legolas"), "frodo"), // user not in list
        Arguments.of(null, "frodo"), // null candidate users
        Arguments.of(List.of(), "frodo"), // empty candidate users
        Arguments.of(List.of("aragorn"), null) // null username
        );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("provideCandidateGroupsPositiveCases")
  void shouldMatchWhenUserGroupsMatchCandidateGroups(
      final String testName, final List<String> candidateGroups, final List<String> userGroups) {
    // given
    final var userTask = createUserTask("gandalf", List.of(), candidateGroups);
    final var authentication = CamundaAuthentication.of(a -> a.user("frodo").groupIds(userGroups));
    final var propertyNames = Set.of(Authorization.PROP_CANDIDATE_GROUPS);

    // when
    final var matches = matcher.matches(userTask, propertyNames, authentication);

    // then
    assertThat(matches).isTrue();
  }

  static Stream<Arguments> provideCandidateGroupsPositiveCases() {
    return Stream.of(
        Arguments.of("single user group matches", List.of("hobbits", "elves"), List.of("hobbits")),
        Arguments.of(
            "multiple user groups match",
            List.of("hobbits", "elves"),
            List.of("dwarves", "elves", "wizards", "hobbits")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("provideCandidateGroupsNegativeCases")
  void shouldNotMatchForInvalidCandidateGroupsCases(
      final String testName, final List<String> candidateGroups, final List<String> userGroups) {
    // given
    final var userTask = createUserTask("gandalf", List.of(), candidateGroups);
    final var authentication = CamundaAuthentication.of(a -> a.user("frodo").groupIds(userGroups));
    final var propertyNames = Set.of(Authorization.PROP_CANDIDATE_GROUPS);

    // when
    final var matches = matcher.matches(userTask, propertyNames, authentication);

    // then
    assertThat(matches).isFalse();
  }

  static Stream<Arguments> provideCandidateGroupsNegativeCases() {
    return Stream.of(
        Arguments.of(
            "no user group matches", List.of("hobbits", "elves"), List.of("dwarves", "wizards")),
        Arguments.of("user groups is null", List.of("hobbits"), null),
        Arguments.of("user groups is empty", List.of("hobbits"), List.of()),
        Arguments.of("candidate groups is null", null, List.of("hobbits")),
        Arguments.of("candidate groups is empty", List.of(), List.of("hobbits")));
  }

  @Test
  void shouldNotMatchWhenPropertyNameIsUnknown() {
    // given
    final var userTask = createUserTask("frodo", List.of(), List.of());
    final var authentication = CamundaAuthentication.of(a -> a.user("frodo"));
    final var propertyNames = Set.of("unknownProperty");

    // when
    final var matches = matcher.matches(userTask, propertyNames, authentication);

    // then
    assertThat(matches).isFalse();
  }

  @Test
  void shouldNotMatchWhenPropertyNamesIsEmpty() {
    // given
    final var userTask = createUserTask("frodo", List.of(), List.of());
    final var authentication = CamundaAuthentication.of(a -> a.user("frodo"));

    // when
    final var matches = matcher.matches(userTask, Set.of(), authentication);

    // then
    assertThat(matches).isFalse();
  }

  private UserTaskEntity createUserTask(
      final String assignee,
      final List<String> candidateUsers,
      final List<String> candidateGroups) {
    final var userTask = mock(UserTaskEntity.class);
    when(userTask.assignee()).thenReturn(assignee);
    when(userTask.candidateUsers()).thenReturn(candidateUsers);
    when(userTask.candidateGroups()).thenReturn(candidateGroups);
    return userTask;
  }
}
