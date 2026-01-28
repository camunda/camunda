/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.util.List;

/**
 * Represents a batch of entities to be deleted from the history.
 *
 * @param historyDeletionEntities List of entities marked for deletion
 */
public record HistoryDeletionBatch(List<HistoryDeletionEntity> historyDeletionEntities) {

  List<Long> getResourceKeys(final HistoryDeletionType historyDeletionType) {
    return historyDeletionEntities.stream()
        .filter(entity -> entity.getResourceType().equals(historyDeletionType))
        .map(HistoryDeletionEntity::getResourceKey)
        .toList();
  }

  List<String> getHistoryDeletionIds(final HistoryDeletionType historyDeletionType) {
    return historyDeletionEntities.stream()
        .filter(entity -> entity.getResourceType().equals(historyDeletionType))
        .map(HistoryDeletionEntity::getId)
        .toList();
  }
}
