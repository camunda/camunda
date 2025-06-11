/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransactionDb;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.nio.file.Path;
import java.util.Set;

/**
 * Component that allows to copy a set of columns from one snapshot to another snapshot. It contains
 * also method to compare two snapshots that can be useful when testing.
 */
public interface SnapshotCopy {

  /**
   * Helper function that can be used to compare or copy two snapshots. Particularly useful for
   * testing.
   *
   * @param fromPath path of the readonly snapshot that will be associated to "from" arguments in
   *     {@link CopyContextConsumer}
   * @param toDBPath path of the snapshot that will be associated to "to" arguments in {@link
   *     CopyContextConsumer}
   * @param consumer that can access both DB instances.
   */
  void withContexts(final Path fromPath, final Path toDBPath, final CopyContextConsumer consumer);

  /**
   * Copies the content of a snapshot from a path to another path, copying only the columns with a
   * certain scope
   *
   * @param fromPath the path of the snapshot to copy from
   * @param toPath the path where the copied snapshot will be located
   * @param scope the scope of the columns to copy
   */
  void copySnapshot(Path fromPath, Path toPath, Set<ColumnFamilyScope> scope) throws Exception;

  interface CopyContextConsumer {

    /**
     * Callback that will be invoked when the two DBs are opened. The arguments of this function
     * cannot escape this method, as they will be closed once this method returns.
     *
     * @param fromDB DB located at fromPath
     * @param fromCtx transaction context at fromPath
     * @param toDB DB located at toPath
     * @param toCtx transaction context at toPath
     */
    void accept(
        ZeebeTransactionDb<ZbColumnFamilies> fromDB,
        TransactionContext fromCtx,
        ZeebeTransactionDb<ZbColumnFamilies> toDB,
        TransactionContext toCtx);
  }
}
