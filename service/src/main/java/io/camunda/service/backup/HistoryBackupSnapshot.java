/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.backup;

import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A single snapshot making up a history backup.
 *
 * <p>{@code state} stays a store-reported string rather than an enum: Elasticsearch and OpenSearch
 * report different vocabularies. It and {@code startTime} are absent when the backup was read
 * without snapshot detail.
 */
@NullMarked
public record HistoryBackupSnapshot(
    String snapshotName,
    @Nullable String state,
    @Nullable OffsetDateTime startTime,
    List<String> failures) {}
