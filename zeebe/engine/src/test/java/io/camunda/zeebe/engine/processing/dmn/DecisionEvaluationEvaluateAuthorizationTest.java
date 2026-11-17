/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.dmn;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class DecisionEvaluationEvaluateAuthorizationTest {
  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());
  private static final String DMN_RESOURCE = "/dmn/drg-force-user.dmn";
  private static final String DECISION_ID = "jedi_or_sith";
  private static final String CUSTOM_TENANT = "custom-tenant";
  private static final String OTHER_TENANT = "other-tenant";

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withAuthorizationsEnabled(true)
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withMultiTenancyChecksEnabled(true)
          .withSecurityConfig(
              cfg -> {
                final var defaultRoles = new HashMap<>(cfg.getInitialization().getDefaultRoles());
                defaultRoles.put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
                cfg.getInitialization().setDefaultRoles(defaultRoles);
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Before
  public void before() {
    engine.deployment().withXmlClasspathResource(DMN_RESOURCE).deploy(DEFAULT_USER.getUsername());
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, DEFAULT_USER.getUsername());
    engine.tenant().newTenant().withTenantId(CUSTOM_TENANT).withName("Custom Tenant").create();
    engine.tenant().newTenant().withTenantId(OTHER_TENANT).withName("Other Tenant").create();
    assignUserToTenant(CUSTOM_TENANT, DEFAULT_USER.getUsername());
    assignUserToTenant(OTHER_TENANT, DEFAULT_USER.getUsername());
  }

  @Test
  public void shouldBeAuthorizedToEvaluateDecisionWithDefaultUser() {
    // when
    final var response =
        engine
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "red")
            .evaluate(DEFAULT_USER.getUsername());

    // then
    assertThat(response.getValue().getDecisionOutput()).isEqualTo("\"Sith\"");
  }

  @Test
  public void shouldBeAuthorizedToEvaluateDecisionWithUser() {
    // given
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());
    addPermissionsToUser(
        user,
        AuthorizationResourceType.DECISION_DEFINITION,
        PermissionType.CREATE_DECISION_INSTANCE);

    // when
    final var response =
        engine
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "red")
            .evaluate(user.getUsername());

    // then
    assertThat(response.getValue().getDecisionOutput()).isEqualTo("\"Sith\"");
  }

  @Test
  public void shouldBeUnauthorizedToEvaluateDecisionIfNoPermissions() {
    // given
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());

    // when
    final var rejection =
        engine
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "red")
            .expectRejection()
            .evaluate(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE_DECISION_INSTANCE' on resource 'DECISION_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(DECISION_ID));
  }

  @Test
  public void shouldRejectEvaluationWhenAssignedTenantsExcludeDecisionTenant() {
    // given - decision deployed under CUSTOM_TENANT, caller has a global (non-tenant-restricted)
    // CREATE_DECISION_INSTANCE permission but is only assigned to OTHER_TENANT
    final var decisionKey = deployToTenant(CUSTOM_TENANT).getDecisionKey();
    final var user = createUser();
    assignUserToTenant(OTHER_TENANT, user.getUsername());
    addPermissionsToUser(
        user,
        AuthorizationResourceType.DECISION_DEFINITION,
        PermissionType.CREATE_DECISION_INSTANCE);

    // when
    final var rejection =
        engine
            .decision()
            .ofDecisionKey(decisionKey)
            .withVariable("lightsaberColor", "red")
            .withTenant(CUSTOM_TENANT)
            .expectRejection()
            .evaluate(user.getUsername());

    // then - tenant denial on an existing decision forwards as NOT_FOUND, masking its existence
    // from callers unauthorized for its tenant
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to evaluate a decision with key '%d', but no such decision was found"
                .formatted(decisionKey));
  }

  @Test
  public void shouldEvaluateDecisionWhenAssignedToDecisionTenant() {
    // given - decision deployed under CUSTOM_TENANT, caller has a global (non-tenant-restricted)
    // CREATE_DECISION_INSTANCE permission and is assigned to CUSTOM_TENANT
    final var decisionKey = deployToTenant(CUSTOM_TENANT).getDecisionKey();
    final var user = createUser();
    assignUserToTenant(CUSTOM_TENANT, user.getUsername());
    addPermissionsToUser(
        user,
        AuthorizationResourceType.DECISION_DEFINITION,
        PermissionType.CREATE_DECISION_INSTANCE);

    // when
    final var response =
        engine
            .decision()
            .ofDecisionKey(decisionKey)
            .withVariable("lightsaberColor", "red")
            .withTenant(CUSTOM_TENANT)
            .evaluate(user.getUsername());

    // then
    assertThat(response.getValue().getDecisionOutput()).isEqualTo("\"Sith\"");
  }

  private DecisionRecordValue deployToTenant(final String tenantId) {
    final var deployment =
        engine
            .deployment()
            .withXmlClasspathResource(DMN_RESOURCE)
            .withTenantId(tenantId)
            .deploy(DEFAULT_USER.getUsername());
    final Function<DecisionRecordValue, String> byDecisionId = DecisionRecordValue::getDecisionId;
    return deployment.getValue().getDecisionsMetadata().stream()
        .collect(Collectors.toMap(byDecisionId, Function.identity()))
        .get(DECISION_ID);
  }

  private void assignUserToTenant(final String tenantId, final String username) {
    engine
        .tenant()
        .addEntity(tenantId)
        .withEntityType(EntityType.USER)
        .withEntityId(username)
        .add();
  }

  private UserRecordValue createUser() {
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create()
        .getValue();
  }

  private void addPermissionsToUser(
      final UserRecordValue user,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType) {
    engine
        .authorization()
        .newAuthorization()
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorization)
        .withPermissions(permissionType)
        .withResourceMatcher(WILDCARD.getMatcher())
        .withResourceId(WILDCARD.getResourceId())
        .create(DEFAULT_USER.getUsername());
  }
}
