/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.variable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.stream.Stream;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
        .hasRejectionReason(
            "Invalid cluster variable name: 'KEY_3'. The name already exists in the scope 'GLOBAL'");
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

  @ParameterizedTest
  @MethodSource("retrieveInvalidClusterVariableName")
  public void checkClusterVariableRecordValidator(
      final String clusterVariableName, final String rejectionReason) {
    final ClusterVariableRecord clusterVariableRecord =
        new ClusterVariableRecord().setName(clusterVariableName);
    final ClusterVariableState clusterVariableState = mock(ClusterVariableState.class);
    final ClusterVariableRecordValidator clusterVariableRecordValidator =
        new ClusterVariableRecordValidator(clusterVariableState);
    final var result = clusterVariableRecordValidator.validateName(clusterVariableRecord);
    assertThat(result.getLeft().reason()).isEqualTo(rejectionReason);
  }

  static Stream<Arguments> retrieveInvalidClusterVariableName() {
    return Stream.of(
        Arguments.of(
            "test key",
            "Invalid cluster variable name: 'test key'. The name must not contains any whitespace."),
        Arguments.of(
            "", "Invalid cluster variable name: ''. Cluster variable can not be null or empty."));
  }
}
