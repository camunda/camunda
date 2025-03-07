/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableMappingState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import java.util.List;
import java.util.Optional;

public class DbMappingState implements MutableMappingState {

  private final DbString claimName;
  private final DbString claimValue;
  private final DbCompositeKey<DbString, DbString> claim;
  private final PersistedMapping persistedMapping = new PersistedMapping();
  private final ColumnFamily<DbCompositeKey<DbString, DbString>, PersistedMapping>
      mappingColumnFamily;

  private final DbForeignKey<DbCompositeKey<DbString, DbString>> fkClaim;

  private final DbString mappingId;
  private final ColumnFamily<DbString, DbForeignKey<DbCompositeKey<DbString, DbString>>>
      claimByIdColumnFamily;

  public DbMappingState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    claimName = new DbString();
    claimValue = new DbString();
    claim = new DbCompositeKey<>(claimName, claimValue);
    final PersistedMapping persistedMapping = new PersistedMapping();
    mappingColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MAPPINGS, transactionContext, claim, persistedMapping);

    fkClaim = new DbForeignKey<>(claim, ZbColumnFamilies.MAPPINGS);

    mappingId = new DbString();
    claimByIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CLAIM_BY_ID, transactionContext, mappingId, fkClaim);
  }

  @Override
  public void create(final MappingRecord mappingRecord) {
    final var id = mappingRecord.getId();
    final var name = mappingRecord.getName();
    final var claimName = mappingRecord.getClaimName();
    final var value = mappingRecord.getClaimValue();

    this.claimName.wrapString(claimName);
    claimValue.wrapString(value);
    mappingId.wrapString(id);
    persistedMapping.setClaimName(claimName);
    persistedMapping.setClaimValue(value);
    persistedMapping.setName(name);
    persistedMapping.setId(id);

    mappingColumnFamily.insert(claim, persistedMapping);
    claimByIdColumnFamily.insert(mappingId, fkClaim);
  }

  @Override
  public void addRole(final String id, final long roleKey) {
    mappingId.wrapString(id);
    final var fkClaim = claimByIdColumnFamily.get(mappingId);
    if (fkClaim != null) {
      final var claim = fkClaim.inner();
      final var persistedMapping = mappingColumnFamily.get(claim);
      persistedMapping.addRoleKey(roleKey);
      mappingColumnFamily.update(claim, persistedMapping);
    }
  }

  @Override
  public void addTenant(final String id, final String tenantId) {
    mappingId.wrapString(id);
    final var fkClaim = claimByIdColumnFamily.get(mappingId);
    if (fkClaim != null) {
      final var claim = fkClaim.inner();
      final var persistedMapping = mappingColumnFamily.get(claim);
      persistedMapping.addTenantId(tenantId);
      mappingColumnFamily.update(claim, persistedMapping);
    }
  }

  @Override
  public void addGroup(final String id, final long groupKey) {
    mappingId.wrapString(id);
    final var fkClaim = claimByIdColumnFamily.get(mappingId);
    if (fkClaim != null) {
      final var claim = fkClaim.inner();
      final var persistedMapping = mappingColumnFamily.get(claim);
      persistedMapping.addGroupKey(groupKey);
      mappingColumnFamily.update(claim, persistedMapping);
    }
  }

  @Override
  public void removeRole(final String id, final long roleKey) {
    mappingId.wrapString(id);
    final var fkClaim = claimByIdColumnFamily.get(mappingId);
    if (fkClaim != null) {
      final var claim = fkClaim.inner();
      final var persistedMapping = mappingColumnFamily.get(claim);
      final List<Long> roleKeys = persistedMapping.getRoleKeysList();
      roleKeys.remove(roleKey);
      persistedMapping.setRoleKeysList(roleKeys);
      mappingColumnFamily.update(claim, persistedMapping);
    }
  }

  @Override
  public void removeTenant(final String id, final String tenantId) {
    mappingId.wrapString(id);
    final var fkClaim = claimByIdColumnFamily.get(mappingId);
    if (fkClaim != null) {
      final var claim = fkClaim.inner();
      final var persistedMapping = mappingColumnFamily.get(claim);
      final var tenantIds = persistedMapping.getTenantIdsList();
      tenantIds.remove(tenantId);
      persistedMapping.setTenantIdsList(tenantIds);
      mappingColumnFamily.update(claim, persistedMapping);
    }
  }

  @Override
  public void removeGroup(final String id, final long groupKey) {
    mappingId.wrapString(id);
    final var fkClaim = claimByIdColumnFamily.get(mappingId);
    if (fkClaim != null) {
      final var claim = fkClaim.inner();
      final var persistedMapping = mappingColumnFamily.get(claim);
      final List<Long> groupKeys = persistedMapping.getGroupKeysList();
      groupKeys.remove(groupKey);
      persistedMapping.setGroupKeysList(groupKeys);
      mappingColumnFamily.update(claim, persistedMapping);
    }
  }

  @Override
  public void delete(final String id) {
    mappingId.wrapString(id);
    final var claimKey = claimByIdColumnFamily.get(mappingId);
    if (claimKey != null) {
      claimByIdColumnFamily.deleteExisting(mappingId);
      mappingColumnFamily.deleteExisting(claimKey.inner());
    }
  }

  @Override
  public Optional<PersistedMapping> get(final String claimName, final String claimValue) {
    this.claimName.wrapString(claimName);
    this.claimValue.wrapString(claimValue);
    return Optional.ofNullable(mappingColumnFamily.get(claim));
  }

  @Override
  public Optional<PersistedMapping> get(final String id) {
    mappingId.wrapString(id);
    final var fk = claimByIdColumnFamily.get(mappingId);
    if (fk != null) {
      return Optional.of(mappingColumnFamily.get(fk.inner()));
    }
    return Optional.empty();
  }

  @Override
  public Optional<PersistedMapping> get(final String id) {
    mappingId.wrapString(id);
    final var fk = claimByIdColumnFamily.get(mappingId);
    if (fk != null) {
      return Optional.of(mappingColumnFamily.get(fk.inner()));
    }
    return Optional.empty();
  }
}
