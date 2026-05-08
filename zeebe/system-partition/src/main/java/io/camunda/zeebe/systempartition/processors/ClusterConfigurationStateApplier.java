/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition.processors;

import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.systempartition.state.ClusterConfigurationState;

/**
 * Deterministic state applier for {@code CLUSTER_CONFIGURATION} events.
 *
 * <p>Decodes the proto-encoded {@link io.camunda.zeebe.dynamic.config.state.ClusterConfiguration}
 * carried by the event and writes it to the {@link ClusterConfigurationState} column family. Reject
 * events (which carry no configuration payload) are ignored.
 *
 * <p>The applier is invoked by the engine's {@link io.camunda.zeebe.engine.state.EventApplier}
 * pipeline whenever an event with {@link ClusterConfigurationIntent#CHANGE_PLAN_STAMPED}, {@link
 * ClusterConfigurationIntent#OPERATION_APPLIED}, or {@link
 * ClusterConfigurationIntent#CHANGE_COMPLETED} is replayed or freshly written.
 */
public final class ClusterConfigurationStateApplier
    implements TypedEventApplier<ClusterConfigurationIntent, ClusterConfigurationRecord> {

  private final ClusterConfigurationState state;
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  public ClusterConfigurationStateApplier(final ClusterConfigurationState state) {
    this.state = state;
  }

  @Override
  public void applyState(final long key, final ClusterConfigurationRecord value) {
    final byte[] encoded = value.getConfiguration();
    if (encoded == null || encoded.length == 0) {
      // REJECT events carry no configuration; nothing to apply.
      return;
    }
    state.put(serializer.decodeClusterTopology(encoded, 0, encoded.length));
  }
}
