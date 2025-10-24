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
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import java.util.Optional;

public class DbGroupState implements MutableGroupState {

  private final DbString groupId;
  private final PersistedGroup persistedGroup = new PersistedGroup();
  private final ColumnFamily<DbString, PersistedGroup> groupColumnFamily;

  public DbGroupState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    groupId = new DbString();
    groupColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.GROUPS, transactionContext, groupId, new PersistedGroup());
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
  public void delete(final String groupId) {
    this.groupId.wrapString(groupId);
    groupColumnFamily.deleteExisting(this.groupId);
  }

  @Override
  public Optional<PersistedGroup> get(final String groupId) {
    this.groupId.wrapString(groupId);
    final var persistedGroup = groupColumnFamily.get(this.groupId, PersistedGroup::new);
    return Optional.ofNullable(persistedGroup);
  }
}
