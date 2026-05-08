/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition.processors;

import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.systempartition.state.ClusterConfigurationState;

/**
 * Factory that registers the cluster-configuration record processors into a {@link
 * TypedRecordProcessors} bundle.
 *
 * <p>Wires up the three command processors ({@link ClusterConfigurationStampProcessor}, {@link
 * ClusterConfigurationApplyOperationProcessor}, {@link
 * ClusterConfigurationCompleteChangeProcessor}). The companion {@link
 * ClusterConfigurationStateApplier} must be registered with the engine's {@code EventApplier}
 * pipeline separately — that wiring lives in the broker bootstrap (Phase 3).
 */
public final class ClusterConfigurationProcessors {

  private ClusterConfigurationProcessors() {}

  public static void register(
      final TypedRecordProcessors processors,
      final ClusterConfigurationState state,
      final Writers writers,
      final KeyGenerator keys,
      final ConfigurationChangeAppliers appliers) {
    processors.onCommand(
        ValueType.CLUSTER_CONFIGURATION,
        ClusterConfigurationIntent.STAMP_CHANGE_PLAN,
        new ClusterConfigurationStampProcessor(state, writers, keys));
    processors.onCommand(
        ValueType.CLUSTER_CONFIGURATION,
        ClusterConfigurationIntent.APPLY_OPERATION,
        new ClusterConfigurationApplyOperationProcessor(state, writers, keys, appliers));
    processors.onCommand(
        ValueType.CLUSTER_CONFIGURATION,
        ClusterConfigurationIntent.COMPLETE_CHANGE,
        new ClusterConfigurationCompleteChangeProcessor(state, writers, keys));
  }
}
