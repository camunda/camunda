/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * Atomic multi-table commits over the lake's JDBC-backed Iceberg catalog.
 *
 * <p>An Iceberg commit is, at bottom, a compare-and-swap of one pointer: "table X's current
 * metadata file is A" becomes "is B", if and only if it is still A. The {@code JdbcCatalog} the
 * lake already uses implements that swap as a single conditional {@code UPDATE} against its {@code
 * iceberg_tables} row — which means multi-table atomicity is not a distributed-systems problem
 * here, it is <em>N conditional updates inside one database transaction</em>. This package
 * implements exactly that and nothing more.
 *
 * <p>Why it exists: one flush window produces files for several tables at once (a raw table plus
 * its derived metrics partials), and the alternative — N independent per-table commits — forces
 * every consumer of the stamps to reason about a "cut" as the minimum across tables, forces a fixed
 * commit order, and leaves a crash window in which tables durably disagree. One transaction deletes
 * all three complications.
 *
 * <p>The moving parts:
 *
 * <ul>
 *   <li>{@link io.camunda.analytics.lake.catalog.DeferredCommitTableOperations} — lets iceberg-core
 *       run its normal commit pipeline (snapshot production, manifest writing, the new metadata
 *       file) but <em>defers</em> the final pointer swap, capturing it as a {@link
 *       io.camunda.analytics.lake.catalog.StagedCommit} instead of touching the database.
 *   <li>{@link io.camunda.analytics.lake.catalog.LakeCommitCoordinator} — stages every table's
 *       change, then executes all captured swaps in one transaction against the same embedded H2
 *       database the catalog itself uses, in deterministic table order (deadlock avoidance),
 *       retrying the whole batch on conflict. It also owns the commit journal (one row per batch:
 *       which tables, which snapshots, which stamps) and the pending-deletes queue (files are never
 *       deleted inside a commit path — a database transaction cannot atomically remove a file, so
 *       intent is journaled in the same transaction and a sweeper deletes later).
 * </ul>
 *
 * <p>Storage stays immutable and write-ahead: metadata files are written to the warehouse
 * <em>before</em> any pointer references them, so a lost race or crash only ever orphans an
 * unreferenced file (queued for sweep), never corrupts a referenced one.
 *
 * <p>What this package deliberately is <em>not</em>: a catalog implementation. Table lifecycle
 * (create/load/drop, namespaces) stays with {@code JdbcCatalog}; this package only batches the
 * commit step. It writes to {@code JdbcCatalog}'s own {@code iceberg_tables} using the exact
 * conditional-update shape iceberg-core itself uses, so the two commit paths (single-table via the
 * catalog, batched via the coordinator) contend correctly on the same rows.
 */
package io.camunda.analytics.lake.catalog;
