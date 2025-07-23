/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.multiinstance.PersistedInputCollection;
import io.camunda.zeebe.engine.state.mutable.MutableMultiInstanceState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class DbMultiInstanceState implements MutableMultiInstanceState {

  private final DbLong multiInstanceKey;
  private final PersistedInputCollection inputCollection;
  private final ColumnFamily<DbLong, PersistedInputCollection> inputCollectionColumnFamily;

  public DbMultiInstanceState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    multiInstanceKey = new DbLong();
    inputCollection = new PersistedInputCollection();
    inputCollectionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MULTI_INSTANCE_INPUT_COLLECTION,
            transactionContext,
            multiInstanceKey,
            inputCollection);
  }

  @Override
  public void insertInputCollection(
      final long multiInstanceKey, final List<DirectBuffer> inputCollection) {
    this.multiInstanceKey.wrapLong(multiInstanceKey);
    this.inputCollection.setInputCollection(inputCollection);
    inputCollectionColumnFamily.insert(this.multiInstanceKey, this.inputCollection);
  }

  @Override
  public void deleteInputCollection(final long multiInstanceKey) {
    this.multiInstanceKey.wrapLong(multiInstanceKey);
    inputCollectionColumnFamily.deleteIfExists(this.multiInstanceKey);
  }

  @Override
  public Optional<List<DirectBuffer>> getInputCollection(final long multiInstanceKey) {
    this.multiInstanceKey.wrapLong(multiInstanceKey);
    final var persistedInputCollection = inputCollectionColumnFamily.get(this.multiInstanceKey);

    if (persistedInputCollection == null) {
      return Optional.empty();
    }

    return Optional.of(persistedInputCollection.getInputCollection());
  }
}
