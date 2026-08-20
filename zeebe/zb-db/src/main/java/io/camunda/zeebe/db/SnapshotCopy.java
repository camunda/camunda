/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

import io.camunda.zeebe.protocol.ColumnFamilyScope;
import java.nio.file.Path;
import java.util.Set;

/**
 * Component that allows to copy a set of columns from one snapshot to another snapshot. It contains
 * also method to compare two snapshots that can be useful when testing.
 */
public interface SnapshotCopy {
  /**
   * Copies the content of a snapshot from a path to another path, copying only the columns with a
   * certain scope
   *
   * @param fromPath the path of the snapshot to copy from
   * @param toPath the path where the copied snapshot will be located
   * @param scope the scope of the columns to copy
   */
  void copySnapshot(Path fromPath, Path toPath, Set<ColumnFamilyScope> scope);
}
