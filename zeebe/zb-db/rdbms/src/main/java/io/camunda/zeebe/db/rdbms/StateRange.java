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
 */
record StateRange(
    int partitionId,
    int columnFamily,
    byte @Nullable [] from,
    byte @Nullable [] to,
    boolean descending) {}
