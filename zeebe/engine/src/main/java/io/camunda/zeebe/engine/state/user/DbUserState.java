/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.user;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import org.agrona.DirectBuffer;

public class DbUserState implements UserState, MutableUserState {

  private final PersistedUser persistedUser = new PersistedUser();

  private final DbString username;
  private final DbLong userKey;
  private final ColumnFamily<DbString, DbLong> usernameToKeyColumnFamily;
  private final ColumnFamily<DbKey, PersistedUser> keyToUserColumnFamily;

  public DbUserState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    username = new DbString();
    userKey = new DbLong();

    usernameToKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.USERNAME_BY_USER_KEY, transactionContext, username, userKey);

    keyToUserColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.USERS, transactionContext, userKey, persistedUser);
  }

  @Override
  public void create(final long key, final UserRecord user) {
    username.wrapBuffer(user.getUsernameBuffer());
    userKey.wrapLong(key);
    persistedUser.setUser(user);

    usernameToKeyColumnFamily.insert(username, userKey);
    keyToUserColumnFamily.insert(userKey, persistedUser);
  }

  @Override
  public UserRecord getUser(final DirectBuffer username) {
    this.username.wrapBuffer(username);
    final var key = usernameToKeyColumnFamily.get(this.username);

    if (key == null) {
      return null;
    }

    final var user = keyToUserColumnFamily.get(key);
    return user == null ? null : user.getUser().copy();
  }

  @Override
  public UserRecord getUser(final String username) {
    return getUser(wrapString(username));
  }
}
