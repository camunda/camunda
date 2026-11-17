/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.backup;

import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The state of a single history backup, aggregated over the snapshots that make it up. */
@NullMarked
public record HistoryBackupState(
    long backupId,
    HistoryBackupStateCode state,
    @Nullable String failureReason,
    List<HistoryBackupSnapshot> snapshots) {}
