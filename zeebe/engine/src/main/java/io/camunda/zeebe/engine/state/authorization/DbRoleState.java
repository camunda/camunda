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
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DbRoleState implements MutableRoleState {

  private final DbString roleId;
  private final PersistedRole persistedRole = new PersistedRole();
  private final ColumnFamily<DbString, PersistedRole> roleColumnFamily;

  private final DbForeignKey<DbString> fkRoleId;
  private final DbString entityId;
  private final DbCompositeKey<DbForeignKey<DbString>, DbString> fkRoleIdAndEntityId;
  private final EntityTypeValue entityTypeValue = new EntityTypeValue();
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbString>, DbString>, EntityTypeValue>
      entityTypeByRoleColumnFamily;

  public DbRoleState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    roleId = new DbString();
    roleColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ROLES, transactionContext, roleId, new PersistedRole());

    fkRoleId = new DbForeignKey<>(roleId, ZbColumnFamilies.ROLES);
    entityId = new DbString();
    fkRoleIdAndEntityId = new DbCompositeKey<>(fkRoleId, entityId);
    entityTypeByRoleColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITY_BY_ROLE,
            transactionContext,
            fkRoleIdAndEntityId,
            entityTypeValue);
  }

  @Override
  public void create(final RoleRecord roleRecord) {
    roleId.wrapString(roleRecord.getRoleId());
    persistedRole.from(roleRecord);
    roleColumnFamily.insert(roleId, persistedRole);
  }

  @Override
  public void update(final RoleRecord roleRecord) {
    // retrieve record from the state
    roleId.wrapString(String.valueOf(roleRecord.getRoleKey()));
    final var persistedRole = roleColumnFamily.get(roleId);

    persistedRole.setRoleKey(roleRecord.getRoleKey()).setName(roleRecord.getName());
    roleColumnFamily.update(roleId, persistedRole);
  }

  @Override
  public void addEntity(final RoleRecord roleRecord) {
    roleId.wrapString(roleRecord.getRoleId());
    entityId.wrapString(roleRecord.getEntityId());
    entityTypeValue.setEntityType(roleRecord.getEntityType());
    entityTypeByRoleColumnFamily.insert(fkRoleIdAndEntityId, entityTypeValue);
  }

  @Override
  public void removeEntity(final long roleKey, final long entityKey) {
    roleId.wrapString(String.valueOf(roleKey));
    entityId.wrapString(String.valueOf(entityKey));
    entityTypeByRoleColumnFamily.deleteExisting(fkRoleIdAndEntityId);
  }

  @Override
  public void delete(final RoleRecord roleRecord) {
    roleId.wrapString(String.valueOf(roleRecord.getRoleKey()));
    // remove all entities associated with the role
    entityTypeByRoleColumnFamily.whileEqualPrefix(
        fkRoleId,
        (compositeKey, entityTypeValue) -> {
          entityTypeByRoleColumnFamily.deleteExisting(compositeKey);
        });
    // remove the role
    roleColumnFamily.deleteExisting(roleId);
  }

  // TODO remove this method after the key > id refactoring
  @Override
  public Optional<PersistedRole> getRole(final long roleKey) {
    return getRole(String.valueOf(roleKey));
  }

  @Override
  public Optional<PersistedRole> getRole(final String roleId) {
    this.roleId.wrapString(roleId);
    final var persistedRole = roleColumnFamily.get(this.roleId);
    return Optional.ofNullable(persistedRole);
  }

  // TODO remove method
  @Override
  public Optional<EntityType> getEntityType(final long roleKey, final long entityKey) {
    roleId.wrapString(String.valueOf(roleKey));
    entityId.wrapString(String.valueOf(entityKey));
    final var result = entityTypeByRoleColumnFamily.get(fkRoleIdAndEntityId);
    return Optional.ofNullable(result).map(EntityTypeValue::getEntityType);
  }

  @Override
  public Optional<EntityType> getEntityType(final String roleId, final String entityId) {
    this.roleId.wrapString(roleId);
    this.entityId.wrapString(entityId);
    final var result = entityTypeByRoleColumnFamily.get(fkRoleIdAndEntityId);
    return Optional.ofNullable(result).map(EntityTypeValue::getEntityType);
  }

  // TODO remove method
  @Override
  public Map<EntityType, List<Long>> getEntitiesByType(final long roleKey) {
    final Map<EntityType, List<Long>> entitiesMap = new HashMap<>();
    roleId.wrapString(String.valueOf(roleKey));
    entityTypeByRoleColumnFamily.whileEqualPrefix(
        fkRoleId,
        (compositeKey, entityTypeValue) -> {
          final var entityType = entityTypeValue.getEntityType();
          final var entityId = compositeKey.second().toString();
          entitiesMap.putIfAbsent(entityType, new ArrayList<>());
          entitiesMap.get(entityType).add(Long.valueOf(entityId));
        });
    return entitiesMap;
  }

  @Override
  public Map<EntityType, List<String>> getEntitiesByType(final String roleId) {
    final Map<EntityType, List<String>> entitiesMap = new HashMap<>();
    this.roleId.wrapString(roleId);
    entityTypeByRoleColumnFamily.whileEqualPrefix(
        fkRoleId,
        (compositeKey, entityTypeValue) -> {
          final var entityType = entityTypeValue.getEntityType();
          final var entityId = compositeKey.second().toString();
          entitiesMap.putIfAbsent(entityType, new ArrayList<>());
          entitiesMap.get(entityType).add(entityId);
        });
    return entitiesMap;
  }
}
