/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.group;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.authorization.EntityTypeValue;
import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DbGroupState implements MutableGroupState {

  private final DbString groupId;
  private final PersistedGroup persistedGroup = new PersistedGroup();
  private final ColumnFamily<DbString, PersistedGroup> groupColumnFamily;

  private final DbForeignKey<DbString> fkGroupId;
  private final DbLong entityKey;
  private final DbCompositeKey<DbForeignKey<DbString>, DbLong> fkGroupIdAndEntityKey;
  private final EntityTypeValue entityTypeValue = new EntityTypeValue();
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbString>, DbLong>, EntityTypeValue>
      entityTypeByGroupColumnFamily;

  public DbGroupState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    groupId = new DbString();
    groupColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GROUPS, transactionContext, groupId, new PersistedGroup());

    fkGroupId = new DbForeignKey<>(groupId, ZbColumnFamilies.GROUPS);
    entityKey = new DbLong();
    fkGroupIdAndEntityKey = new DbCompositeKey<>(fkGroupId, entityKey);
    entityTypeByGroupColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITY_BY_GROUP,
            transactionContext,
            fkGroupIdAndEntityKey,
            entityTypeValue);
  }

  @Override
  public void create(final long groupKey, final GroupRecord group) {
    groupId.wrapString(String.valueOf(groupKey));
    persistedGroup.wrap(group);
    groupColumnFamily.insert(groupId, persistedGroup);
  }

  @Override
  public void update(final long groupKey, final GroupRecord group) {
    groupId.wrapString(String.valueOf(groupKey));
    final var persistedGroup = groupColumnFamily.get(groupId);
    if (persistedGroup != null) {
      persistedGroup.copyFrom(group);
      groupColumnFamily.update(groupId, persistedGroup);
    }
  }

  @Override
  public void addEntity(final long groupKey, final GroupRecord group) {
    groupId.wrapString(String.valueOf(groupKey));
    entityKey.wrapLong(group.getEntityKey());
    entityTypeValue.setEntityType(group.getEntityType());
    entityTypeByGroupColumnFamily.insert(fkGroupIdAndEntityKey, entityTypeValue);
  }

  @Override
  public void removeEntity(final long groupKey, final long entityKey) {
    groupId.wrapString(String.valueOf(groupKey));
    this.entityKey.wrapLong(entityKey);
    entityTypeByGroupColumnFamily.deleteExisting(fkGroupIdAndEntityKey);
  }

  @Override
  public void delete(final long groupKey) {
    groupId.wrapString(String.valueOf(groupKey));

    // remove entries from ENTITY_BY_GROUP cf
    entityTypeByGroupColumnFamily.whileEqualPrefix(
        fkGroupId,
        (compositeKey, value) -> {
          entityTypeByGroupColumnFamily.deleteExisting(compositeKey);
        });

    groupColumnFamily.deleteExisting(groupId);
  }

  @Override
  public void addTenant(final long groupKey, final String tenantId) {
    groupId.wrapString(String.valueOf(groupKey));
    final PersistedGroup persistedGroup = groupColumnFamily.get(groupId);
    persistedGroup.addTenantId(tenantId);
    groupColumnFamily.update(groupId, persistedGroup);
  }

  @Override
  public void removeTenant(final long groupKey, final String tenantId) {
    groupId.wrapString(String.valueOf(groupKey));
    final var persistedGroup = groupColumnFamily.get(groupId);
    final List<String> tenantIdsList = persistedGroup.getTenantIdsList();
    tenantIdsList.remove(tenantId);
    persistedGroup.setTenantIdsList(tenantIdsList);
    groupColumnFamily.update(groupId, persistedGroup);
  }

  @Override
  public Optional<PersistedGroup> get(final long groupKey) {
    groupId.wrapString(String.valueOf(groupKey));
    final var persistedGroup = groupColumnFamily.get(groupId);
    return Optional.ofNullable(persistedGroup);
  }

  @Override
  public Optional<PersistedGroup> get(final String groupId) {
    this.groupId.wrapString(groupId);
    final var persistedGroup = groupColumnFamily.get(this.groupId);
    return Optional.ofNullable(persistedGroup);
  }

  @Override
  public Optional<EntityType> getEntityType(final long groupKey, final long entityKey) {
    groupId.wrapString(String.valueOf(groupKey));
    this.entityKey.wrapLong(entityKey);
    final var entityType = entityTypeByGroupColumnFamily.get(fkGroupIdAndEntityKey);
    return Optional.ofNullable(entityType).map(EntityTypeValue::getEntityType);
  }

  @Override
  public Map<EntityType, List<Long>> getEntitiesByType(final long groupKey) {
    groupId.wrapString(String.valueOf(groupKey));
    final Map<EntityType, List<Long>> entitiesMap = new HashMap<>();
    entityTypeByGroupColumnFamily.whileEqualPrefix(
        fkGroupId,
        (compositeKey, value) -> {
          final var entityType = value.getEntityType();
          final var entityKey = compositeKey.second().getValue();
          entitiesMap.computeIfAbsent(entityType, k -> new ArrayList<>()).add(entityKey);
        });
    return entitiesMap;
  }
}
