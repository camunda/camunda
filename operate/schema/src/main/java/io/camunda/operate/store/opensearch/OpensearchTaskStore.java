/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.TaskStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpensearchCondition.class)
public class OpensearchTaskStore implements TaskStore {

  String descriptionPrefixFromIndex = "reindex from [";
  String descriptionPrefixToIndex = "to [";
  String taskActionIndicesReindex = "indices:data/write/reindex";

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Override
  public List<String> getRunningReindexTasksIdsFor(String fromIndex, String toIndex)
      throws IOException {
    if (fromIndex == null || toIndex == null) {
      return List.of();
    }

    final var id2taskInfo =
        richOpenSearchClient.task().tasksWithActions(List.of(taskActionIndicesReindex));
    final Function<String, Boolean> descriptionContainsReindexFromTo =
        desc ->
            desc != null
                && desc.contains(descriptionPrefixFromIndex + fromIndex)
                && desc.contains(descriptionPrefixToIndex + toIndex);

    return id2taskInfo.entrySet().stream()
        .filter(
            id2TaskInfo ->
                descriptionContainsReindexFromTo.apply(id2TaskInfo.getValue().description()))
        .map(Map.Entry::getKey)
        .toList();
  }
}
