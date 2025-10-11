/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CreateClusterVariableTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void createGlobalScopedClusterVariable() {
    final var record = ENGINE_RULE.clusterVariables().withName("KEY_1").withValue("VALUE").create();
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATED)
        .hasRecordType(RecordType.EVENT);
  }

  @Test
  public void createTenantScopedClusterVariable() {
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_2")
            .withValue("VALUE")
            .withTenantId("tenant_1")
            .create();
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATED)
        .hasRecordType(RecordType.EVENT);
  }

  @Test
  public void createGlobalScopedClusterVariableAlreadyExists() {
    ENGINE_RULE.clusterVariables().withName("KEY_3").withValue("VALUE").create();
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_3")
            .withValue("VALUE_2")
            .expectRejection()
            .create();
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATE)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason("The variable already exists in this scope");
  }

  @Test
  public void clusterVariableContainsIllegalCharacter() {
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY-1")
            .withValue("VALUE")
            .expectRejection()
            .create();
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Invalid Camunda variable name: 'KEY-1'. The name must not start with a digit, contain whitespace, or use any of the following characters: +-*/=><?.. Additionally, variable names cannot be any of the reserved keywords or literals: [null, true, false, function, if, then, else, for, return, between, instance, of, not, in, and, or, some, every, satisfies].");
  }

  @Test
  public void clusterVariableIsTooLarge() {
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_4")
            .withValue("Value-".repeat(3000))
            .expectRejection()
            .create();
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Invalid Camunda variable value. The variable has a size of 18003 but the max size is 16384");
  }

  @Test
  public void globalScopedAndTenantScopedClusterVariableDoNotOverlap() {
    final var recordGlobal =
        ENGINE_RULE.clusterVariables().withName("KEY_5").withValue("VALUE").create();
    final var recordTenant =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_5")
            .withValue("VALUE")
            .withTenantId("tenant-1")
            .create();
    Assertions.assertThat(recordGlobal)
        .hasIntent(ClusterVariableIntent.CREATED)
        .hasRecordType(RecordType.EVENT);
    Assertions.assertThat(recordTenant)
        .hasIntent(ClusterVariableIntent.CREATED)
        .hasRecordType(RecordType.EVENT);
  }
}
