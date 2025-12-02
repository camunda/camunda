/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class TenantAwareClusterVariableTest {

  private static final String USERNAME = UUID.randomUUID().toString();
  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          USERNAME,
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withSecurityConfig(config -> config.getMultiTenancy().setChecksEnabled(true))
          .withSecurityConfig(config -> config.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg ->
                  cfg.getInitialization()
                      .getDefaultRoles()
                      .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername()))));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void setupTenants() {
    ENGINE.tenant().newTenant().withTenantId(TENANT_A).create();
    ENGINE.tenant().newTenant().withTenantId(TENANT_B).create();
    ENGINE
        .tenant()
        .addEntity(TENANT_A)
        .withEntityId(USERNAME)
        .withEntityType(EntityType.USER)
        .add();
    ENGINE
        .tenant()
        .addEntity(TENANT_B)
        .withEntityId(USERNAME)
        .withEntityType(EntityType.USER)
        .add();
  }

  @Test
  public void shouldCreateTenantScopedClusterVariableForTenantA() {
    final String varName = "tenantAClusterVar-" + UUID.randomUUID();
    final String varValue = "\"tenantAValue-" + UUID.randomUUID() + "\"";
    // when
    final var record =
        ENGINE
            .clusterVariables()
            .withName(varName)
            .setTenantScope()
            .withTenantId(TENANT_A)
            .withValue(varValue)
            .create(USERNAME);

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATED)
        .hasRecordType(RecordType.EVENT);
    Assertions.assertThat(record.getValue())
        .hasName(varName)
        .hasValue(varValue)
        .hasTenantId(TENANT_A);
  }

  @Test
  public void shouldCreateTenantScopedClusterVariableForTenantB() {
    final String varName = "tenantBClusterVar-" + UUID.randomUUID();
    final String varValue = "\"tenantBValue-" + UUID.randomUUID() + "\"";
    // when
    final var record =
        ENGINE
            .clusterVariables()
            .withName(varName)
            .setTenantScope()
            .withTenantId(TENANT_B)
            .withValue(varValue)
            .create(USERNAME);

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATED)
        .hasRecordType(RecordType.EVENT);
    Assertions.assertThat(record.getValue())
        .hasName(varName)
        .hasValue(varValue)
        .hasTenantId(TENANT_B);
  }

  @Test
  public void shouldRejectDuplicateTenantScopedClusterVariable() {
    final String varName = "tenantAClusterVar-" + UUID.randomUUID();
    final String varValue = "\"tenantAValue-" + UUID.randomUUID() + "\"";
    // given
    ENGINE
        .clusterVariables()
        .withName(varName)
        .setTenantScope()
        .withTenantId(TENANT_A)
        .withValue(varValue)
        .create(USERNAME);

    // when
    final var record =
        ENGINE
            .clusterVariables()
            .withName(varName)
            .setTenantScope()
            .withTenantId(TENANT_A)
            .withValue("\"newValue-" + UUID.randomUUID() + "\"")
            .expectRejection()
            .create(USERNAME);

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATE)
        .hasRejectionType(RejectionType.ALREADY_EXISTS);
  }

  @Test
  public void shouldAllowSameNameForDifferentTenants() {
    // given
    final String varName = "sharedName-" + UUID.randomUUID();
    final String valA = "\"valueA-" + UUID.randomUUID() + "\"";
    final String valB = "\"valueB-" + UUID.randomUUID() + "\"";

    // when
    final var recordA =
        ENGINE
            .clusterVariables()
            .withName(varName)
            .setTenantScope()
            .withTenantId(TENANT_A)
            .withValue(valA)
            .create(USERNAME);

    final var recordB =
        ENGINE
            .clusterVariables()
            .withName(varName)
            .setTenantScope()
            .withTenantId(TENANT_B)
            .withValue(valB)
            .create(USERNAME);

    // then
    Assertions.assertThat(recordA).hasIntent(ClusterVariableIntent.CREATED);
    Assertions.assertThat(recordA.getValue()).hasTenantId(TENANT_A).hasName(varName).hasValue(valA);

    Assertions.assertThat(recordB).hasIntent(ClusterVariableIntent.CREATED);
    Assertions.assertThat(recordB.getValue()).hasTenantId(TENANT_B).hasName(varName).hasValue(valB);
  }

  @Test
  public void shouldDeleteTenantScopedClusterVariable() {
    final String varName = "tenantAClusterVar-" + UUID.randomUUID();
    final String varValue = "\"tenantAValue-" + UUID.randomUUID() + "\"";
    // given
    ENGINE
        .clusterVariables()
        .withName(varName)
        .setTenantScope()
        .withTenantId(TENANT_A)
        .withValue(varValue)
        .create(USERNAME);

    // when
    final var record =
        ENGINE
            .clusterVariables()
            .withName(varName)
            .setTenantScope()
            .withTenantId(TENANT_A)
            .delete(USERNAME);

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.DELETED)
        .hasRecordType(RecordType.EVENT);
    Assertions.assertThat(record.getValue()).hasName(varName).hasTenantId(TENANT_A);
  }

  @Test
  public void shouldRejectDeleteOfNonExistentTenantScopedClusterVariable() {
    // when
    final var record =
        ENGINE
            .clusterVariables()
            .withName("nonExistent")
            .setTenantScope()
            .withTenantId(TENANT_A)
            .expectRejection()
            .delete(USERNAME);

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.DELETE)
        .hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldCreateClusterVariableForDefaultTenant() {
    final String varName = "defaultTenantVar-" + UUID.randomUUID();
    final String varValue = "\"defaultValue-" + UUID.randomUUID() + "\"";
    // when
    final var record =
        ENGINE
            .clusterVariables()
            .withName(varName)
            .setTenantScope()
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .withValue(varValue)
            .create(USERNAME);

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATED)
        .hasRecordType(RecordType.EVENT);
    Assertions.assertThat(record.getValue()).hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldAllowGlobalAndTenantScopedVariablesWithSameName() {
    // given
    final String sharedName = "mixedScopeVar-" + UUID.randomUUID();
    final String globalValue = "\"globalVal-" + UUID.randomUUID() + "\"";
    final String tenantValue = "\"tenantVal-" + UUID.randomUUID() + "\"";

    // when
    final var globalRecord =
        ENGINE
            .clusterVariables()
            .withName(sharedName)
            .setGlobalScope()
            .withValue(globalValue)
            .create(USERNAME);

    final var tenantRecord =
        ENGINE
            .clusterVariables()
            .withName(sharedName)
            .setTenantScope()
            .withTenantId(TENANT_A)
            .withValue(tenantValue)
            .create(USERNAME);

    // then
    Assertions.assertThat(globalRecord).hasIntent(ClusterVariableIntent.CREATED);
    Assertions.assertThat(globalRecord.getValue()).hasName(sharedName).hasValue(globalValue);

    Assertions.assertThat(tenantRecord).hasIntent(ClusterVariableIntent.CREATED);
    Assertions.assertThat(tenantRecord.getValue())
        .hasName(sharedName)
        .hasValue(tenantValue)
        .hasTenantId(TENANT_A);
  }

  @Test
  public void shouldRejectTenantScopedVariableWithoutTenantId() {
    // when
    final var record =
        ENGINE
            .clusterVariables()
            .withName("noTenantVar")
            .setTenantScope()
            .withValue("\"value\"")
            .expectRejection()
            .create(USERNAME);

    // then
    assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Invalid cluster variable scope. Tenant-scoped variables must have a non-blank tenant ID.");
  }

  @Test
  public void shouldRejectVariableWithoutScope() {
    // when
    final var record =
        ENGINE
            .clusterVariables()
            .withName("noScopeVar")
            .withValue("\"value\"")
            .expectRejection()
            .create(USERNAME);

    // then
    assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Invalid cluster variable scope. The scope must be either 'GLOBAL' or 'TENANT'.");
  }

  @Test
  public void shouldIsolateTenantVariables() {
    final String varName = "isolatedVar-" + UUID.randomUUID();
    final String varValue = "\"tenantAValue-" + UUID.randomUUID() + "\"";
    // given
    ENGINE
        .clusterVariables()
        .withName(varName)
        .setTenantScope()
        .withTenantId(TENANT_A)
        .withValue(varValue)
        .create(USERNAME);

    // when - try to delete with wrong tenant id
    final var record =
        ENGINE
            .clusterVariables()
            .withName(varName)
            .setTenantScope()
            .withTenantId(TENANT_B)
            .expectRejection()
            .delete(USERNAME);

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.DELETE)
        .hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldNotConfuseGlobalWithTenantScoped() {
    // given
    final String varName = "confusableVar-" + UUID.randomUUID();
    final String varValue = "\"globalValue-" + UUID.randomUUID() + "\"";
    ENGINE
        .clusterVariables()
        .withName(varName)
        .setGlobalScope()
        .withValue(varValue)
        .create(USERNAME);

    // when - try to delete as tenant-scoped
    final var record =
        ENGINE
            .clusterVariables()
            .withName(varName)
            .setTenantScope()
            .withTenantId(TENANT_A)
            .expectRejection()
            .delete(USERNAME);

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.DELETE)
        .hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldCreateMultipleTenantVariablesInParallel() {
    final String var1Name = "var1-" + UUID.randomUUID();
    final String var2Name = "var2-" + UUID.randomUUID();
    final String value1A = "\"value1A-" + UUID.randomUUID() + "\"";
    final String value1B = "\"value1B-" + UUID.randomUUID() + "\"";
    final String value2A = "\"value2A-" + UUID.randomUUID() + "\"";
    final String value2B = "\"value2B-" + UUID.randomUUID() + "\"";
    // when
    final var recordA1 =
        ENGINE
            .clusterVariables()
            .withName(var1Name)
            .setTenantScope()
            .withTenantId(TENANT_A)
            .withValue(value1A)
            .create(USERNAME);

    final var recordB1 =
        ENGINE
            .clusterVariables()
            .withName(var1Name)
            .setTenantScope()
            .withTenantId(TENANT_B)
            .withValue(value1B)
            .create(USERNAME);

    final var recordA2 =
        ENGINE
            .clusterVariables()
            .withName(var2Name)
            .setTenantScope()
            .withTenantId(TENANT_A)
            .withValue(value2A)
            .create(USERNAME);

    final var recordB2 =
        ENGINE
            .clusterVariables()
            .withName(var2Name)
            .setTenantScope()
            .withTenantId(TENANT_B)
            .withValue(value2B)
            .create(USERNAME);

    // then - all should succeed
    Assertions.assertThat(recordA1).hasIntent(ClusterVariableIntent.CREATED);
    Assertions.assertThat(recordB1).hasIntent(ClusterVariableIntent.CREATED);
    Assertions.assertThat(recordA2).hasIntent(ClusterVariableIntent.CREATED);
    Assertions.assertThat(recordB2).hasIntent(ClusterVariableIntent.CREATED);

    Assertions.assertThat(recordA1.getValue()).hasTenantId(TENANT_A);
    Assertions.assertThat(recordB1.getValue()).hasTenantId(TENANT_B);
    Assertions.assertThat(recordA2.getValue()).hasTenantId(TENANT_A);
    Assertions.assertThat(recordB2.getValue()).hasTenantId(TENANT_B);
  }

  @Test
  public void shouldPreventTenantFromAccessingOtherTenantVariables() {
    final String varNameA = "tenantAClusterVar-" + UUID.randomUUID();
    final String varNameB = "tenantBClusterVar-" + UUID.randomUUID();
    final String valueA = "\"tenantAValue-" + UUID.randomUUID() + "\"";
    final String valueB = "\"tenantBValue-" + UUID.randomUUID() + "\"";
    // given
    ENGINE
        .clusterVariables()
        .withName(varNameA)
        .setTenantScope()
        .withTenantId(TENANT_A)
        .withValue(valueA)
        .create(USERNAME);

    ENGINE
        .clusterVariables()
        .withName(varNameB)
        .setTenantScope()
        .withTenantId(TENANT_B)
        .withValue(valueB)
        .create(USERNAME);

    // when - try to delete Tenant A variable using Tenant B ID
    final var recordA =
        ENGINE
            .clusterVariables()
            .withName(varNameA)
            .setTenantScope()
            .withTenantId(TENANT_B)
            .expectRejection()
            .delete(USERNAME);

    // when - try to delete Tenant B variable using Tenant A ID
    final var recordB =
        ENGINE
            .clusterVariables()
            .withName(varNameB)
            .setTenantScope()
            .withTenantId(TENANT_A)
            .expectRejection()
            .delete(USERNAME);

    // then - both should be rejected
    Assertions.assertThat(recordA).hasRejectionType(RejectionType.NOT_FOUND);
    Assertions.assertThat(recordB).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldStoreDifferentValuesForSameVariableNameInDifferentTenants() {
    // given
    final String sharedVarName = "sharedVariable-" + UUID.randomUUID();
    final String valueA = "\"valueForTenantA-" + UUID.randomUUID() + "\"";
    final String valueB = "\"valueForTenantB-" + UUID.randomUUID() + "\"";

    // when - create same named variables with different values for different tenants
    final var recordA =
        ENGINE
            .clusterVariables()
            .withName(sharedVarName)
            .setTenantScope()
            .withTenantId(TENANT_A)
            .withValue(valueA)
            .create(USERNAME);

    final var recordB =
        ENGINE
            .clusterVariables()
            .withName(sharedVarName)
            .setTenantScope()
            .withTenantId(TENANT_B)
            .withValue(valueB)
            .create(USERNAME);

    // then - verify different values are stored
    Assertions.assertThat(recordA.getValue()).hasValue(valueA);
    Assertions.assertThat(recordB.getValue()).hasValue(valueB);
  }
}
