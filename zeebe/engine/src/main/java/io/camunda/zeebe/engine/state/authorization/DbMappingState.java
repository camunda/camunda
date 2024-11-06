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
import io.camunda.zeebe.db.impl.DbLong;
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

  private final DbLong mappingKey;
  private final DbForeignKey<DbCompositeKey<DbString, DbString>> fkClaim;
  private final ColumnFamily<DbLong, DbForeignKey<DbCompositeKey<DbString, DbString>>>
      claimByKeyColumnFamily;

  public DbMappingState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    claimName = new DbString();
    claimValue = new DbString();
    claim = new DbCompositeKey<>(claimName, claimValue);
    mappingColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MAPPINGS, transactionContext, claim, new PersistedMapping());

    mappingKey = new DbLong();
    fkClaim = new DbForeignKey<>(claim, ZbColumnFamilies.MAPPINGS);
    claimByKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CLAIM_BY_KEY, transactionContext, mappingKey, fkClaim);
  }

  @Override
  public void create(final MappingRecord mappingRecord) {
    final var key = mappingRecord.getMappingKey();
    final var name = mappingRecord.getClaimName();
    final var value = mappingRecord.getClaimValue();

    mappingKey.wrapLong(key);
    claimName.wrapString(name);
    claimValue.wrapString(value);
    persistedMapping.setMappingKey(key);
    persistedMapping.setClaimName(name);
    persistedMapping.setClaimValue(value);

    mappingColumnFamily.insert(claim, persistedMapping);
    claimByKeyColumnFamily.insert(mappingKey, fkClaim);
  }

  @Override
  public void addRole(final long mappingKey, final long roleKey) {
    this.mappingKey.wrapLong(mappingKey);
    final var fkClaim = claimByKeyColumnFamily.get(this.mappingKey);
    if (fkClaim != null) {
      final var claim = fkClaim.inner();
      final var persistedMapping = mappingColumnFamily.get(claim);
      persistedMapping.addRoleKey(roleKey);
      mappingColumnFamily.update(claim, persistedMapping);
    }
  }

  @Override
  public void addGroup(final long mappingKey, final long groupKey) {
    this.mappingKey.wrapLong(mappingKey);
    final var fkClaim = claimByKeyColumnFamily.get(this.mappingKey);
    if (fkClaim != null) {
      final var claim = fkClaim.inner();
      final var persistedMapping = mappingColumnFamily.get(claim);
      persistedMapping.addGroupKey(groupKey);
      mappingColumnFamily.update(claim, persistedMapping);
    }
  }

  @Override
  public void addTenant(final long mappingKey, final String tenantId) {
    this.mappingKey.wrapLong(mappingKey);
    final var fkClaim = claimByKeyColumnFamily.get(this.mappingKey);
    if (fkClaim != null) {
      final var claim = fkClaim.inner();
      final var persistedMapping = mappingColumnFamily.get(claim);
      persistedMapping.addTenantId(tenantId);
      mappingColumnFamily.update(claim, persistedMapping);
    }
  }

  @Override
  public void removeRole(final long mappingKey, final long roleKey) {
    this.mappingKey.wrapLong(mappingKey);
    final var fkClaim = claimByKeyColumnFamily.get(this.mappingKey);
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
  public void removeGroup(final long mappingKey, final long groupKey) {
    this.mappingKey.wrapLong(mappingKey);
    final var fkClaim = claimByKeyColumnFamily.get(this.mappingKey);
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
  public void removeTenant(final long mappingKey, final String tenantId) {
    this.mappingKey.wrapLong(mappingKey);
    final var fkClaim = claimByKeyColumnFamily.get(this.mappingKey);
    if (fkClaim != null) {
      final var claim = fkClaim.inner();
      final var persistedMapping = mappingColumnFamily.get(claim);
      final List<String> tenantIds = persistedMapping.getTenantIdsList();
      tenantIds.remove(tenantId);
      persistedMapping.setTenantIdsList(tenantIds);
      mappingColumnFamily.update(claim, persistedMapping);
    }
  }

  @Override
  public Optional<PersistedMapping> get(final long key) {
    mappingKey.wrapLong(key);
    final var fk = claimByKeyColumnFamily.get(mappingKey);
    if (fk != null) {
      return Optional.of(mappingColumnFamily.get(fk.inner()));
    }
    return Optional.empty();
  }

  @Override
  public Optional<PersistedMapping> get(final String claimName, final String claimValue) {
    this.claimName.wrapString(claimName);
    this.claimValue.wrapString(claimValue);
    return Optional.ofNullable(mappingColumnFamily.get(claim));
  }
}
