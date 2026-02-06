/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel.HistoryDeletionTypeDbModel;
import java.util.List;

/**
 * Represents a batch of db models to be deleted from the history.
 *
 * @param historyDeletionModels List of entities marked for deletion
 */
public record HistoryDeletionBatch(List<HistoryDeletionDbModel> historyDeletionModels) {

  public List<Long> getResourceKeys(final HistoryDeletionTypeDbModel historyDeletionType) {
    return historyDeletionModels.stream()
        .filter(model -> model.resourceType().equals(historyDeletionType))
        .map(HistoryDeletionDbModel::resourceKey)
        .toList();
  }
}
