/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class GroupValidatorTest {

  private final FakeIdentityView view = new FakeIdentityView();
  private final AuthenticationConfiguration authentication =
      mock(AuthenticationConfiguration.class);
  private GroupValidator validator;

  @BeforeEach
  void setUp() {
    when(authentication.isCamundaUsersEnabled()).thenReturn(true);
    when(authentication.isCamundaGroupsEnabled()).thenReturn(true);
    validator =
        new GroupValidator(
            view,
            new EngineSecurityConfig(
                authentication,
                /* authorizationsEnabled= */ true,
                /* multiTenancyChecksEnabled= */ false,
                new InitializationConfiguration(),
                EngineSecurityConfigurations.ID_VALIDATION_PATTERN,
                EngineSecurityConfigurations.GROUP_ID_VALIDATION_PATTERN));
  }

  private static String uid(final String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }

  private static GroupRecord group(final String groupId) {
    return new GroupRecord().setGroupId(groupId);
  }

  private static GroupRecord member(
      final String groupId, final EntityType entityType, final String entityId) {
    return new GroupRecord().setGroupId(groupId).setEntityType(entityType).setEntityId(entityId);
  }

  @Nested
  final class Create {

    @Test
    void shouldAcceptANewGroup() {
      // when
      final var result = validator.validateCreate(group(uid("group")));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAnExistingGroup() {
      // given
      final var groupId = view.addGroup(uid("group"));

      // when
      final var result = validator.validateCreate(group(groupId));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.ALREADY_EXISTS,
                  "Expected to create group with ID '%s', but a group with this ID already exists."
                      .formatted(groupId)));
    }
  }

  @Nested
  final class Update {

    @Test
    void shouldAcceptAnExistingGroup() {
      // given
      final var groupId = view.addGroup(uid("group"));

      // when
      final var result = validator.validateUpdate(group(groupId));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAMissingGroup() {
      // given
      final var groupId = uid("group");

      // when
      final var result = validator.validateUpdate(group(groupId));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to update group with ID '%s', but a group with this ID does not exist."
                      .formatted(groupId)));
    }
  }

  @Nested
  final class Delete {

    @Test
    void shouldAcceptAnExistingGroup() {
      // given
      final var groupId = view.addGroup(uid("group"));

      // when
      final var result = validator.validateDelete(group(groupId));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAMissingGroup() {
      // given
      final var groupId = uid("group");

      // when
      final var result = validator.validateDelete(group(groupId));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to delete group with ID '%s', but a group with this ID does not exist."
                      .formatted(groupId)));
    }
  }

  @Nested
  final class AddEntity {

    @Test
    void shouldAcceptAnExistingUser() {
      // given
      final var groupId = view.addGroup(uid("group"));
      final var username = view.addUser(uid("user"));

      // when
      final var result = validator.validateAddEntity(member(groupId, EntityType.USER, username));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAMemberOfAMissingGroup() {
      // given
      final var groupId = uid("group");

      // when
      final var result = validator.validateAddEntity(member(groupId, EntityType.CLIENT, "client"));

      // then
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.NOT_FOUND);
      assertThat(result.getLeft().reason())
          .contains("update group with ID '%s'".formatted(groupId));
    }

    @Test
    void shouldRejectAMissingUserIfLocalUsersAreEnabled() {
      // given
      final var groupId = view.addGroup(uid("group"));
      final var username = uid("user");

      // when
      final var result = validator.validateAddEntity(member(groupId, EntityType.USER, username));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to add an entity with ID '%s' and type 'USER' to group with ID '%s', but the entity does not exist."
                      .formatted(username, groupId)));
    }

    @Test
    void shouldAcceptAnyUserIfLocalUsersAreDisabled() {
      // given
      when(authentication.isCamundaUsersEnabled()).thenReturn(false);
      final var groupId = view.addGroup(uid("group"));

      // when
      final var result = validator.validateAddEntity(member(groupId, EntityType.USER, uid("user")));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectARoleAsMember() {
      // given
      final var groupId = view.addGroup(uid("group"));
      final var roleId = view.addRole(uid("role"));

      // when
      final var result = validator.validateAddEntity(member(groupId, EntityType.ROLE, roleId));

      // then
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.NOT_FOUND);
    }

    @Test
    void shouldRejectAnAssignedMember() {
      // given
      final var groupId = view.addGroup(uid("group"));
      view.addRelation(RelationType.GROUP, groupId, EntityType.CLIENT, "client");

      // when
      final var result = validator.validateAddEntity(member(groupId, EntityType.CLIENT, "client"));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.ALREADY_EXISTS,
                  "Expected to add entity with ID 'client' to group with ID '%s', but the entity is already assigned to this group."
                      .formatted(groupId)));
    }
  }

  @Nested
  final class RemoveEntity {

    @Test
    void shouldAcceptAnAssignedMember() {
      // given
      final var groupId = view.addGroup(uid("group"));
      final var username = uid("user");
      view.addRelation(RelationType.GROUP, groupId, EntityType.USER, username);

      // when
      final var result = validator.validateRemoveEntity(member(groupId, EntityType.USER, username));

      // then the user itself doesn't have to exist
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAMissingMappingRule() {
      // given
      final var groupId = view.addGroup(uid("group"));
      final var ruleId = uid("rule");

      // when
      final var result =
          validator.validateRemoveEntity(member(groupId, EntityType.MAPPING_RULE, ruleId));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to remove an entity with ID '%s' and type 'MAPPING_RULE' from group with ID '%s', but the entity does not exist."
                      .formatted(ruleId, groupId)));
    }

    @Test
    void shouldRejectAMemberThatIsNotAssigned() {
      // given
      final var groupId = view.addGroup(uid("group"));

      // when
      final var result =
          validator.validateRemoveEntity(member(groupId, EntityType.CLIENT, "client"));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to remove entity with ID 'client' from group with ID '%s', but the entity is not assigned to this group."
                      .formatted(groupId)));
    }
  }
}
