/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.user;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.List;
import java.util.Optional;

public class DbUserState implements UserState, MutableUserState {

  private final PersistedUser persistedUser = new PersistedUser();

  private final DbString username;
  private final DbLong userKey;
  private final DbForeignKey<DbString> fkUsername;
  private final ColumnFamily<DbLong, DbForeignKey<DbString>> userKeyByUsernameColumnFamily;
  private final ColumnFamily<DbString, PersistedUser> usersColumnFamily;

  public DbUserState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    username = new DbString();
    userKey = new DbLong();
    fkUsername = new DbForeignKey<>(username, ZbColumnFamilies.USERS);
    userKeyByUsernameColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.USERNAME_BY_USER_KEY, transactionContext, userKey, fkUsername);
    usersColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.USERS, transactionContext, username, new PersistedUser());
  }

  @Override
  public void create(final UserRecord user) {
    username.wrapBuffer(user.getUsernameBuffer());
    userKey.wrapLong(user.getUserKey());
    persistedUser.setUser(user);

    usersColumnFamily.insert(username, persistedUser);
    userKeyByUsernameColumnFamily.insert(userKey, fkUsername);
  }

  @Override
  public void update(final UserRecord user) {
    username.wrapBuffer(user.getUsernameBuffer());
    persistedUser.setUser(user);

    usersColumnFamily.update(username, persistedUser);
  }

  @Override
  public void delete(final String username) {
    this.username.wrapString(username);
    usersColumnFamily.deleteExisting(this.username);
  }

  @Override
  public void addTenantId(final String username, final String tenantId) {
    this.username.wrapString(username);
    final var persistedUser = usersColumnFamily.get(this.username);
    persistedUser.addTenantId(tenantId);
    usersColumnFamily.update(this.username, persistedUser);
  }

  @Override
  public void removeTenant(final String username, final String tenantId) {
    this.username.wrapString(username);
    final var persistedUser = usersColumnFamily.get(this.username);
    final List<String> tenantIds = persistedUser.getTenantIdsList();
    tenantIds.remove(tenantId);
    persistedUser.setTenantIdsList(tenantIds);
    usersColumnFamily.update(this.username, persistedUser);
  }

  @Override
  public void addGroup(final String username, final String groupId) {
    this.username.wrapString(username);
    final var persistedUser = usersColumnFamily.get(this.username);
    persistedUser.addGroupId(groupId);
    usersColumnFamily.update(this.username, persistedUser);
  }

  @Override
  public void removeGroup(final String username, final String groupId) {
    this.username.wrapString(username);
    final var persistedUser = usersColumnFamily.get(this.username);
    final List<String> groupIds = persistedUser.getGroupIdsList();
    groupIds.remove(groupId);
    persistedUser.setGroupIdsList(groupIds);
    usersColumnFamily.update(this.username, persistedUser);
  }

  @Override
  public Optional<PersistedUser> getUser(final String username) {
    this.username.wrapString(username);
    final var persistedUser = usersColumnFamily.get(this.username);

    if (persistedUser == null) {
      return Optional.empty();
    }
    return Optional.of(persistedUser.copy());
  }

  @Override
  public Optional<PersistedUser> getUser(final long userKey) {
    this.userKey.wrapLong(userKey);
    final var username = userKeyByUsernameColumnFamily.get(this.userKey);

    return Optional.ofNullable(username)
        .flatMap(dbUsername -> getUser(dbUsername.inner().toString()));
  }
}
