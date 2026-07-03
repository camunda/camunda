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
 * Cell}s. Each backend transforms the neutral fetch into its native query (SQL for RDBMS, the
 * aggregation DSL for ES/OS) and maps rows back to cells; the {@link DatasetQueryExecutor} does the
 * merge/finalize on top, so this seam stays a thin filter-and-fetch.
 */
public interface DatasetQueryClient extends AutoCloseable {

  List<Cell> fetch(DatasetFetch fetch);

  @Override
  void close();
}
