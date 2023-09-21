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
import org.opensearch.client.opensearch.tasks.TaskExecutingNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore.SYSTEM_TASKS_INDEX;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.ExceptionHelper.withIOException;

@Component
@Conditional(OpensearchCondition.class)
public class OpensearchTaskStore implements TaskStore {
  int MAX_TASKS_ENTRIES = 2_000;

  String DESCRIPTION_PREFIX_FROM_INDEX = "reindex from [";
  String DESCRIPTION_PREFIX_TO_INDEX = "to [";
  String TASK_ACTION = "task.action";
  String TASK_ACTION_INDICES_REINDEX = "indices:data/write/reindex";

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Override
  public List<String> getRunningReindexTasksIdsFor(String fromIndex, String toIndex) throws IOException {
    if(! withIOException(() -> richOpenSearchClient.index().indexExists(SYSTEM_TASKS_INDEX)) || fromIndex == null || toIndex == null) {
      return List.of();
    }

    Function<String, Boolean> descriptionContainsReindexFromTo = desc -> desc != null &&
      desc.contains(DESCRIPTION_PREFIX_FROM_INDEX + fromIndex) &&
      desc.contains(DESCRIPTION_PREFIX_TO_INDEX + toIndex);

    var searchRequestBuilder = searchRequestBuilder(SYSTEM_TASKS_INDEX)
      .query(term(TASK_ACTION, TASK_ACTION_INDICES_REINDEX))
      .size(MAX_TASKS_ENTRIES);

    return withIOException(() -> richOpenSearchClient.doc().searchValues(searchRequestBuilder, TaskExecutingNode.class))
      .stream()
      .flatMap(taskNode -> taskNode.tasks().entrySet().stream())
      .filter( idState -> descriptionContainsReindexFromTo.apply(idState.getValue().description()))
      .map(Map.Entry::getKey)
      .toList();
  }
}
