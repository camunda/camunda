/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ClusterVariableState;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import org.agrona.DirectBuffer;

public interface MutableClusterVariableState extends ClusterVariableState {

  void createTenantScopedClusterVariable(ClusterVariableRecord clusterVariableRecord);

  void createGloballyScopedClusterVariable(ClusterVariableRecord clusterVariableRecord);

  void updateTenantScopedClusterVariable(ClusterVariableRecord clusterVariableRecord);

  void updateGloballyScopedClusterVariable(ClusterVariableRecord clusterVariableRecord);

  void deleteTenantScopedClusterVariable(DirectBuffer variableNameBuffer, String tenantId);

  void deleteGloballyScopedClusterVariable(DirectBuffer variableNameBuffer);
}
