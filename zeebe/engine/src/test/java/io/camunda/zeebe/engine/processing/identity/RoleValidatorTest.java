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

import io.camunda.security.api.model.authz.DefaultRole;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class RoleValidatorTest {

  private final FakeIdentityView view = new FakeIdentityView();
  private final AuthenticationConfiguration authentication =
      mock(AuthenticationConfiguration.class);
  private RoleValidator validator;

  @BeforeEach
  void setUp() {
    when(authentication.isCamundaUsersEnabled()).thenReturn(true);
    when(authentication.isCamundaGroupsEnabled()).thenReturn(true);
    validator =
        new RoleValidator(
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

  private static RoleRecord role(final String roleId) {
    return new RoleRecord().setRoleId(roleId);
  }

  private static RoleRecord member(
      final String roleId, final EntityType entityType, final String entityId) {
    return new RoleRecord().setRoleId(roleId).setEntityType(entityType).setEntityId(entityId);
  }

  @Nested
  final class Create {

    @Test
    void shouldAcceptANewRole() {
      // when
      final var result = validator.validateCreate(role(uid("role")));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAnExistingRole() {
      // given
      final var roleId = view.addRole(uid("role"));

      // when
      final var result = validator.validateCreate(role(roleId));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.ALREADY_EXISTS,
                  "Expected to create role with ID '%s', but a role with this ID already exists"
                      .formatted(roleId)));
    }
  }

  @Nested
  final class Update {

    @Test
    void shouldAcceptAnExistingRole() {
      // given
      final var roleId = view.addRole(uid("role"));

      // when
      final var result = validator.validateUpdate(role(roleId));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAMissingRole() {
      // given
      final var roleId = uid("role");

      // when
      final var result = validator.validateUpdate(role(roleId));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to update role with ID '%s', but a role with this ID does not exist."
                      .formatted(roleId)));
    }

    @Test
    void shouldRejectADefaultRoleEvenIfItExists() {
      // given
      final var roleId = view.addRole(DefaultRole.ADMIN.getId());

      // when
      final var result = validator.validateUpdate(role(roleId));

      // then
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.INVALID_STATE);
    }
  }

  @Nested
  final class Delete {

    @Test
    void shouldAcceptAnExistingRole() {
      // given
      final var roleId = view.addRole(uid("role"));

      // when
      final var result = validator.validateDelete(role(roleId));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAMissingRole() {
      // given
      final var roleId = uid("role");

      // when
      final var result = validator.validateDelete(role(roleId));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to delete role with ID '%s', but a role with this ID doesn't exist."
                      .formatted(roleId)));
    }

    @Test
    void shouldRejectADefaultRole() {
      // given
      final var roleId = view.addRole(DefaultRole.ADMIN.getId());

      // when
      final var result = validator.validateDelete(role(roleId));

      // then
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.INVALID_STATE);
    }
  }

  @Nested
  final class AddEntity {

    @Test
    void shouldAcceptAnExistingMappingRule() {
      // given
      final var roleId = view.addRole(uid("role"));
      final var ruleId = view.addMappingRule(uid("rule"));

      // when
      final var result =
          validator.validateAddEntity(member(roleId, EntityType.MAPPING_RULE, ruleId));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAMemberOfAMissingRole() {
      // given
      final var roleId = uid("role");

      // when
      final var result = validator.validateAddEntity(member(roleId, EntityType.CLIENT, "client"));

      // then
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.NOT_FOUND);
      assertThat(result.getLeft().reason()).contains("update role with ID '%s'".formatted(roleId));
    }

    @Test
    void shouldRejectAMissingUserIfLocalUsersAreEnabled() {
      // given
      final var roleId = view.addRole(uid("role"));
      final var username = uid("user");

      // when
      final var result = validator.validateAddEntity(member(roleId, EntityType.USER, username));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to add an entity with ID '%s' and type 'USER' to role with ID '%s', but the entity doesn't exist."
                      .formatted(username, roleId)));
    }

    @Test
    void shouldAcceptAnyUserIfLocalUsersAreDisabled() {
      // given
      when(authentication.isCamundaUsersEnabled()).thenReturn(false);
      final var roleId = view.addRole(uid("role"));

      // when
      final var result = validator.validateAddEntity(member(roleId, EntityType.USER, uid("user")));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAnAssignedMember() {
      // given
      final var roleId = view.addRole(uid("role"));
      view.addMember(roleId, EntityType.CLIENT, "client");

      // when
      final var result = validator.validateAddEntity(member(roleId, EntityType.CLIENT, "client"));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.ALREADY_EXISTS,
                  "Expected to add entity with ID 'client' to role with ID '%s', but the entity is already assigned to this role."
                      .formatted(roleId)));
    }
  }

  @Nested
  final class RemoveEntity {

    @Test
    void shouldAcceptAnAssignedMember() {
      // given
      final var roleId = view.addRole(uid("role"));
      final var username = uid("user");
      view.addMember(roleId, EntityType.USER, username);

      // when
      final var result = validator.validateRemoveEntity(member(roleId, EntityType.USER, username));

      // then the user itself doesn't have to exist
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAMissingMappingRule() {
      // given
      final var roleId = view.addRole(uid("role"));
      final var ruleId = uid("rule");

      // when
      final var result =
          validator.validateRemoveEntity(member(roleId, EntityType.MAPPING_RULE, ruleId));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to remove an entity with ID '%s' and type 'MAPPING_RULE' from role with ID '%s', but the entity doesn't exist."
                      .formatted(ruleId, roleId)));
    }

    @Test
    void shouldRejectAMemberThatIsNotAssigned() {
      // given
      final var roleId = view.addRole(uid("role"));

      // when
      final var result =
          validator.validateRemoveEntity(member(roleId, EntityType.CLIENT, "client"));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to remove entity with ID 'client' from role with ID '%s', but the entity is not assigned to this role."
                      .formatted(roleId)));
    }
  }

  private static final class FakeIdentityView implements IdentityView {

    private final Set<String> roles = new HashSet<>();
    private final Set<String> mappingRules = new HashSet<>();
    private final Set<String> roleMembers = new HashSet<>();

    String addRole(final String roleId) {
      roles.add(roleId);
      return roleId;
    }

    String addMappingRule(final String mappingRuleId) {
      mappingRules.add(mappingRuleId);
      return mappingRuleId;
    }

    void addMember(final String roleId, final EntityType entityType, final String entityId) {
      roleMembers.add(roleId + "/" + entityType + "/" + entityId);
    }

    @Override
    public boolean roleExists(final String roleId) {
      return roles.contains(roleId);
    }

    @Override
    public boolean groupExists(final String groupId) {
      return false;
    }

    @Override
    public boolean userExists(final String username) {
      return false;
    }

    @Override
    public boolean mappingRuleExists(final String mappingRuleId) {
      return mappingRules.contains(mappingRuleId);
    }

    @Override
    public boolean hasRelation(
        final EntityType entityType,
        final String entityId,
        final RelationType relationType,
        final String relationId) {
      return relationType == RelationType.ROLE
          && roleMembers.contains(relationId + "/" + entityType + "/" + entityId);
    }
  }
}
