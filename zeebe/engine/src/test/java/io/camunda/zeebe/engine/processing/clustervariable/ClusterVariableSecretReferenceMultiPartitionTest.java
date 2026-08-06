/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue.ClusterVariableSecretReferenceValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that secret references, detected once on the origin partition, are carried
 * deterministically to the distributed partitions on the {@link ClusterVariableRecord} value
 * itself, without the receiver re-scanning the value.
 */
public final class ClusterVariableSecretReferenceMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;

  @ClassRule
  public static final EngineRule ENGINE_RULE = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCarrySecretReferencesToDistributedPartitionsOnCreate() {
    // given
    final Record<ClusterVariableRecordValue> origin =
        ENGINE_RULE
            .clusterVariables()
            .withName("secret-create-multi")
            .setGlobalScope()
            .withKind(ClusterVariableKind.SECRET_REFERENCE)
            .withValue(Map.of("auth", "camunda.secrets.token"))
            .create();

    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
        .withDistributionIntent(ClusterVariableIntent.CREATE)
        .await();

    // then the origin partition (1) carries the scanned reference
    assertThat(origin.getValue().getSecretReferences())
        .extracting(
            ClusterVariableSecretReferenceValue::getStoreId,
            ClusterVariableSecretReferenceValue::getSecretReference,
            ClusterVariableSecretReferenceValue::getPath)
        .containsExactly(tuple("default", "token", "/auth"));

    // and every distributed partition's CREATED event carries the identical reference
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      final var created =
          RecordingExporter.clusterVariableRecords()
              .withIntent(ClusterVariableIntent.CREATED)
              .withPartitionId(partitionId)
              .withName("secret-create-multi")
              .getFirst();

      assertThat(created.getValue().getSecretReferences())
          .extracting(
              ClusterVariableSecretReferenceValue::getStoreId,
              ClusterVariableSecretReferenceValue::getSecretReference,
              ClusterVariableSecretReferenceValue::getPath)
          .containsExactly(tuple("default", "token", "/auth"));
    }
  }

  @Test
  public void shouldCarrySecretReferencesToDistributedPartitionsOnUpdate() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("secret-update-multi")
        .setGlobalScope()
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("auth", "camunda.secrets.token"))
        .create();

    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
        .withDistributionIntent(ClusterVariableIntent.CREATE)
        .await();

    // when the value is updated to reference a different secret
    final Record<ClusterVariableRecordValue> updated =
        ENGINE_RULE
            .clusterVariables()
            .withName("secret-update-multi")
            .setGlobalScope()
            .withValue(Map.of("auth", "camunda.secrets.rotated"))
            .update();

    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
        .withDistributionIntent(ClusterVariableIntent.UPDATE)
        .await();

    // then the origin partition (1) carries the newly scanned reference
    assertThat(updated.getValue().getSecretReferences())
        .extracting(
            ClusterVariableSecretReferenceValue::getStoreId,
            ClusterVariableSecretReferenceValue::getSecretReference,
            ClusterVariableSecretReferenceValue::getPath)
        .containsExactly(tuple("default", "rotated", "/auth"));
    assertThat(updated.getValue().getKind()).isEqualTo(ClusterVariableKind.SECRET_REFERENCE);

    // and every distributed partition's UPDATED event carries the identical reference
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      final var updatedOnReceiver =
          RecordingExporter.clusterVariableRecords()
              .withIntent(ClusterVariableIntent.UPDATED)
              .withPartitionId(partitionId)
              .withName("secret-update-multi")
              .getFirst();

      assertThat(updatedOnReceiver.getValue().getSecretReferences())
          .extracting(
              ClusterVariableSecretReferenceValue::getStoreId,
              ClusterVariableSecretReferenceValue::getSecretReference,
              ClusterVariableSecretReferenceValue::getPath)
          .containsExactly(tuple("default", "rotated", "/auth"));
      assertThat(updatedOnReceiver.getValue().getKind())
          .isEqualTo(ClusterVariableKind.SECRET_REFERENCE);
    }
  }
}
