/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.catalog;

import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.catalog.TableIdentifier;

/**
 * One table's deferred pointer swap, captured by {@link DeferredCommitTableOperations} instead of
 * being executed: "if {@code identifier}'s current metadata location is still {@code
 * expectedMetadataLocation}, advance it to {@code newMetadataLocation}". The new metadata file is
 * already durably written when this is captured — staging is write-ahead, only the swap waits.
 *
 * @param identifier the table, as the catalog knows it
 * @param expectedMetadataLocation the metadata location this commit was based on — the
 *     compare-and-swap predicate
 * @param newMetadataLocation the just-written metadata file the pointer should advance to
 * @param metadata the staged table metadata (for journaling: snapshot id etc.); never read from
 *     again for correctness — the file is the durable form
 */
public record StagedCommit(
    TableIdentifier identifier,
    String expectedMetadataLocation,
    String newMetadataLocation,
    TableMetadata metadata) {}
