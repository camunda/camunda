/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

import io.camunda.zeebe.protocol.EnumValue;
import java.io.File;

/**
 * Represents the zeebe database factory. The {@link ColumnFamilyNames} has to be an enum and
 * specifies the different column families for the zeebe database.
 *
 * @param <ColumnFamilyNames> the names of the column families
 */
public interface ZeebeDbFactory<ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue> {

  /**
   * Creates a zeebe database in the given directory.
   *
   * @param pathName the path where the database should be created
   * @return the created zeebe database
   */
  ZeebeDb<ColumnFamilyNames> createDb(File pathName);

  /**
   * Opens an existing DB in read-only mode for the sole purpose of creating snapshots from it.
   *
   * <p>NOTE: if a read-only DB is required in the future that allows actually reading, then this
   * can be extended to do so. However, keep in mind that you cannot use transactions on such DBs,
   * and it might be better to do this when we've moved away from the transaction DB family.
   *
   * @param path the path to the existing database
   * @return a snapshot-able DB
   */
  ZeebeDb<ColumnFamilyNames> openSnapshotOnlyDb(final File path);
}
