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
  private final DbLong entityKey;
  private final DbCompositeKey<DbForeignKey<DbString>, DbLong> fkRoleIdAndEntityKey;
  private final EntityTypeValue entityTypeValue = new EntityTypeValue();
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbString>, DbLong>, EntityTypeValue>
      entityTypeByRoleColumnFamily;

  public DbRoleState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    roleId = new DbString();
    roleColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ROLES, transactionContext, roleId, new PersistedRole());

    fkRoleId = new DbForeignKey<>(roleId, ZbColumnFamilies.ROLES);
    entityKey = new DbLong();
    fkRoleIdAndEntityKey = new DbCompositeKey<>(fkRoleId, entityKey);
    entityTypeByRoleColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITY_BY_ROLE,
            transactionContext,
            fkRoleIdAndEntityKey,
            entityTypeValue);
  }

  @Override
  public void create(final RoleRecord roleRecord) {
    roleId.wrapString(String.valueOf(roleRecord.getRoleKey()));
    // TODO introduce wrap method on PersistedRole
    persistedRole.setRoleKey(roleRecord.getRoleKey()).setName(roleRecord.getName());
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
    roleId.wrapString(String.valueOf(roleRecord.getRoleKey()));
    entityKey.wrapLong(roleRecord.getEntityKey());
    entityTypeValue.setEntityType(roleRecord.getEntityType());
    entityTypeByRoleColumnFamily.insert(fkRoleIdAndEntityKey, entityTypeValue);
  }

  @Override
  public void removeEntity(final long roleKey, final long entityKey) {
    roleId.wrapString(String.valueOf(roleKey));
    this.entityKey.wrapLong(entityKey);
    entityTypeByRoleColumnFamily.deleteExisting(fkRoleIdAndEntityKey);
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

  @Override
  public Optional<PersistedRole> getRole(final long roleKey) {
    roleId.wrapString(String.valueOf(roleKey));
    final var persistedRole = roleColumnFamily.get(roleId);
    return Optional.ofNullable(persistedRole);
  }

  // TODO remove this method
  @Override
  public Optional<Long> getRoleKeyByName(final String roleName) {
    this.roleName.wrapString(roleName);
    final var fkRoleKey = roleByNameColumnFamily.get(this.roleName);
    return fkRoleKey != null ? Optional.of(fkRoleKey.inner().getValue()) : Optional.empty();
  }

  @Override
  public Optional<EntityType> getEntityType(final long roleKey, final long entityKey) {
    roleId.wrapString(String.valueOf(roleKey));
    this.entityKey.wrapLong(entityKey);
    final var result = entityTypeByRoleColumnFamily.get(fkRoleIdAndEntityKey);
    return Optional.ofNullable(result).map(EntityTypeValue::getEntityType);
  }

  @Override
  public Map<EntityType, List<Long>> getEntitiesByType(final long roleKey) {
    final Map<EntityType, List<Long>> entitiesMap = new HashMap<>();
    roleId.wrapString(String.valueOf(roleKey));
    entityTypeByRoleColumnFamily.whileEqualPrefix(
        fkRoleId,
        (compositeKey, entityTypeValue) -> {
          final var entityType = entityTypeValue.getEntityType();
          final var entityKey = compositeKey.second().getValue();
          entitiesMap.putIfAbsent(entityType, new ArrayList<>());
          entitiesMap.get(entityType).add(entityKey);
        });
    return entitiesMap;
  }
}
