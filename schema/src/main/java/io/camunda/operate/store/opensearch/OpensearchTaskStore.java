/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.TaskStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
@Conditional(OpensearchCondition.class)
public class OpensearchTaskStore implements TaskStore {

  String DESCRIPTION_PREFIX_FROM_INDEX = "reindex from [";
  String DESCRIPTION_PREFIX_TO_INDEX = "to [";
  String TASK_ACTION_INDICES_REINDEX = "indices:data/write/reindex";

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Override
  public List<String> getRunningReindexTasksIdsFor(String fromIndex, String toIndex) throws IOException {
    if(fromIndex == null || toIndex == null) {
      return List.of();
    }

    var id2taskInfo = richOpenSearchClient.task().tasksWithActions(List.of(TASK_ACTION_INDICES_REINDEX));
    Function<String, Boolean> descriptionContainsReindexFromTo = desc -> desc != null &&
      desc.contains(DESCRIPTION_PREFIX_FROM_INDEX + fromIndex) &&
      desc.contains(DESCRIPTION_PREFIX_TO_INDEX + toIndex);

    return id2taskInfo.entrySet().stream()
      .filter( id2TaskInfo -> descriptionContainsReindexFromTo.apply(id2TaskInfo.getValue().description()))
      .map(Map.Entry::getKey)
      .toList();
  }
}
