/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clusterversion;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public final class ClusterVersionProcessors {
  private ClusterVersionProcessors() {}

  public static void addClusterVersionProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final ProcessingState state,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ClusterVersionUpdateListener updateListener) {
    typedRecordProcessors.onCommand(
        ValueType.CLUSTER_VERSION,
        ClusterVersionIntent.RAISE,
        new RaiseClusterVersionProcessor(state, writers, keyGenerator, updateListener));
    typedRecordProcessors.onCommand(
        ValueType.CLUSTER_VERSION,
        ClusterVersionIntent.PING,
        new PingProcessor(state, writers, keyGenerator));
    typedRecordProcessors.onCommand(
        ValueType.CLUSTER_VERSION,
        ClusterVersionIntent.ECHO,
        new EchoProcessor(writers, keyGenerator));
    typedRecordProcessors.onCommand(
        ValueType.CLUSTER_VERSION,
        ClusterVersionIntent.SUPPRESS_FLAG,
        new SuppressFlagProcessor(writers, keyGenerator));
    typedRecordProcessors.onCommand(
        ValueType.CLUSTER_VERSION,
        ClusterVersionIntent.UNSUPPRESS_FLAG,
        new UnsuppressFlagProcessor(writers, keyGenerator));
    // Seed: after the stream processor recovers, push the active ECV from state to the listener so
    // the broker's admission layer starts with the right value.
    typedRecordProcessors.withListener(
        new ClusterVersionSeedLifecycleListener(state.getClusterVersionState(), updateListener));
  }
}
