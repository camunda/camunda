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

  private final DbLong groupKey;
  private final PersistedGroup persistedGroup = new PersistedGroup();
  private final ColumnFamily<DbLong, PersistedGroup> groupColumnFamily;

  private final DbForeignKey<DbLong> fkGroupKey;
  private final DbString entityKey;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbString> fkGroupKeyAndEntityKey;
  private final EntityTypeValue entityTypeValue = new EntityTypeValue();
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbString>, EntityTypeValue>
      entityTypeByGroupColumnFamily;

  private final DbString groupName;
  private final ColumnFamily<DbString, DbForeignKey<DbLong>> groupByNameColumnFamily;

  public DbGroupState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    groupKey = new DbLong();
    groupColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GROUPS, transactionContext, groupKey, new PersistedGroup());

    fkGroupKey = new DbForeignKey<>(groupKey, ZbColumnFamilies.GROUPS);
    entityKey = new DbString();
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
  }

  @Override
  public void create(final long groupKey, final GroupRecord group) {
    this.groupKey.wrapLong(groupKey);
    groupName.wrapString(group.getName());
    persistedGroup.wrap(group);

    groupColumnFamily.insert(this.groupKey, persistedGroup);
    groupByNameColumnFamily.insert(groupName, fkGroupKey);
  }

  @Override
  public void update(final long groupKey, final GroupRecord group) {
    this.groupKey.wrapLong(groupKey);
    final var persistedGroup = groupColumnFamily.get(this.groupKey);
    if (persistedGroup != null) {
      // remove old record from GROUP_BY_NAME cf
      groupName.wrapString(persistedGroup.getName());
      groupByNameColumnFamily.deleteExisting(groupName);

      // add new record to GROUP_BY_NAME cf
      groupName.wrapString(group.getName());
      groupByNameColumnFamily.insert(groupName, fkGroupKey);

      persistedGroup.copyFrom(group);
      groupColumnFamily.update(this.groupKey, persistedGroup);
    }
  }

  @Override
  public void addEntity(final long groupKey, final GroupRecord group) {
    this.groupKey.wrapLong(groupKey);
    entityKey.wrapString(group.getEntityKey());
    entityTypeValue.setEntityType(group.getEntityType());
    entityTypeByGroupColumnFamily.insert(fkGroupKeyAndEntityKey, entityTypeValue);
  }

  @Override
  public void removeEntity(final long groupKey, final String entityKey) {
    this.groupKey.wrapLong(groupKey);
    this.entityKey.wrapString(entityKey);
    entityTypeByGroupColumnFamily.deleteExisting(fkGroupKeyAndEntityKey);
  }

  @Override
  public void delete(final long groupKey) {
    this.groupKey.wrapLong(groupKey);

    // remove record from GROUP_BY_NAME cf
    groupName.wrapString(persistedGroup.getName());
    groupByNameColumnFamily.deleteExisting(groupName);

    // remove entries from ENTITY_BY_GROUP cf
    entityTypeByGroupColumnFamily.whileEqualPrefix(
        fkGroupKey,
        (compositeKey, value) -> {
          entityTypeByGroupColumnFamily.deleteExisting(compositeKey);
        });

    groupColumnFamily.deleteExisting(this.groupKey);
  }

  @Override
  public void addTenant(final long groupKey, final String tenantId) {
    this.groupKey.wrapLong(groupKey);
    final PersistedGroup persistedGroup = groupColumnFamily.get(this.groupKey);
    persistedGroup.addTenantId(tenantId);
    groupColumnFamily.update(this.groupKey, persistedGroup);
  }

  @Override
  public void removeTenant(final long groupKey, final String tenantId) {
    this.groupKey.wrapLong(groupKey);
    final var persistedGroup = groupColumnFamily.get(this.groupKey);
    final List<String> tenantIdsList = persistedGroup.getTenantIdsList();
    tenantIdsList.remove(tenantId);
    persistedGroup.setTenantIdsList(tenantIdsList);
    groupColumnFamily.update(this.groupKey, persistedGroup);
  }

  @Override
  public Optional<PersistedGroup> get(final long groupKey) {
    this.groupKey.wrapLong(groupKey);
    final var persistedGroup = groupColumnFamily.get(this.groupKey);
    return Optional.ofNullable(persistedGroup);
  }

  @Override
  public Optional<Long> getGroupKeyByName(final String groupName) {
    this.groupName.wrapString(groupName);
    final var groupKey = groupByNameColumnFamily.get(this.groupName);
    return Optional.ofNullable(groupKey).map(key -> key.inner().getValue());
  }

  @Override
  public Optional<EntityType> getEntityType(final long groupKey, final String entityKey) {
    this.groupKey.wrapLong(groupKey);
    this.entityKey.wrapString(entityKey);
    final var entityType = entityTypeByGroupColumnFamily.get(fkGroupKeyAndEntityKey);
    return Optional.ofNullable(entityType).map(EntityTypeValue::getEntityType);
  }

  @Override
  public Map<EntityType, List<String>> getEntitiesByType(final long groupKey) {
    this.groupKey.wrapLong(groupKey);
    final Map<EntityType, List<String>> entitiesMap = new HashMap<>();
    entityTypeByGroupColumnFamily.whileEqualPrefix(
        fkGroupKey,
        (compositeKey, value) -> {
          final var entityType = value.getEntityType();
          final var entityKey = compositeKey.second().toString();
          entitiesMap.computeIfAbsent(entityType, k -> new ArrayList<>()).add(entityKey);
        });
    return entitiesMap;
  }
}
