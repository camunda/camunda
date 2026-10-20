/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyDeleteEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

public final class PolicyUpdateApplyTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldApplyUpsertsOfEveryEntityTypeInOneChunk() {
    // given
    final var envelopeId = UUID.randomUUID().toString();

    // when
    engine.writeRecords(
        write(
            chunk(envelopeId, 1, 1, 0)
                .setTenants(
                    List.<TenantRecordValue>of(new TenantRecord().setTenantId("t1").setName("T")))
                .setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId("r1").setName("R")))
                .setGroups(
                    List.<GroupRecordValue>of(new GroupRecord().setGroupId("g1").setName("G")))
                .setMappingRules(
                    List.<MappingRuleRecordValue>of(
                        new MappingRuleRecord()
                            .setMappingRuleId("m1")
                            .setName("M")
                            .setClaimName("c")
                            .setClaimValue("v")))));

    // then
    assertThat(created(ValueType.TENANT, TenantIntent.CREATED)).isNotNull();
    assertThat(created(ValueType.ROLE, RoleIntent.CREATED)).isNotNull();
    assertThat(created(ValueType.GROUP, GroupIntent.CREATED)).isNotNull();
    assertThat(created(ValueType.MAPPING_RULE, MappingRuleIntent.CREATED)).isNotNull();
    assertThat(appliedLast(1)).isNotNull();
  }

  @Test
  public void shouldUpdateExistingRoleKeepingItsKey() {
    // given
    final var first = UUID.randomUUID().toString();
    engine.writeRecords(
        write(
            chunk(first, 1, 1, 0)
                .setRoles(
                    List.<RoleRecordValue>of(new RoleRecord().setRoleId("r1").setName("A")))));
    final Record<?> created = created(ValueType.ROLE, RoleIntent.CREATED);
    appliedLast(1);

    // when
    final var second = UUID.randomUUID().toString();
    engine.writeRecords(
        write(
            chunk(second, 2, 1, 0)
                .setRoles(
                    List.<RoleRecordValue>of(new RoleRecord().setRoleId("r1").setName("B")))));
    appliedLast(2);

    // then
    final Record<?> updated =
        RecordingExporter.records()
            .withValueType(ValueType.ROLE)
            .withIntent(RoleIntent.UPDATED)
            .getFirst();
    assertThat(updated.getKey()).isEqualTo(created.getKey());
  }

  @Test
  public void shouldApplyChunksInOrderWithOneAppliedEventPerChunk() {
    // given
    final var envelopeId = UUID.randomUUID().toString();

    // when
    for (int index = 0; index < 3; index++) {
      engine.writeRecords(
          write(
              chunk(envelopeId, 1, 3, index)
                  .setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId("r" + index)))));
    }

    // then
    final List<Integer> applied =
        RecordingExporter.records()
            .withValueType(ValueType.POLICY_UPDATE)
            .withIntent(PolicyUpdateIntent.APPLIED)
            .limit(3)
            .map(record -> ((PolicyUpdateRecordValue) record.getValue()).getChunkIndex())
            .toList();
    assertThat(applied).containsExactly(0, 1, 2);
    assertThat(
            RecordingExporter.records()
                .withValueType(ValueType.ROLE)
                .withIntent(RoleIntent.CREATED)
                .limit(3)
                .count())
        .isEqualTo(3);
  }

  @Test
  public void shouldDeleteEntityOfNewerVersion() {
    // given
    engine.writeRecords(
        write(
            chunk(UUID.randomUUID().toString(), 1, 1, 0)
                .setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId("r1")))));
    final Record<?> created = created(ValueType.ROLE, RoleIntent.CREATED);
    appliedLast(1);

    // when
    engine.writeRecords(
        write(
            chunk(UUID.randomUUID().toString(), 2, 1, 0)
                .setDeletes(
                    List.<PolicyDeleteEntryValue>of(
                        new PolicyDeleteEntry()
                            .setEntityType(PolicyUpdateEntityType.ROLE)
                            .setId("r1")))));
    appliedLast(2);

    // then
    final Record<?> deleted =
        RecordingExporter.records()
            .withValueType(ValueType.ROLE)
            .withIntent(RoleIntent.DELETED)
            .getFirst();
    assertThat(deleted.getKey()).isEqualTo(created.getKey());
  }

  private static PolicyUpdateRecord chunk(
      final String envelopeId, final long version, final int count, final int index) {
    return new PolicyUpdateRecord()
        .setEnvelopeId(envelopeId)
        .setPolicyVersion(version)
        .setChunkCount(count)
        .setChunkIndex(index);
  }

  private static RecordToWrite write(final PolicyUpdateRecord chunk) {
    return RecordToWrite.command().policyUpdate(PolicyUpdateIntent.APPEND_CHUNK, chunk);
  }

  private static Record<?> created(
      final ValueType valueType, final io.camunda.zeebe.protocol.record.intent.Intent intent) {
    return RecordingExporter.records().withValueType(valueType).withIntent(intent).getFirst();
  }

  private static Record<?> appliedLast(final long version) {
    return RecordingExporter.records()
        .withValueType(ValueType.POLICY_UPDATE)
        .withIntent(PolicyUpdateIntent.APPLIED)
        .filter(
            record -> {
              final var value = (PolicyUpdateRecordValue) record.getValue();
              return value.isLastPage() && value.getPolicyVersion() == version;
            })
        .getFirst();
  }
}
