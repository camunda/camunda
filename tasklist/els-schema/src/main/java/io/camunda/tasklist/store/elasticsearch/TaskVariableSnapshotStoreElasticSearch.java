/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.templates.TasklistTaskVariableSnapshotTemplate;
import io.camunda.tasklist.store.TaskVariableSnapshotStore;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class TaskVariableSnapshotStoreElasticSearch implements TaskVariableSnapshotStore {

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired private TasklistTaskVariableSnapshotTemplate tasklistTaskVariableSnapshotTemplate;

  @Override
  public List<String> retrieveVariableByScopeKey(final String scopeKey) {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(tasklistTaskVariableSnapshotTemplate)
            .source(
                SearchSourceBuilder.searchSource()
                    .query(
                        termQuery(
                            TasklistTaskVariableSnapshotTemplate.VARIABLE_SCOPE_KEY, scopeKey))
                    .fetchField(TasklistTaskVariableSnapshotTemplate.ID));
    try {
      final var result = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final var result1 = ElasticsearchUtil.scrollIdsToList(searchRequest, esClient);
      return result1;
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }
}
