/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableClusterVariableState;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;

public class ClusterVariableDeletedApplier
    implements TypedEventApplier<ClusterVariableIntent, ClusterVariableRecord> {

  private final MutableClusterVariableState clusterVariableState;

  public ClusterVariableDeletedApplier(final MutableClusterVariableState clusterVariableState) {
    this.clusterVariableState = clusterVariableState;
  }

  @Override
  public void applyState(final long key, final ClusterVariableRecord clusterVariableRecord) {
    if (clusterVariableRecord.isTenantScoped()) {
      clusterVariableState.deleteTenantScopedClusterVariable(
          clusterVariableRecord.getNameBuffer(), clusterVariableRecord.getTenantId());
    } else if (clusterVariableRecord.isGloballyScoped()) {
      clusterVariableState.deleteGloballyScopedClusterVariable(
          clusterVariableRecord.getNameBuffer());
    }
  }
}
