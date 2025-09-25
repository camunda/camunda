/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.user;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.common.state.immutable.UserState;
import io.camunda.zeebe.engine.common.state.mutable.MutableUserState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.util.VisibleForTesting;
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
  public void deleteByUsername(final String username) {
    this.username.wrapString(username);
    usersColumnFamily.deleteExisting(this.username);
  }

  @Override
  public void deleteByUserKey(final long userKey) {
    this.userKey.wrapLong(userKey);
    userKeyByUsernameColumnFamily.deleteExisting(this.userKey);
  }

  @Override
  public Optional<PersistedUser> getUser(final String username) {
    this.username.wrapString(username);
    return Optional.ofNullable(usersColumnFamily.get(this.username, PersistedUser::new));
  }

  @Override
  public Optional<PersistedUser> getUser(final long userKey) {
    this.userKey.wrapLong(userKey);
    final var username = userKeyByUsernameColumnFamily.get(this.userKey);

    return Optional.ofNullable(username)
        .flatMap(dbUsername -> getUser(dbUsername.inner().toString()));
  }

  @VisibleForTesting
  public ColumnFamily<DbString, PersistedUser> getUsersColumnFamily() {
    return usersColumnFamily;
  }

  @VisibleForTesting
  public ColumnFamily<DbLong, DbForeignKey<DbString>> getUserKeyByUsernameColumnFamily() {
    return userKeyByUsernameColumnFamily;
  }
}
