/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import org.jspecify.annotations.Nullable;

/**
 * The keys of one column family from {@code from}, included, to {@code to}, excluded. A missing
 * bound means the range doesn't stop on that side.
 *
 * <p>The range carries the column family's table as plain values, because the mapper reads them by
 * name.
 */
record StateRange(
    int partitionId,
    String table,
    boolean shared,
    int columnFamily,
    byte @Nullable [] from,
    byte @Nullable [] to,
    boolean descending) {

  StateRange(
      final int partitionId,
      final int columnFamily,
      final byte @Nullable [] from,
      final byte @Nullable [] to,
      final boolean descending) {
    this(
        partitionId,
        StateTables.of(columnFamily).name(),
        StateTables.of(columnFamily).shared(),
        columnFamily,
        from,
        to,
        descending);
  }
}
