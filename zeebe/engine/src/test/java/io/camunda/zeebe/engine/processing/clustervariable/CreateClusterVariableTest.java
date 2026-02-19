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

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
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
    // when
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_1")
            .setGlobalScope()
            .withValue("\"VALUE\"")
            .create();

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATED)
        .hasRecordType(RecordType.EVENT);
  }

  @Test
  public void createTenantScopedClusterVariable() {
    // when
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_2")
            .withValue("\"VALUE\"")
            .setTenantScope()
            .withTenantId("tenant_1")
            .create();
    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATED)
        .hasRecordType(RecordType.EVENT);
  }

  @Test
  public void createClusterVariableWithoutScope() {
    // when
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_1")
            .withValue("\"VALUE\"")
            .expectRejection()
            .create();

    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Invalid cluster variable scope. The scope must be either 'GLOBAL' or 'TENANT'.");
  }

  @Test
  public void createGlobalScopedClusterVariableAlreadyExists() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_3")
        .setGlobalScope()
        .withValue("\"VALUE\"")
        .create();
    // when
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_3")
            .setGlobalScope()
            .withValue("\"VALUE_2\"")
            .expectRejection()
            .create();
    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATE)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Invalid cluster variable name: 'KEY_3'. The name already exists in the scope 'GLOBAL'");
  }

  @Test
  public void createTenantScopedClusterVariableWithoutTenant() {
    // when
    final var record =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_3")
            .setTenantScope()
            .withValue("\"VALUE\"")
            .expectRejection()
            .create();
    // then
    Assertions.assertThat(record)
        .hasIntent(ClusterVariableIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Invalid cluster variable scope. Tenant-scoped variables must have a non-blank tenant ID.");
  }

  @Test
  public void globalScopedAndTenantScopedClusterVariableDoNotOverlap() {
    // given
    final var recordGlobal =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_5")
            .setGlobalScope()
            .withValue("\"VALUE\"")
            .create();
    // when
    final var recordTenant =
        ENGINE_RULE
            .clusterVariables()
            .withName("KEY_5")
            .setTenantScope()
            .withValue("\"VALUE\"")
            .withTenantId("tenant-1")
            .create();
    // then
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

    final var engineConfiguration = new EngineConfiguration();
    engineConfiguration.setMaxNameFieldLength(10);

    // given
    final ClusterVariableRecord clusterVariableRecord =
        new ClusterVariableRecord().setName(clusterVariableName);
    final ClusterVariableState clusterVariableState = mock(ClusterVariableState.class);
    final ClusterVariableRecordValidator clusterVariableRecordValidator =
        new ClusterVariableRecordValidator(clusterVariableState, engineConfiguration);
    // when
    final var result = clusterVariableRecordValidator.validateName(clusterVariableRecord);
    // then
    assertThat(result.getLeft().reason()).isEqualTo(rejectionReason);
  }

  static Stream<Arguments> retrieveInvalidClusterVariableName() {
    return Stream.of(
        Arguments.of(
            "test key",
            "Invalid cluster variable name: 'test key'. The name must not contains any whitespace."),
        Arguments.of(
            "", "Invalid cluster variable name: ''. Cluster variable can not be null or empty."),
        Arguments.of(
            "this-is-a-very-long-cluster-variable-name-that-exceeds-the-maximum-length",
            "Invalid cluster variable name: 'this-is-a-very-long-cluster-variable-name-that-exceeds-the-maximum-length'. The name must not be longer than 10 characters."));
  }
}
