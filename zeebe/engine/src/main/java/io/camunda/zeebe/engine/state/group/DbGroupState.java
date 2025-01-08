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
import io.camunda.zeebe.db.impl.DbNil;
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

  private final DbForeignKey<DbString> fkGroupKey;
  private final DbLong entityKey;
  private final DbString entityId;
  private final DbCompositeKey<DbForeignKey<DbString>, DbLong> fkGroupKeyAndEntityKey;
  private final EntityTypeValue entityTypeValue = new EntityTypeValue();
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbString>, DbLong>, EntityTypeValue>
      entityTypeByGroupColumnFamily;

  // CF key: groupId + entityId -> DbNil
  private final ColumnFamily<DbCompositeKey<DbString, DbForeignKey<DbString>>, DbNil>
      entitiesByGroup;

  private final DbString groupName;
  private final ColumnFamily<DbString, DbForeignKey<DbString>> groupByNameColumnFamily;
  private final DbCompositeKey<DbString, DbForeignKey<DbString>> entityIdAndGroupId;

  public DbGroupState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    groupId = new DbString();
    groupColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GROUPS, transactionContext, groupId, new PersistedGroup());

    fkGroupKey = new DbForeignKey<>(groupId, ZbColumnFamilies.GROUPS);
    entityKey = new DbLong();
    fkGroupKeyAndEntityKey = new DbCompositeKey<>(fkGroupKey, entityKey);
    entityTypeByGroupColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITY_BY_GROUP,
            transactionContext,
            fkGroupKeyAndEntityKey,
            entityTypeValue);

    groupName = new DbString();
    groupByNameColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GROUP_BY_NAME, transactionContext, groupName, fkGroupKey);

    entityId = new DbString();
    entityIdAndGroupId = new DbCompositeKey<>(entityId, fkGroupKey);
    entitiesByGroup =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITIES_BY_GROUP,
            transactionContext,
            entityIdAndGroupId,
            DbNil.INSTANCE);
  }

  @Override
  public void create(final String groupId, final GroupRecord group) {
    this.groupId.wrapString(groupId);
    groupName.wrapString(group.getName());
    persistedGroup.wrap(group);

    groupColumnFamily.insert(this.groupId, persistedGroup);
    groupByNameColumnFamily.insert(groupName, fkGroupKey);
  }

  @Override
  public void update(final String groupId, final GroupRecord group) {
    this.groupId.wrapString(groupId);
    final var persistedGroup = groupColumnFamily.get(this.groupId);
    if (persistedGroup != null) {
      // remove old record from GROUP_BY_NAME cf
      groupName.wrapString(persistedGroup.getName());
      groupByNameColumnFamily.deleteExisting(groupName);

      // add new record to GROUP_BY_NAME cf
      groupName.wrapString(group.getName());
      groupByNameColumnFamily.insert(groupName, fkGroupKey);

      persistedGroup.copyFrom(group);
      groupColumnFamily.update(this.groupId, persistedGroup);
    }
  }

  @Override
  public void addEntity(final String groupId, final GroupRecord group) {
    this.groupId.wrapString(groupId);
    entityKey.wrapLong(group.getEntityKey());
    entityTypeValue.setEntityType(group.getEntityType());
    entityTypeByGroupColumnFamily.insert(fkGroupKeyAndEntityKey, entityTypeValue);

    entityId.wrapString(group.getEntityId());
    entitiesByGroup.insert(entityIdAndGroupId, DbNil.INSTANCE);
  }

  @Override
  public void removeEntity(final String groupId, final long entityKey, final String entityId) {
    this.groupId.wrapString(groupId);
    this.entityKey.wrapLong(entityKey);
    entityTypeByGroupColumnFamily.deleteExisting(fkGroupKeyAndEntityKey);

    this.entityId.wrapString(entityId);
    entitiesByGroup.deleteExisting(entityIdAndGroupId);
  }

  @Override
  public void delete(final String groupId) {
    this.groupId.wrapString(groupId);

    // remove record from GROUP_BY_NAME cf
    groupName.wrapString(persistedGroup.getName());
    groupByNameColumnFamily.deleteExisting(groupName);

    // remove entries from ENTITY_BY_GROUP cf
    entityTypeByGroupColumnFamily.whileEqualPrefix(
        fkGroupKey,
        (compositeKey, value) -> {
          entityTypeByGroupColumnFamily.deleteExisting(compositeKey);
        });

    groupColumnFamily.deleteExisting(this.groupId);
  }

  @Override
  public void addTenant(final String groupId, final String tenantId) {
    this.groupId.wrapString(groupId);
    final PersistedGroup persistedGroup = groupColumnFamily.get(this.groupId);
    persistedGroup.addTenantId(tenantId);
    groupColumnFamily.update(this.groupId, persistedGroup);
  }

  @Override
  public void removeTenant(final String groupId, final String tenantId) {
    this.groupId.wrapString(groupId);
    final var persistedGroup = groupColumnFamily.get(this.groupId);
    final List<String> tenantIdsList = persistedGroup.getTenantIdsList();
    tenantIdsList.remove(tenantId);
    persistedGroup.setTenantIdsList(tenantIdsList);
    groupColumnFamily.update(this.groupId, persistedGroup);
  }

  @Override
  public Optional<PersistedGroup> get(final String groupId) {
    this.groupId.wrapString(groupId);
    final var persistedGroup = groupColumnFamily.get(this.groupId);
    return Optional.ofNullable(persistedGroup);
  }

  @Override
  public Optional<String> getGroupKeyByName(final String groupName) {
    this.groupName.wrapString(groupName);
    final var groupId = groupByNameColumnFamily.get(this.groupName);
    return Optional.ofNullable(groupId).map(key -> key.inner().toString());
  }

  @Override
  public Optional<EntityType> getEntityType(final String groupId, final long entityKey) {
    this.groupId.wrapString(groupId);
    this.entityKey.wrapLong(entityKey);
    final var entityType = entityTypeByGroupColumnFamily.get(fkGroupKeyAndEntityKey);
    return Optional.ofNullable(entityType).map(EntityTypeValue::getEntityType);
  }

  @Override
  public Map<EntityType, List<Long>> getEntitiesByType(final String groupId) {
    this.groupId.wrapString(groupId);
    final Map<EntityType, List<Long>> entitiesMap = new HashMap<>();
    entityTypeByGroupColumnFamily.whileEqualPrefix(
        fkGroupKey,
        (compositeKey, value) -> {
          final var entityType = value.getEntityType();
          final var entityKey = compositeKey.second().getValue();
          entitiesMap.computeIfAbsent(entityType, k -> new ArrayList<>()).add(entityKey);
        });
    return entitiesMap;
  }

  @Override
  public List<String> getGroupIdsForEntity(final String entityId) {
    this.entityId.wrapString(entityId);
    final var groupIds = new ArrayList<String>();
    entitiesByGroup.whileEqualPrefix(
        this.entityId,
        (key, value) -> {
          groupIds.add(key.second().inner().toString());
        });
    return groupIds;
  }
}
