/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.apply;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.chunk;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateCommitVersionTest.versionCommitted;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyMembershipEntry;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * The broker writes the identity commands of a policy update itself, so they are not checked
 * against a user's authorizations, even when authorizations and multi-tenancy checks are enabled.
 */
public final class PolicyUpdateAuthorizationTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withAuthorizationsEnabled(true)
          .withMultiTenancyChecksEnabled(true);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldApplyTheUpdateWithAuthorizationsEnabled() {
    // given
    final var tenantId = "tenant-" + uid();
    final var roleId = "role-" + uid();
    final var mappingRuleId = "rule-" + uid();
    final var update =
        chunk()
            .setTenants(
                List.<TenantRecordValue>of(
                    new TenantRecord().setTenantId(tenantId).setName(tenantId)))
            .setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId(roleId).setName(roleId)))
            .setMappingRules(
                List.<MappingRuleRecordValue>of(
                    new MappingRuleRecord()
                        .setMappingRuleId(mappingRuleId)
                        .setName(mappingRuleId)
                        .setClaimName("claim")
                        .setClaimValue(mappingRuleId)))
            .setMemberships(
                List.<PolicyMembershipEntryValue>of(
                    new PolicyMembershipEntry()
                        .setTargetType(PolicyUpdateEntityType.ROLE)
                        .setTargetId(roleId)
                        .setMemberType(EntityType.MAPPING_RULE)
                        .setMemberId(mappingRuleId),
                    new PolicyMembershipEntry()
                        .setTargetType(PolicyUpdateEntityType.TENANT)
                        .setTargetId(tenantId)
                        .setMemberType(EntityType.ROLE)
                        .setMemberId(roleId)));

    // when
    apply(ENGINE, update);

    // then
    versionCommitted(update.getUpdateId());
    assertThat(
            RecordingExporter.tenantRecords(TenantIntent.CREATED).withTenantId(tenantId).exists())
        .isTrue();
    assertThat(RecordingExporter.roleRecords(RoleIntent.ENTITY_ADDED).withRoleId(roleId).exists())
        .isTrue();
    assertThat(
            RecordingExporter.mappingRuleRecords(MappingRuleIntent.CREATED)
                .withMappingRuleId(mappingRuleId)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.tenantRecords(TenantIntent.ENTITY_ADDED)
                .withTenantId(tenantId)
                .exists())
        .isTrue();
  }
}
