/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.upgrade.client;

import java.io.IOException;

public abstract class AbstractDatabaseSchemaTestClient {

  public static final String SNAPSHOT_REPOSITORY_NAME = "my_backup";
  public static final String SNAPSHOT_NAME_1 = "snapshot_1";
  public static final String SNAPSHOT_NAME_2 = "snapshot_2";
  public static final String OPTIMIZE_INDEX_PREFIX = "optimize";
  public static final String DEFAULT_OPTIMIZE_INDEX_PATTERN =
      OPTIMIZE_INDEX_PREFIX + "-*,-" + OPTIMIZE_INDEX_PREFIX + "-update-log*";
  protected static final String[] SETTINGS_FILTER =
      new String[] {
        "index.analysis.*",
        "index.number_of_shards",
        "index.number_of_replicas",
        "index.max_ngram_diff",
        "index.mapping.*",
        "index.refresh_interval"
      };
  protected final String name;

  public AbstractDatabaseSchemaTestClient(final String name) {
    this.name = name;
  }

  public abstract void close() throws IOException;

  public abstract void refreshAll() throws IOException;

  public abstract void cleanIndicesAndTemplates() throws IOException;

  public abstract void createSnapshotRepository() throws IOException;

  public abstract void deleteSnapshotRepository() throws IOException;

  public abstract void createSnapshotOfOptimizeIndices() throws IOException;

  public abstract void createAsyncSnapshot() throws IOException;

  public abstract void restoreSnapshot() throws IOException;

  public abstract void deleteSnapshot() throws IOException;

  public abstract void deleteAsyncSnapshot() throws IOException;

  public abstract void deleteSnapshot(final String snapshotName) throws IOException;
}
