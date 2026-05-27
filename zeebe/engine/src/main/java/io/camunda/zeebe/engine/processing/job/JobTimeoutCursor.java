/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex;

/**
 * Resume cursor for {@link JobTimeoutCheckScheduler}. Carries both the entry to resume from and the
 * execution timestamp the previous run started with — keeping the timestamp inside the cursor
 * preserves the invariant that all entries within one continuation are evaluated against the same
 * {@code now}.
 */
public record JobTimeoutCursor(long executionTimestamp, DeadlineIndex resumeFrom) {}
