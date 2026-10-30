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
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableClusterVariableState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class DbClusterVariableState implements MutableClusterVariableState {

  private final DbString clusterVariableName;
  private final DbEnumValue<ClusterVariableScope> clusterVariableScope;
  private final DbString clusterVariableTenantId;
  private final DbCompositeKey<DbString, DbClusterVariableScopeKey> clusterVariableKey;

  private final ColumnFamily<
          DbCompositeKey<DbString, DbClusterVariableScopeKey>, ClusterVariableInstance>
      clusterVariablesColumnFamily;
  private final ClusterVariableInstance clusterVariableInstance;

  public DbClusterVariableState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    clusterVariableName = new DbString();
    clusterVariableScope = new DbEnumValue<>(ClusterVariableScope.class);
    clusterVariableTenantId = new DbString();
    clusterVariableKey =
        new DbCompositeKey<>(
            clusterVariableName,
            new DbClusterVariableScopeKey(clusterVariableScope, clusterVariableTenantId));

    clusterVariableInstance = new ClusterVariableInstance();
    clusterVariablesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CLUSTER_VARIABLES,
            transactionContext,
            clusterVariableKey,
            clusterVariableInstance);
  }

  @Override
  public void createTenantScopedClusterVariable(final ClusterVariableRecord clusterVariableRecord) {
    clusterVariableName.wrapBuffer(clusterVariableRecord.getNameBuffer());
    clusterVariableScope.setValue(ClusterVariableScope.TENANT);
    clusterVariableTenantId.wrapString(clusterVariableRecord.getTenantId());
    clusterVariableInstance.setRecord(clusterVariableRecord);
    clusterVariablesColumnFamily.insert(clusterVariableKey, clusterVariableInstance);
  }

  @Override
  public void createGloballyScopedClusterVariable(
      final ClusterVariableRecord clusterVariableRecord) {
    clusterVariableName.wrapBuffer(clusterVariableRecord.getNameBuffer());
    clusterVariableScope.setValue(ClusterVariableScope.GLOBAL);
    clusterVariableInstance.setRecord(clusterVariableRecord);
    clusterVariablesColumnFamily.insert(clusterVariableKey, clusterVariableInstance);
  }

  @Override
  public void updateTenantScopedClusterVariable(final ClusterVariableRecord clusterVariableRecord) {
    clusterVariableName.wrapBuffer(clusterVariableRecord.getNameBuffer());
    clusterVariableScope.setValue(ClusterVariableScope.TENANT);
    clusterVariableTenantId.wrapString(clusterVariableRecord.getTenantId());
    clusterVariableInstance.setRecord(clusterVariableRecord);
    clusterVariablesColumnFamily.update(clusterVariableKey, clusterVariableInstance);
  }

  @Override
  public void updateGloballyScopedClusterVariable(
      final ClusterVariableRecord clusterVariableRecord) {
    clusterVariableName.wrapBuffer(clusterVariableRecord.getNameBuffer());
    clusterVariableScope.setValue(ClusterVariableScope.GLOBAL);
    clusterVariableInstance.setRecord(clusterVariableRecord);
    clusterVariablesColumnFamily.update(clusterVariableKey, clusterVariableInstance);
  }

  @Override
  public void deleteTenantScopedClusterVariable(
      final DirectBuffer variableNameBuffer, final String tenantId) {
    clusterVariableName.wrapBuffer(variableNameBuffer);
    clusterVariableScope.setValue(ClusterVariableScope.TENANT);
    clusterVariableTenantId.wrapString(tenantId);
    clusterVariablesColumnFamily.deleteExisting(clusterVariableKey);
  }

  @Override
  public void deleteGloballyScopedClusterVariable(final DirectBuffer variableNameBuffer) {
    clusterVariableName.wrapBuffer(variableNameBuffer);
    clusterVariableScope.setValue(ClusterVariableScope.GLOBAL);
    clusterVariablesColumnFamily.deleteExisting(clusterVariableKey);
  }

  @Override
  public Optional<ClusterVariableInstance> getTenantScopedClusterVariable(
      final DirectBuffer variableNameBuffer, final String tenantId) {
    clusterVariableName.wrapBuffer(variableNameBuffer);
    clusterVariableScope.setValue(ClusterVariableScope.TENANT);
    clusterVariableTenantId.wrapString(tenantId);
    return Optional.ofNullable(
        clusterVariablesColumnFamily.get(clusterVariableKey, ClusterVariableInstance::new));
  }

  @Override
  public Optional<ClusterVariableInstance> getGloballyScopedClusterVariable(
      final DirectBuffer variableNameBuffer) {
    clusterVariableName.wrapBuffer(variableNameBuffer);
    clusterVariableScope.setValue(ClusterVariableScope.GLOBAL);
    return Optional.ofNullable(
        clusterVariablesColumnFamily.get(clusterVariableKey, ClusterVariableInstance::new));
  }

  @Override
  public boolean existsAtTenantScope(final DirectBuffer variableNameBuffer, final String tenantId) {
    clusterVariableName.wrapBuffer(variableNameBuffer);
    clusterVariableScope.setValue(ClusterVariableScope.TENANT);
    clusterVariableTenantId.wrapString(tenantId);
    return clusterVariablesColumnFamily.exists(clusterVariableKey);
  }

  @Override
  public boolean existsAtGlobalScope(final DirectBuffer variableNameBuffer) {
    clusterVariableName.wrapBuffer(variableNameBuffer);
    clusterVariableScope.setValue(ClusterVariableScope.GLOBAL);
    return clusterVariablesColumnFamily.exists(clusterVariableKey);
  }
}
