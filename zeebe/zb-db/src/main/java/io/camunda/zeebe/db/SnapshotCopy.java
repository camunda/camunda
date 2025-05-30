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

public interface SnapshotCopy {

  void withContexts(final Path fromPath, final Path toDBPath, final CopyContextConsumer consumer);

  void copySnapshot(Path fromPath, Path toPath, Set<ColumnFamilyScope> scope) throws Exception;

  interface CopyContextConsumer {
    void accept(
        ZeebeTransactionDb<ZbColumnFamilies> fromDB,
        TransactionContext fromCtx,
        ZeebeTransactionDb<ZbColumnFamilies> toDB,
        TransactionContext toCtx);
  }
}
