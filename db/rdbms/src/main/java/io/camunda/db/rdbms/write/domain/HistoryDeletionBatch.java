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
import java.util.function.Predicate;

/**
 * Represents a batch of db models to be deleted from the history.
 *
 * @param historyDeletionModels List of entities marked for deletion
 */
public record HistoryDeletionBatch(List<HistoryDeletionDbModel> historyDeletionModels) {

  /**
   * Gets resource keys filtered by resource type.
   *
   * @param historyDeletionType The type of resource to filter by
   * @return List of resource keys matching the type
   */
  public List<Long> getResourceKeys(final HistoryDeletionTypeDbModel historyDeletionType) {
    return getResourceKeys(historyDeletionType, model -> true);
  }

  /**
   * Gets resource keys filtered by resource type and an additional predicate.
   *
   * @param historyDeletionType The type of resource to filter by
   * @param additionalFilter Additional predicate to filter the models
   * @return List of resource keys matching both the type and the additional filter
   */
  public List<Long> getResourceKeys(
      final HistoryDeletionTypeDbModel historyDeletionType,
      final Predicate<HistoryDeletionDbModel> additionalFilter) {
    return historyDeletionModels.stream()
        .filter(model -> model.resourceType().equals(historyDeletionType))
        .filter(additionalFilter)
        .map(HistoryDeletionDbModel::resourceKey)
        .toList();
  }

  /**
   * Gets models filtered by resource type and a set of resource keys.
   *
   * @param historyDeletionType The type of resource to filter by
   * @param resourceKeys The specific resource keys to include
   * @return List of models matching the type and the given resource keys
   */
  public List<HistoryDeletionDbModel> getModels(
      final HistoryDeletionTypeDbModel historyDeletionType, final List<Long> resourceKeys) {
    return historyDeletionModels.stream()
        .filter(model -> model.resourceType().equals(historyDeletionType))
        .filter(model -> resourceKeys.contains(model.resourceKey()))
        .toList();
  }
}
