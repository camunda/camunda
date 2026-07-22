/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * L0 sink rung 1: a {@link io.camunda.analytics.lake.sink.BatchEncoder} implementation over
 * iceberg-parquet's generic {@code org.apache.iceberg.data.Record} writer machinery, plus the
 * per-family-day file routing ({@link io.camunda.analytics.lake.sink.encode.DayRouter}) and local
 * {@link io.camunda.analytics.lake.sink.FileSink} that feed it.
 *
 * <p>Nothing in this package is schema-specific: every column mapping is derived from {@link
 * io.camunda.analytics.lake.sink.TableSchema}'s field ids, resolved once per file against the
 * caller-supplied {@code org.apache.iceberg.Schema} (see {@link
 * io.camunda.analytics.lake.sink.encode.BatchRowView}).
 */
package io.camunda.analytics.lake.sink.encode;
