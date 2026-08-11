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

/**
 * The outcome of scheduling a history backup: the requested id echoed back alongside the snapshot
 * names the store scheduled for it.
 *
 * <p>The id is echoed by the adapter from the request, not read back from the store — the
 * underlying take-backup response carries only the snapshot names.
 */
@NullMarked
public record HistoryBackupTaken(long backupId, List<String> scheduledSnapshots) {}
