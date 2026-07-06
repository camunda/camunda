/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

/**
 * How the {@link DatasetQueryExecutor} answers one meter (chosen per meter by the {@link
 * DatasetQueryPlanner} from the query's granularity and group-by):
 *
 * <ul>
 *   <li>{@link #DIRECT} — the read is 1:1 with stored cells (granularity == the tier's window and
 *       group-by == the full grain): each cell is one output row, so the store returns the stored
 *       column(s) finalized, with no aggregation and no merge.
 *   <li>{@link #PUSH_DOWN} — an additive meter that rolls up (coarser granularity or dropped
 *       dimensions): the store does the {@code GROUP BY} + {@code SUM}/{@code MIN}/{@code MAX} and
 *       returns finalized rows.
 *   <li>{@link #STREAM_MERGE} — a sketch (or any blob) that rolls up: the only path that streams
 *       the blobs and merges them in the application (Layer A). Finalized percentiles are not
 *       combinable, so a sketch cannot be pushed down.
 * </ul>
 */
public enum ReadStrategy {
  DIRECT,
  PUSH_DOWN,
  STREAM_MERGE
}
