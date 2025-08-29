/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.mappingrule;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MappingRuleDeleteAuthorizationTest {

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg ->
                  cfg.getInitialization()
                      .getDefaultRoles()
                      .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername()))));

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDeleteCorrectMappingRuleWithMappingRuleAuthorization() {
    // given
    final var mappingRule1 = createMappingRule("rule1", "name1", "claim1", "value1");

    final var mappingRule2 = createMappingRule("rule2", "name2", "claim2", "value2");
    final var mappingRule2Id = mappingRule2.getMappingRuleId();

    createMappingRule("rule3", "name3", "claim3", "value3");

    // With this setup of mapping rules, we are assuming here that the internal order of iterating
    // the mapping rules when evaluating authorizations (in
    // AuthorizationCheckBehavior#getPersistedMappingRules) is: rule1, rule2, rule3

    authorizeMappingRuleToDeleteMappingRules(mappingRule1.getMappingRuleId());

    // when
    final var deletedMappingRule =
        engine
            .mappingRule()
            .deleteMappingRule(mappingRule2Id)
            .deleteWithMappingRuleAuth(mappingRule1.getClaimName(), mappingRule1.getClaimValue())
            .getValue();

    // then
    assertThat(deletedMappingRule).isNotNull().hasMappingRuleId(mappingRule2Id);
  }

  private void authorizeMappingRuleToDeleteMappingRules(String mappingRuleId) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.DELETE)
        .withOwnerId(mappingRuleId)
        .withOwnerType(AuthorizationOwnerType.MAPPING_RULE)
        .withResourceType(AuthorizationResourceType.MAPPING_RULE)
        .withResourceMatcher(WILDCARD.getMatcher())
        .withResourceId(WILDCARD.getResourceId())
        .create(DEFAULT_USER.getUsername())
        .getValue();
  }

  private MappingRuleRecordValue createMappingRule(
      String id, String name, String claimName, String claimValue) {
    return engine
        .mappingRule()
        .newMappingRule(id)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create(DEFAULT_USER.getUsername())
        .getValue();
  }
}
