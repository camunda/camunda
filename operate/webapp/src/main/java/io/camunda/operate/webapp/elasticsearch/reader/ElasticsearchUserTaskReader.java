/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static org.elasticsearch.index.query.QueryBuilders.*;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.reader.UserTaskReader;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskVariableEntity;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchUserTaskReader extends AbstractReader implements UserTaskReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchUserTaskReader.class);

  private final TaskTemplate userTaskTemplate;

  public ElasticsearchUserTaskReader(final TaskTemplate userTaskTemplate) {
    this.userTaskTemplate = userTaskTemplate;
  }

  @Override
  public List<TaskEntity> getUserTasks() {
    LOGGER.debug("retrieve all user tasks");
    try {
      final QueryBuilder query = matchAllQuery();
      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(userTaskTemplate, ALL)
              .source(new SearchSourceBuilder().query(constantScoreQuery(query)));
      return scroll(searchRequest, TaskEntity.class);
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining user task list: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Optional<TaskEntity> getUserTaskByFlowNodeInstanceKey(final long flowNodeInstanceKey) {
    LOGGER.debug("Get UserTask by flowNodeInstanceKey {}", flowNodeInstanceKey);
    try {
      final QueryBuilder query = termQuery(TaskTemplate.FLOW_NODE_INSTANCE_ID, flowNodeInstanceKey);
      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(userTaskTemplate, ALL)
              .source(new SearchSourceBuilder().query(constantScoreQuery(query)));
      final var hits = tenantAwareClient.search(searchRequest).getHits();
      if (hits.getTotalHits().value == 1) {
        return Optional.of(
            ElasticsearchUtil.mapSearchHits(hits.getHits(), objectMapper, TaskEntity.class).get(0));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining user task list: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return Optional.empty();
  }

  @Override
  public List<TaskVariableEntity> getUserTaskCompletedVariables(final long flowNodeInstanceKey) {
    LOGGER.debug("Get UserTask Completed Variables by flowNodeInstanceKey {}", flowNodeInstanceKey);

    final HasParentQueryBuilder hasParentQuery =
        new HasParentQueryBuilder(
            "task", // Parent type as defined in your index
            QueryBuilders.termQuery("id", flowNodeInstanceKey), // Parent ID
            false);

    // Make sure `name` field exists, indicating only variables are present in the result set
    final ExistsQueryBuilder existsQuery = QueryBuilders.existsQuery("name");

    final BoolQueryBuilder combinedQuery =
        QueryBuilders.boolQuery().must(hasParentQuery).must(existsQuery);
    try {
      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(userTaskTemplate, ALL)
              .source(new SearchSourceBuilder().query(constantScoreQuery(combinedQuery)));
      final var hits = tenantAwareClient.search(searchRequest).getHits();
      if (hits.getTotalHits().value > 0) {
        return ElasticsearchUtil.mapSearchHits(
            hits.getHits(), objectMapper, TaskVariableEntity.class);
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining user task list: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return List.of();
  }
}
