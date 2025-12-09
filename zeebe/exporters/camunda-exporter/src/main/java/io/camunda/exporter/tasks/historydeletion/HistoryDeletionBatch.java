/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.util.Map;

/**
 * Represents a batch of resource IDs to be deleted from the history.
 *
 * @param ids Map of resource IDs marked for deletion and their corresponding deletion type.
 */
public record HistoryDeletionBatch(Map<String, HistoryDeletionType> ids) {}
