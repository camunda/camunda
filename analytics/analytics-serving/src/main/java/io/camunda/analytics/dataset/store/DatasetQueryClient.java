/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

import java.util.List;

/**
 * The backend-neutral <b>read</b> seam of the serving store (mirroring OC's {@code
 * DocumentBasedSearchClient}): executes a {@link DatasetFetch} and returns the matching raw {@link
 * Cell}s (for a cube), or a {@link TableFetch} and returns the matching {@link TableRow}s (for a
 * table). Each backend transforms the neutral fetch into its native query (SQL for RDBMS, the
 * aggregation/search DSL for ES/OS); the {@link DatasetQueryExecutor} does the cube merge/finalize
 * on top, while a table is served as fetched — so this seam stays a thin filter-and-fetch.
 */
public interface DatasetQueryClient extends AutoCloseable {

  /** Fetches the raw cells of a cube at one tier (see {@link DatasetFetch}). */
  List<Cell> fetch(DatasetFetch fetch);

  /** Fetches the raw rows of a table (see {@link TableFetch}). */
  List<TableRow> fetchRows(TableFetch fetch);

  @Override
  void close();
}
