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
  private final DbString entityId;
  private final DbCompositeKey<DbForeignKey<DbString>, DbString> fkGroupIdAndEntityId;
  private final EntityTypeValue entityTypeValue = new EntityTypeValue();
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbString>, DbString>, EntityTypeValue>
      entityTypeByGroupColumnFamily;

  public DbGroupState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    groupId = new DbString();
    groupColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GROUPS, transactionContext, groupId, new PersistedGroup());

    fkGroupId = new DbForeignKey<>(groupId, ZbColumnFamilies.GROUPS);
    entityId = new DbString();
    fkGroupIdAndEntityId = new DbCompositeKey<>(fkGroupId, entityId);
    entityTypeByGroupColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITY_BY_GROUP,
            transactionContext,
            fkGroupIdAndEntityId,
            entityTypeValue);
  }

  @Override
  public void create(final GroupRecord group) {
    groupId.wrapString(group.getGroupId());
    persistedGroup.wrap(group);
    groupColumnFamily.insert(groupId, persistedGroup);
  }

  @Override
  public void update(final GroupRecord group) {
    groupId.wrapString(group.getGroupId());
    final var persistedGroup = groupColumnFamily.get(groupId);
    if (persistedGroup != null) {
      persistedGroup.copyFrom(group);
      groupColumnFamily.update(groupId, persistedGroup);
    }
  }

  @Override
  public void addEntity(final GroupRecord group) {
    groupId.wrapString(group.getGroupId());
    entityId.wrapString(String.valueOf(group.getEntityId()));
    entityTypeValue.setEntityType(group.getEntityType());
    entityTypeByGroupColumnFamily.insert(fkGroupIdAndEntityId, entityTypeValue);
  }

  @Override
  public void removeEntity(final String groupId, final String entityId) {
    this.groupId.wrapString(groupId);
    this.entityId.wrapString(entityId);
    entityTypeByGroupColumnFamily.deleteExisting(fkGroupIdAndEntityId);
  }

  @Override
  public void delete(final String groupId) {
    this.groupId.wrapString(groupId);

    // remove entries from ENTITY_BY_GROUP cf
    entityTypeByGroupColumnFamily.whileEqualPrefix(
        fkGroupId,
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
  public Optional<EntityType> getEntityType(final String groupId, final String entityId) {
    this.groupId.wrapString(groupId);
    this.entityId.wrapString(entityId);
    final var entityType = entityTypeByGroupColumnFamily.get(fkGroupIdAndEntityId);
    return Optional.ofNullable(entityType).map(EntityTypeValue::getEntityType);
  }

  @Override
  public Map<EntityType, List<String>> getEntitiesByType(final String groupId) {
    this.groupId.wrapString(groupId);
    final Map<EntityType, List<String>> entitiesMap = new HashMap<>();
    entityTypeByGroupColumnFamily.whileEqualPrefix(
        fkGroupId,
        (compositeKey, value) -> {
          final var entityType = value.getEntityType();
          final var entityId = compositeKey.second().toString();
          entitiesMap.computeIfAbsent(entityType, k -> new ArrayList<>()).add(entityId);
        });
    return entitiesMap;
  }
}
