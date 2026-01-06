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

public final class DeleteClusterVariableTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void deleteGlobalScopedClusterVariable() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_1")
        .setGlobalScope()
        .withValue("\"VALUE\"")
        .create();

    // when
    final var record = ENGINE_RULE.clusterVariables().withName("KEY_1").setGlobalScope().delete();

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.DELETED)
        .hasRecordType(RecordType.EVENT);
  }

  @Test
  public void deleteTenantScopedClusterVariable() {
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
            .delete();

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.DELETED)
        .hasRecordType(RecordType.EVENT);
  }

  @Test
  public void deleteWithoutScopeTenantScopedClusterVariable() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_TO_DELETE_1")
        .setTenantScope()
        .withValue("\"VALUE\"")
        .withTenantId("tenant_1")
        .create();

    // when
    final var record =
        ENGINE_RULE.clusterVariables().withName("KEY_TO_DELETE_1").expectRejection().delete();

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.DELETE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Invalid cluster variable scope. The scope must be either 'GLOBAL' or 'TENANT'.");
  }

  @Test
  public void deleteWithoutScopeGloballyScopedClusterVariable() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_TO_DELETE_2")
        .setTenantScope()
        .withValue("\"VALUE\"")
        .create();

    // when
    final var record =
        ENGINE_RULE.clusterVariables().withName("KEY_TO_DELETE_2").expectRejection().delete();

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.DELETE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Invalid cluster variable scope. The scope must be either 'GLOBAL' or 'TENANT'.");
  }

  @Test
  public void deleteNonExistingGlobalScopedClusterVariable() {
    // when
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_3")
            .setGlobalScope()
            .expectRejection()
            .delete();
    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.DELETE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Invalid cluster variable name: 'KEY_3'. The variable does not exist in the scope 'GLOBAL'");
  }

  @Test
  public void deleteNonExistingTenantScopedClusterVariable() {
    // when
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_3")
            .setTenantScope()
            .withTenantId("tenant_1")
            .expectRejection()
            .delete();
    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.DELETE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Invalid cluster variable name: 'KEY_3'. The variable does not exist in the scope 'tenant: 'tenant_1''");
  }

  @Test
  public void globalScopedAndTenantScopedClusterVariableDoNotOverlapForDeletion() {
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
            .expectRejection()
            .delete();
    // then

    Assertions.assertThat(recordTenant)
        .hasIntent(ClusterVariableIntent.DELETE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Invalid cluster variable name: 'KEY_4'. The variable does not exist in the scope 'tenant: 'tenant-1''");
  }

  @Test
  public void tenantScopedAndGlobalScopedClusterVariableDoNotOverlapForDeletion() {
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
            .expectRejection()
            .delete();
    // then

    Assertions.assertThat(recordTenant)
        .hasIntent(ClusterVariableIntent.DELETE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Invalid cluster variable name: 'KEY_5'. The variable does not exist in the scope 'GLOBAL'");
  }
}
