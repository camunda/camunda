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
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class TenantValidatorTest {

  private final FakeIdentityView view = new FakeIdentityView();
  private final AuthenticationConfiguration authentication =
      mock(AuthenticationConfiguration.class);
  private TenantValidator validator;

  @BeforeEach
  void setUp() {
    when(authentication.isCamundaUsersEnabled()).thenReturn(true);
    when(authentication.isCamundaGroupsEnabled()).thenReturn(true);
    validator =
        new TenantValidator(
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

  private static TenantRecord tenant(final String tenantId) {
    return new TenantRecord().setTenantId(tenantId);
  }

  private static TenantRecord member(
      final String tenantId, final EntityType entityType, final String entityId) {
    return new TenantRecord().setTenantId(tenantId).setEntityType(entityType).setEntityId(entityId);
  }

  @Nested
  final class Create {

    @Test
    void shouldAcceptANewTenant() {
      // when
      final var result = validator.validateCreate(tenant(uid("tenant")));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAnExistingTenant() {
      // given
      final var tenantId = view.addTenant(uid("tenant"));

      // when
      final var result = validator.validateCreate(tenant(tenantId));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.ALREADY_EXISTS,
                  "Expected to create tenant with ID '%s', but a tenant with this ID already exists"
                      .formatted(tenantId)));
    }
  }

  @Nested
  final class Update {

    @Test
    void shouldAcceptAnExistingTenant() {
      // given
      final var tenantId = view.addTenant(uid("tenant"));

      // when
      final var result = validator.validateUpdate(tenant(tenantId));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAMissingTenant() {
      // given
      final var tenantId = uid("tenant");

      // when
      final var result = validator.validateUpdate(tenant(tenantId));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to update tenant with id '%s', but no tenant with this id exists."
                      .formatted(tenantId)));
    }
  }

  @Nested
  final class Delete {

    @Test
    void shouldAcceptAnExistingTenant() {
      // given
      final var tenantId = view.addTenant(uid("tenant"));

      // when
      final var result = validator.validateDelete(tenant(tenantId));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAMissingTenant() {
      // given
      final var tenantId = uid("tenant");

      // when
      final var result = validator.validateDelete(tenant(tenantId));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to delete tenant with id '%s', but no tenant with this id exists."
                      .formatted(tenantId)));
    }
  }

  @Nested
  final class AddEntity {

    @Test
    void shouldAcceptAnExistingRole() {
      // given
      final var tenantId = view.addTenant(uid("tenant"));
      final var roleId = view.addRole(uid("role"));

      // when
      final var result = validator.validateAddEntity(member(tenantId, EntityType.ROLE, roleId));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAMemberOfAMissingTenant() {
      // given
      final var tenantId = uid("tenant");

      // when
      final var result = validator.validateAddEntity(member(tenantId, EntityType.CLIENT, "client"));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to add entity to tenant with ID '%s', but no tenant with this ID exists."
                      .formatted(tenantId)));
    }

    @Test
    void shouldRejectAMissingRole() {
      // given
      final var tenantId = view.addTenant(uid("tenant"));
      final var roleId = uid("role");

      // when
      final var result = validator.validateAddEntity(member(tenantId, EntityType.ROLE, roleId));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to add role with ID '%s' to tenant with ID '%s', but the role doesn't exist."
                      .formatted(roleId, tenantId)));
    }

    @Test
    void shouldRejectAMissingGroupIfLocalGroupsAreEnabled() {
      // given
      final var tenantId = view.addTenant(uid("tenant"));

      // when
      final var result =
          validator.validateAddEntity(member(tenantId, EntityType.GROUP, uid("group")));

      // then
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.NOT_FOUND);
    }

    @Test
    void shouldAcceptAnyGroupIfLocalGroupsAreDisabled() {
      // given
      when(authentication.isCamundaGroupsEnabled()).thenReturn(false);
      final var tenantId = view.addTenant(uid("tenant"));

      // when
      final var result =
          validator.validateAddEntity(member(tenantId, EntityType.GROUP, uid("group")));

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAnAssignedMember() {
      // given
      final var tenantId = view.addTenant(uid("tenant"));
      view.addRelation(RelationType.TENANT, tenantId, EntityType.CLIENT, "client");

      // when
      final var result = validator.validateAddEntity(member(tenantId, EntityType.CLIENT, "client"));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.ALREADY_EXISTS,
                  "Expected to add client with ID 'client' to tenant with ID '%s', but the client is already assigned to the tenant."
                      .formatted(tenantId)));
    }
  }

  @Nested
  final class RemoveEntity {

    @Test
    void shouldAcceptAnAssignedMember() {
      // given
      final var tenantId = view.addTenant(uid("tenant"));
      final var username = uid("user");
      view.addRelation(RelationType.TENANT, tenantId, EntityType.USER, username);

      // when
      final var result =
          validator.validateRemoveEntity(member(tenantId, EntityType.USER, username));

      // then the user itself doesn't have to exist
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAMemberOfAMissingTenant() {
      // given
      final var tenantId = uid("tenant");

      // when
      final var result =
          validator.validateRemoveEntity(member(tenantId, EntityType.CLIENT, "client"));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to remove entity from tenant '%s', but no tenant with this ID exists."
                      .formatted(tenantId)));
    }

    @Test
    void shouldRejectAMissingMappingRule() {
      // given
      final var tenantId = view.addTenant(uid("tenant"));
      final var ruleId = uid("rule");

      // when
      final var result =
          validator.validateRemoveEntity(member(tenantId, EntityType.MAPPING_RULE, ruleId));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to remove mapping_rule with ID '%s' from tenant with ID '%s', but the mapping_rule doesn't exists."
                      .formatted(ruleId, tenantId)));
    }

    @Test
    void shouldRejectAMemberThatIsNotAssigned() {
      // given
      final var tenantId = view.addTenant(uid("tenant"));

      // when
      final var result =
          validator.validateRemoveEntity(member(tenantId, EntityType.CLIENT, "client"));

      // then
      assertThat(result.getLeft())
          .isEqualTo(
              new Rejection(
                  RejectionType.NOT_FOUND,
                  "Expected to remove client with ID 'client' from tenant with ID '%s', but the client is not assigned to this tenant."
                      .formatted(tenantId)));
    }
  }
}
