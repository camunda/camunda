/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.authorization.PersistedMappingRule;
import io.camunda.zeebe.engine.state.authorization.PersistedRole;
import io.camunda.zeebe.engine.state.group.PersistedGroup;
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.user.PersistedUser;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationEntityCheckerTest {

  @Mock private ProcessingState processingState;
  @Mock private UserState userState;
  @Mock private MappingRuleState mappingRuleState;
  @Mock private GroupState groupState;
  @Mock private RoleState roleState;

  @Mock private TypedRecord<AuthorizationRecord> command;

  private AuthorizationEntityChecker checker;

  @BeforeEach
  void setUp() {
    when(processingState.getUserState()).thenReturn(userState);
    when(processingState.getMappingRuleState()).thenReturn(mappingRuleState);
    when(processingState.getGroupState()).thenReturn(groupState);
    when(processingState.getRoleState()).thenReturn(roleState);

    checker = new AuthorizationEntityChecker(processingState);
    lenient().when(userState.getUser("user1")).thenReturn(Optional.of(mock(PersistedUser.class)));
    lenient().when(roleState.getRole("role1")).thenReturn(Optional.of(mock(PersistedRole.class)));
    lenient().when(groupState.get("group1")).thenReturn(Optional.of(mock(PersistedGroup.class)));
    lenient()
        .when(mappingRuleState.get("mapping-rule1"))
        .thenReturn(Optional.of(mock(PersistedMappingRule.class)));
  }

  @ParameterizedTest
  @MethodSource("provideValidTestCases")
  void shouldSucceedWhenOwnerAndResourceExist(
      final String testCase,
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final String resourceId,
      final boolean localUserEnabled,
      final boolean localGroupEnabled) {

    // given
    final AuthorizationRecord record = new AuthorizationRecord();
    record.setOwnerType(ownerType);
    record.setOwnerId(ownerId);
    record.setResourceType(resourceType);
    record.setResourceId(resourceId);

    when(command.getValue()).thenReturn(record);
    when(command.getAuthorizations())
        .thenReturn(
            Map.of(
                "LOCAL_USER_ENABLED", localUserEnabled,
                "LOCAL_GROUP_ENABLED", localGroupEnabled));
    // when
    final Either<Rejection, AuthorizationRecord> result = checker.ownerAndResourceExists(command);

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).isEqualTo(record);
  }

  @ParameterizedTest
  @MethodSource("provideInvalidTestCases")
  void shouldFailWhenOwnerOrResourceNotExist(
      final String testCase,
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final String resourceId,
      final boolean localUserEnabled,
      final boolean localGroupEnabled,
      final String expectedErrorMessage) {

    // given
    final AuthorizationRecord record = new AuthorizationRecord();
    record.setOwnerType(ownerType);
    record.setOwnerId(ownerId);
    record.setResourceType(resourceType);
    record.setResourceId(resourceId);

    when(command.getValue()).thenReturn(record);
    when(command.getAuthorizations())
        .thenReturn(
            Map.of(
                "LOCAL_USER_ENABLED", localUserEnabled,
                "LOCAL_GROUP_ENABLED", localGroupEnabled));

    // when
    final Either<Rejection, AuthorizationRecord> result = checker.ownerAndResourceExists(command);

    // then
    assertThat(result.isLeft()).isTrue();
    final Rejection rejection = result.getLeft();
    assertThat(rejection.type()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.reason()).isEqualTo(expectedErrorMessage);
  }

  private static Stream<Arguments> provideValidTestCases() {
    return Stream.of(
        Arguments.of(
            "User owner with role resource - both exist",
            AuthorizationOwnerType.USER,
            "user1",
            AuthorizationResourceType.ROLE,
            "role1",
            true,
            true),
        Arguments.of(
            "Group owner with user resource - both exist",
            AuthorizationOwnerType.GROUP,
            "group1",
            AuthorizationResourceType.USER,
            "user1",
            true,
            true),
        Arguments.of(
            "Mapping rule owner with group resource - both exist",
            AuthorizationOwnerType.MAPPING_RULE,
            "mapping-rule1",
            AuthorizationResourceType.GROUP,
            "group1",
            true,
            true),
        Arguments.of(
            "Role owner with mapping rule resource - both exist",
            AuthorizationOwnerType.ROLE,
            "role1",
            AuthorizationResourceType.MAPPING_RULE,
            "mapping-rule1",
            true,
            true),
        Arguments.of(
            "Wildcard resource - owner exists",
            AuthorizationOwnerType.USER,
            "user1",
            AuthorizationResourceType.USER,
            "*",
            true,
            true),
        Arguments.of(
            "User owner does not exist but local user disabled - should succeed",
            AuthorizationOwnerType.USER,
            "nonExistentUser",
            AuthorizationResourceType.GROUP,
            "group1",
            false,
            true),
        Arguments.of(
            "Group owner does not exist but local group disabled - should succeed",
            AuthorizationOwnerType.GROUP,
            "nonExistentGroup",
            AuthorizationResourceType.USER,
            "user1",
            true,
            false),
        Arguments.of(
            "User resource does not exist but local user disabled - should succeed",
            AuthorizationOwnerType.ROLE,
            "role1",
            AuthorizationResourceType.USER,
            "nonExistentUser",
            false,
            true),
        Arguments.of(
            "Group resource does not exist but local group disabled - should succeed",
            AuthorizationOwnerType.USER,
            "user1",
            AuthorizationResourceType.GROUP,
            "nonExistentGroup",
            true,
            false));
  }

  private static Stream<Arguments> provideInvalidTestCases() {
    return Stream.of(
        Arguments.of(
            "User owner does not exist - local user enabled",
            AuthorizationOwnerType.USER,
            "nonExistentUser",
            AuthorizationResourceType.ROLE,
            "role1",
            true,
            false,
            AuthorizationEntityChecker.USER_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(
                "nonExistentUser")),
        Arguments.of(
            "Group owner does not exist - local group enabled",
            AuthorizationOwnerType.GROUP,
            "nonExistentGroup",
            AuthorizationResourceType.USER,
            "user1",
            false,
            true,
            AuthorizationEntityChecker.GROUP_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(
                "nonExistentGroup")),
        Arguments.of(
            "Mapping rule owner does not exist",
            AuthorizationOwnerType.MAPPING_RULE,
            "nonExistentMapping",
            AuthorizationResourceType.ROLE,
            "role1",
            false,
            false,
            AuthorizationEntityChecker.MAPPING_RULE_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(
                "nonExistentMapping")),
        Arguments.of(
            "Role owner does not exist",
            AuthorizationOwnerType.ROLE,
            "nonExistentRole",
            AuthorizationResourceType.MAPPING_RULE,
            "mapping1",
            false,
            false,
            AuthorizationEntityChecker.ROLE_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(
                "nonExistentRole")),

        // Resource validation failures
        Arguments.of(
            "User resource does not exist - local user enabled",
            AuthorizationOwnerType.ROLE,
            "role1",
            AuthorizationResourceType.USER,
            "nonExistentUser",
            true,
            false,
            AuthorizationEntityChecker.USER_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(
                "nonExistentUser")),
        Arguments.of(
            "Group resource does not exist - local group enabled",
            AuthorizationOwnerType.USER,
            "user1",
            AuthorizationResourceType.GROUP,
            "nonExistentGroup",
            false,
            true,
            AuthorizationEntityChecker.GROUP_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(
                "nonExistentGroup")),
        Arguments.of(
            "Mapping rule resource does not exist",
            AuthorizationOwnerType.ROLE,
            "role1",
            AuthorizationResourceType.MAPPING_RULE,
            "nonExistentMapping",
            false,
            false,
            AuthorizationEntityChecker.MAPPING_RULE_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(
                "nonExistentMapping")),
        Arguments.of(
            "Role resource does not exist",
            AuthorizationOwnerType.USER,
            "user1",
            AuthorizationResourceType.ROLE,
            "nonExistentRole",
            false,
            false,
            AuthorizationEntityChecker.ROLE_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(
                "nonExistentRole")));
  }
}
