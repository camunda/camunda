/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class UpdateClusterVariableTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void updateGlobalScopedClusterVariable() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_1")
        .setGlobalScope()
        .withValue("\"VALUE\"")
        .create();

    // when
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_1")
            .setGlobalScope()
            .withValue("\"UPDATED_VALUE\"")
            .update();

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.UPDATED)
        .hasRecordType(RecordType.EVENT);
    Assertions.assertThat(record.getValue()).hasValue("\"UPDATED_VALUE\"");
  }

  @Test
  public void updateTenantScopedClusterVariable() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_2")
        .setTenantScope()
        .withValue("\"VALUE\"")
        .withTenantId("tenant_1")
        .create();

    // when
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_2")
            .setTenantScope()
            .withTenantId("tenant_1")
            .withValue("\"UPDATED_VALUE\"")
            .update();

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.UPDATED)
        .hasRecordType(RecordType.EVENT);
    Assertions.assertThat(record.getValue()).hasValue("\"UPDATED_VALUE\"");
  }

  @Test
  public void updateWithoutScopeTenantScopedClusterVariable() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_TO_UPDATE_1")
        .setTenantScope()
        .withValue("\"VALUE\"")
        .withTenantId("tenant_1")
        .create();

    // when
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_TO_UPDATE_1")
            .withValue("\"UPDATED_VALUE\"")
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.UPDATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Invalid cluster variable scope. The scope must be either 'GLOBAL' or 'TENANT'.");
  }

  @Test
  public void updateWithoutScopeGloballyScopedClusterVariable() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_TO_UPDATE_2")
        .setGlobalScope()
        .withTenantId("tenant_1")
        .withValue("\"VALUE\"")
        .create();

    // when
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_TO_UPDATE_2")
            .withValue("\"UPDATED_VALUE\"")
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.UPDATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Invalid cluster variable scope. The scope must be either 'GLOBAL' or 'TENANT'.");
  }

  @Test
  public void updateNonExistingGlobalScopedClusterVariable() {
    // when
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_3")
            .setGlobalScope()
            .withValue("\"UPDATED_VALUE\"")
            .expectRejection()
            .update();
    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.UPDATE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Invalid cluster variable name: 'KEY_3'. The variable does not exist in the scope 'GLOBAL'");
  }

  @Test
  public void updateTenantScopedClusterVariableWithoutTenantId() {
    // when
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_3")
            .setTenantScope()
            .withValue("\"UPDATED_VALUE\"")
            .expectRejection()
            .update();
    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.UPDATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Invalid cluster variable scope. Tenant-scoped variables must have a non-blank tenant ID.");
  }

  @Test
  public void updateNonExistingTenantScopedClusterVariable() {
    // when
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_3")
            .setTenantScope()
            .withTenantId("tenant_1")
            .withValue("\"UPDATED_VALUE\"")
            .expectRejection()
            .update();
    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.UPDATE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Invalid cluster variable name: 'KEY_3'. The variable does not exist in the scope 'tenant: 'tenant_1''");
  }

  @Test
  public void globalScopedAndTenantScopedClusterVariableDoNotOverlapForUpdate() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_4")
        .setGlobalScope()
        .withValue("\"VALUE\"")
        .create();

    // when
    final var recordTenant =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_4")
            .setTenantScope()
            .withTenantId("tenant-1")
            .withValue("\"UPDATED_VALUE\"")
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(recordTenant)
        .hasIntent(ClusterVariableIntent.UPDATE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Invalid cluster variable name: 'KEY_4'. The variable does not exist in the scope 'tenant: 'tenant-1''");
  }

  @Test
  public void tenantScopedAndGlobalScopedClusterVariableDoNotOverlapForUpdate() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_5")
        .setTenantScope()
        .withValue("\"VALUE\"")
        .withTenantId("tenant-1")
        .create();

    // when
    final var recordTenant =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_5")
            .setGlobalScope()
            .withValue("\"UPDATED_VALUE\"")
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(recordTenant)
        .hasIntent(ClusterVariableIntent.UPDATE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Invalid cluster variable name: 'KEY_5'. The variable does not exist in the scope 'GLOBAL'");
  }

  @Test
  public void updateClusterVariableMultipleTimes() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_6")
        .setGlobalScope()
        .withValue("\"VALUE_1\"")
        .create();

    // when - first update
    final var record1 =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_6")
            .setGlobalScope()
            .withValue("\"VALUE_2\"")
            .update();

    // then
    Assertions.assertThat(record1)
        .hasIntent(ClusterVariableIntent.UPDATED)
        .hasRecordType(RecordType.EVENT);
    Assertions.assertThat(record1.getValue()).hasValue("\"VALUE_2\"");

    // when - second update
    final var record2 =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_6")
            .setGlobalScope()
            .withValue("\"VALUE_3\"")
            .update();

    // then
    Assertions.assertThat(record2)
        .hasIntent(ClusterVariableIntent.UPDATED)
        .hasRecordType(RecordType.EVENT);
    Assertions.assertThat(record2.getValue()).hasValue("\"VALUE_3\"");
  }
}
