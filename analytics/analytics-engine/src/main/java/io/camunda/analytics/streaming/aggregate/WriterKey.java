/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

/**
 * A grouping key scoped to a single writer (source partition). The Stage-1 combiner groups by
 * {@code (key, writer)} so each cell's partial folds only one source partition's instances — that
 * is the stable per-writer identity Stage 2's slots overwrite and merge across. Facts are still
 * <em>routed</em> by the inner {@code key} alone (see {@code FactPublishSink}), so all writers of a
 * cell converge on one Stage-2 consumer.
 *
 * @param <K> the base grouping key type
 */
public record WriterKey<K>(K key, int writer) {}
