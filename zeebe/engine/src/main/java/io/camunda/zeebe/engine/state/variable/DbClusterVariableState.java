/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableClusterVariableState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.variable.ClusterVariableRecord;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class DbClusterVariableState implements MutableClusterVariableState {

  private final ColumnFamily<
          DbCompositeKey<DbString, DbCompositeKey<DbEnumValue<Scope>, DbString>>,
          ClusterVariableInstance>
      clusterVariablesColumnFamily;
  private final DbCompositeKey<DbString, DbCompositeKey<DbEnumValue<Scope>, DbString>>
      clusterVariableNameScopeTenantIdCompositeKey;

  private final DbEnumValue<Scope> scope;
  private final DbString name;
  private final DbString tenantId;

  private final ClusterVariableInstance clusterVariableInstance;

  public DbClusterVariableState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    clusterVariableInstance = new ClusterVariableInstance();

    name = new DbString();
    tenantId = new DbString();
    scope = new DbEnumValue<>(Scope.class);

    clusterVariableNameScopeTenantIdCompositeKey =
        new DbCompositeKey<>(name, new DbCompositeKey<>(scope, tenantId));
    clusterVariablesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CLUSTER_VARIABLES,
            transactionContext,
            clusterVariableNameScopeTenantIdCompositeKey,
            clusterVariableInstance);
  }

  @Override
  public void setClusterVariable(final ClusterVariableRecord clusterVariableRecord) {
    name.wrapBuffer(clusterVariableRecord.getNameBuffer());
    if (clusterVariableRecord.getTenantId() == null
        || clusterVariableRecord.getTenantId().isBlank()) {
      tenantId.wrapString("");
      scope.setValue(Scope.GLOBAL);
    } else {
      tenantId.wrapString(clusterVariableRecord.getTenantId());
      scope.setValue(Scope.TENANT);
    }

    clusterVariableInstance.setRecord(clusterVariableRecord);

    clusterVariablesColumnFamily.upsert(
        clusterVariableNameScopeTenantIdCompositeKey, clusterVariableInstance);
  }

  @Override
  public void deleteTenantScopedClusterVariable(
      final DirectBuffer variableNameBuffer, final String tenantId) {
    name.wrapBuffer(variableNameBuffer);
    scope.setValue(Scope.TENANT);
    this.tenantId.wrapString(tenantId);
    clusterVariablesColumnFamily.deleteExisting(clusterVariableNameScopeTenantIdCompositeKey);
  }

  @Override
  public void deleteGloballyScopedClusterVariable(final DirectBuffer variableNameBuffer) {
    name.wrapBuffer(variableNameBuffer);
    scope.setValue(Scope.GLOBAL);
    tenantId.wrapString("");
    clusterVariablesColumnFamily.deleteExisting(clusterVariableNameScopeTenantIdCompositeKey);
  }

  @Override
  public Optional<ClusterVariableInstance> getTenantScopedClusterVariable(
      final DirectBuffer variableNameBuffer, final String tenantId) {
    name.wrapBuffer(variableNameBuffer);
    this.tenantId.wrapString(tenantId);
    scope.setValue(Scope.TENANT);
    return Optional.ofNullable(
        clusterVariablesColumnFamily.get(
            clusterVariableNameScopeTenantIdCompositeKey, ClusterVariableInstance::new));
  }

  @Override
  public Optional<ClusterVariableInstance> getGloballyScopedClusterVariable(
      final DirectBuffer variableNameBuffer) {
    name.wrapBuffer(variableNameBuffer);
    tenantId.wrapString("");
    scope.setValue(Scope.GLOBAL);
    return Optional.ofNullable(
        clusterVariablesColumnFamily.get(
            clusterVariableNameScopeTenantIdCompositeKey, ClusterVariableInstance::new));
  }

  @Override
  public boolean existsAtTenantScope(final DirectBuffer variableNameBuffer, final String tenantId) {
    name.wrapBuffer(variableNameBuffer);
    this.tenantId.wrapString(tenantId);
    scope.setValue(Scope.TENANT);
    return clusterVariablesColumnFamily.exists(clusterVariableNameScopeTenantIdCompositeKey);
  }

  @Override
  public boolean existsAtGlobalScope(final DirectBuffer variableNameBuffer) {
    name.wrapBuffer(variableNameBuffer);
    tenantId.wrapString("");
    scope.setValue(Scope.GLOBAL);
    return clusterVariablesColumnFamily.exists(clusterVariableNameScopeTenantIdCompositeKey);
  }

  enum Scope {
    GLOBAL,
    TENANT
  }
}
