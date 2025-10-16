/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clustervariable;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbClusterVariableKey;
import io.camunda.zeebe.engine.state.mutable.MutableClusterVariableState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.variable.ClusterVariableRecord;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class DbClusterVariableState implements MutableClusterVariableState {

  private final DbClusterVariableKey clusterVariableKey;
  private final ColumnFamily<DbClusterVariableKey, ClusterVariableInstance>
      clusterVariablesColumnFamily;
  private final ClusterVariableInstance clusterVariableInstance;

  public DbClusterVariableState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    clusterVariableInstance = new ClusterVariableInstance();
    clusterVariableKey = new DbClusterVariableKey();
    clusterVariablesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CLUSTER_VARIABLES,
            transactionContext,
            clusterVariableKey,
            clusterVariableInstance);
  }

  @Override
  public void create(final ClusterVariableRecord clusterVariableRecord) {
    if (clusterVariableRecord.getTenantId() == null
        || clusterVariableRecord.getTenantId().isBlank()) {
      clusterVariableKey.globalKey(clusterVariableRecord.getNameBuffer());
    } else {
      clusterVariableKey.tenantKey(
          clusterVariableRecord.getNameBuffer(), clusterVariableRecord.getTenantId());
    }
    clusterVariableInstance.setRecord(clusterVariableRecord);
    clusterVariablesColumnFamily.insert(clusterVariableKey, clusterVariableInstance);
  }

  @Override
  public void deleteTenantScopedClusterVariable(
      final DirectBuffer variableNameBuffer, final String tenantId) {
    clusterVariablesColumnFamily.deleteExisting(
        clusterVariableKey.tenantKey(variableNameBuffer, tenantId));
  }

  @Override
  public void deleteGloballyScopedClusterVariable(final DirectBuffer variableNameBuffer) {
    clusterVariablesColumnFamily.deleteExisting(clusterVariableKey.globalKey(variableNameBuffer));
  }

  @Override
  public Optional<ClusterVariableInstance> getTenantScopedClusterVariable(
      final DirectBuffer variableNameBuffer, final String tenantId) {
    return Optional.ofNullable(
        clusterVariablesColumnFamily.get(
            clusterVariableKey.tenantKey(variableNameBuffer, tenantId),
            ClusterVariableInstance::new));
  }

  @Override
  public Optional<ClusterVariableInstance> getGloballyScopedClusterVariable(
      final DirectBuffer variableNameBuffer) {
    return Optional.ofNullable(
        clusterVariablesColumnFamily.get(
            clusterVariableKey.globalKey(variableNameBuffer), ClusterVariableInstance::new));
  }

  @Override
  public boolean existsAtTenantScope(final DirectBuffer variableNameBuffer, final String tenantId) {
    return clusterVariablesColumnFamily.exists(
        clusterVariableKey.tenantKey(variableNameBuffer, tenantId));
  }

  @Override
  public boolean existsAtGlobalScope(final DirectBuffer variableNameBuffer) {
    return clusterVariablesColumnFamily.exists(clusterVariableKey.globalKey(variableNameBuffer));
  }
}
