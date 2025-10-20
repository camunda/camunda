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
    ENGINE_RULE.clusterVariables().withName("KEY_1").withValue("\"VALUE\"").create();

    // when
    final var record = ENGINE_RULE.clusterVariables().withName("KEY_1").delete();

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.DELETED)
        .hasRecordType(RecordType.EVENT);
  }

  @Test
  public void createTenantScopedClusterVariable() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_2")
        .withValue("\"VALUE\"")
        .withTenantId("tenant_1")
        .create();

    // when
    final var record =
        ENGINE_RULE.clusterVariables().withName("KEY_2").withTenantId("tenant_1").delete();

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.DELETED)
        .hasRecordType(RecordType.EVENT);
  }

  @Test
  public void deleteNonExistingGlobalScopedClusterVariable() {
    // when
    final var record = ENGINE_RULE.clusterVariables().withName("KEY_3").expectRejection().delete();
    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.DELETE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Invalid cluster variable name: 'KEY_3'. The variable does not exist in the scope 'GLOBAL'");
  }

  @Test
  public void globalScopedAndTenantScopedClusterVariableDoNotOverlapForDeletion() {
    // given

    ENGINE_RULE.clusterVariables().withName("KEY_4").withValue("\"VALUE\"").create();
    // when
    final var recordTenant =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_4")
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
}
