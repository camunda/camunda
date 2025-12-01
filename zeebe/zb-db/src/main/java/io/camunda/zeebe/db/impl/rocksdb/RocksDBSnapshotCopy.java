/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import io.camunda.zeebe.db.SnapshotCopy;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.nio.file.Path;
import java.util.Set;

public class RocksDBSnapshotCopy implements SnapshotCopy {

  private final ZeebeRocksDbFactory<ZbColumnFamilies> factory;

  public RocksDBSnapshotCopy(final ZeebeRocksDbFactory<ZbColumnFamilies> factory) {
    this.factory = factory;
  }

  @Override
  public void copySnapshot(
      final Path fromPath, final Path toPath, final Set<ColumnFamilyScope> scopes) {
    try (final var fromDB =
            (SnapshotOnlyDb<ZbColumnFamilies>) factory.openSnapshotOnlyDb(fromPath.toFile());
        final var toDB = factory.createDb(toPath.toFile(), false)) {
      fromDB.copySnapshot(toDB, scopes);
    }
  }
}
