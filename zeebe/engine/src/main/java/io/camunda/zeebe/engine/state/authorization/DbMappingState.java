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

    mappingKey = new DbLong();
    fkClaim = new DbForeignKey<>(claim, ZbColumnFamilies.MAPPINGS);
    claimByKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CLAIM_BY_KEY, transactionContext, mappingKey, fkClaim);

    mappingId = new DbString();
    claimByIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CLAIM_BY_ID, transactionContext, mappingId, fkClaim);
  }

  @Override
  public void create(final MappingRecord mappingRecord) {
    final var key = mappingRecord.getMappingKey();
    final var id = mappingRecord.getMappingId();
    final var name = mappingRecord.getName();
    final var claimName = mappingRecord.getClaimName();
    final var value = mappingRecord.getClaimValue();

    mappingKey.wrapLong(key);
    this.claimName.wrapString(claimName);
    claimValue.wrapString(value);
    mappingId.wrapString(id);
    persistedMapping.setMappingKey(key);
    persistedMapping.setClaimName(claimName);
    persistedMapping.setClaimValue(value);
    persistedMapping.setName(name);
    persistedMapping.setMappingId(id);

    mappingColumnFamily.insert(claim, persistedMapping);
    claimByIdColumnFamily.insert(mappingId, fkClaim);
    claimByKeyColumnFamily.insert(mappingKey, fkClaim);
  }

  @Override
  public void update(final MappingRecord mappingRecord) {
    mappingId.wrapString(mappingRecord.getMappingId());
    get(mappingRecord.getMappingId())
        .ifPresentOrElse(
            persistedMapping -> {
              mappingKey.wrapLong(persistedMapping.getMappingKey());

              // remove old record from mapping by claim
              claimName.wrapString(persistedMapping.getClaimName());
              claimValue.wrapString(persistedMapping.getClaimValue());
              mappingColumnFamily.deleteExisting(claim);

              persistedMapping.setName(mappingRecord.getName());
              persistedMapping.setClaimName(mappingRecord.getClaimName());
              persistedMapping.setClaimValue(mappingRecord.getClaimValue());

              claimName.wrapString(persistedMapping.getClaimName());
              claimValue.wrapString(persistedMapping.getClaimValue());
              mappingColumnFamily.insert(claim, persistedMapping);
              claimByKeyColumnFamily.update(mappingKey, fkClaim);
              claimByIdColumnFamily.update(mappingId, fkClaim);
            },
            () -> {
              throw new IllegalStateException(
                  String.format(
                      "Expected to update mapping with id '%s', but a mapping with this id does not exist.",
                      mappingRecord.getMappingId()));
            });
  }

  @Override
  public void addRole(final String mappingId, final long roleKey) {
    this.mappingId.wrapString(mappingId);
    final var fkClaim = claimByIdColumnFamily.get(this.mappingId);
    if (fkClaim != null) {
      final var claim = fkClaim.inner();
      final var persistedMapping = mappingColumnFamily.get(claim);
      persistedMapping.addRoleKey(roleKey);
      mappingColumnFamily.update(claim, persistedMapping);
    }
  }

  @Override
  public void addTenant(final String mappingId, final String tenantId) {
    this.mappingId.wrapString(mappingId);
    final var fkClaim = claimByIdColumnFamily.get(this.mappingId);
    if (fkClaim != null) {
      final var claim = fkClaim.inner();
      final var persistedMapping = mappingColumnFamily.get(claim);
      persistedMapping.addTenantId(tenantId);
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
  public void removeRole(final String mappingId, final long roleKey) {
    this.mappingId.wrapString(mappingId);
    final var fkClaim = claimByIdColumnFamily.get(this.mappingId);
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
  public void removeTenant(final long mappingKey, final String tenantId) {
    this.mappingKey.wrapLong(mappingKey);
    final var fkClaim = claimByKeyColumnFamily.get(this.mappingKey);
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
  public void delete(final String id) {
    get(id)
        .ifPresentOrElse(
            persistedMapping -> {
              mappingKey.wrapLong(persistedMapping.getMappingKey());
              claimName.wrapString(persistedMapping.getClaimName());
              claimValue.wrapString(persistedMapping.getClaimValue());
              mappingColumnFamily.deleteExisting(claim);
              claimByKeyColumnFamily.deleteExisting(mappingKey);
              claimByIdColumnFamily.deleteExisting(mappingId);
            },
            () -> {
              throw new IllegalStateException(
                  String.format(
                      "Expected to delete mapping with id '%s', but a mapping with this id does not exist.",
                      id));
            });
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
    final var persistedMapping = mappingColumnFamily.get(claim);

    if (persistedMapping == null) {
      return Optional.empty();
    }

    return Optional.of(persistedMapping.copy());
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
