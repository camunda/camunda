/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.es.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.tasklist.entities.WorkflowEntity;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.schema.indices.WorkflowIndex;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkflowReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowReader.class);

  @Autowired private WorkflowIndex workflowIndex;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  /** Gets the workflow by id. */
  public WorkflowEntity getWorkflow(String workflowId) {
    final SearchRequest searchRequest =
        new SearchRequest(workflowIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(QueryBuilders.termQuery(WorkflowIndex.KEY, workflowId)));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().totalHits == 1) {
        return fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
      } else if (response.getHits().totalHits > 1) {
        throw new TasklistRuntimeException(
            String.format("Could not find unique workflow with id '%s'.", workflowId));
      } else {
        throw new TasklistRuntimeException(
            String.format("Could not find workflow with id '%s'.", workflowId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the workflow: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private WorkflowEntity fromSearchHit(String workflowString) {
    return ElasticsearchUtil.fromSearchHit(workflowString, objectMapper, WorkflowEntity.class);
  }
}
