/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.RawAccessibleDb;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.io.File;

/**
 * Wraps only the runtime DB ({@link #createDb(File)}), which the wrapped factory must return as a
 * {@link RawAccessibleDb}. Bulk copies ({@code createDb(path, false)}) and read-only snapshot DBs
 * stay on the wrapped factory: a bulk copy runs in one huge transaction, which would sit on the
 * heap here.
 */
public final class WriteBehindZeebeDbFactory<
        ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDbFactory<ColumnFamilyType> {

  private final ZeebeDbFactory<ColumnFamilyType> delegate;
  private final WriteBehindConfiguration config;
  private final ConsistencyChecksSettings consistencyChecks;

  /**
   * @param consistencyChecks the checks that the write-behind column families run; pass the same
   *     settings as the wrapped factory's
   */
  public WriteBehindZeebeDbFactory(
      final ZeebeDbFactory<ColumnFamilyType> delegate,
      final WriteBehindConfiguration config,
      final ConsistencyChecksSettings consistencyChecks) {
    this.delegate = delegate;
    this.config = config;
    this.consistencyChecks = consistencyChecks;
  }

  @Override
  public ZeebeDb<ColumnFamilyType> createDb(final File pathName, final boolean avoidFlush) {
    return delegate.createDb(pathName, avoidFlush);
  }

  @Override
  public ZeebeDb<ColumnFamilyType> createDb(final File pathName) {
    final var db = delegate.createDb(pathName);
    if (!(db instanceof final RawAccessibleDb<ColumnFamilyType> rawAccessibleDb)) {
      final var failure =
          new IllegalArgumentException(
              "Expected the wrapped factory to create a %s, but it created a %s"
                  .formatted(RawAccessibleDb.class.getSimpleName(), db.getClass().getName()));
      try {
        db.close();
      } catch (final Exception e) {
        failure.addSuppressed(e);
      }
      throw failure;
    }
    return new WriteBehindZeebeDb<>(rawAccessibleDb, config, consistencyChecks);
  }

  @Override
  public ZeebeDb<ColumnFamilyType> openSnapshotOnlyDb(final File path) {
    return delegate.openSnapshotOnlyDb(path);
  }
}
