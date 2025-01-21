/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.*;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.reader.UserTaskReader;
import io.camunda.webapps.schema.descriptors.tasklist.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.tasklist.SnapshotTaskVariableEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship.TaskJoinRelationshipType;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchUserTaskReader extends AbstractReader implements UserTaskReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchUserTaskReader.class);
  private static final TermQueryBuilder TASK_QUERY =
      termQuery(TaskTemplate.JOIN_FIELD_NAME, TaskJoinRelationshipType.TASK.getType());

  private final TaskTemplate taskTemplate;
  private final SnapshotTaskVariableTemplate snapshotTaskVariableTemplate;

  public ElasticsearchUserTaskReader(
      final TaskTemplate taskTemplate,
      @Qualifier("operateSnapshotTaskVariableTemplate")
          final SnapshotTaskVariableTemplate snapshotTaskVariableTemplate) {
    this.taskTemplate = taskTemplate;
    this.snapshotTaskVariableTemplate = snapshotTaskVariableTemplate;
  }

  @Override
  public List<TaskEntity> getUserTasks() {
    LOGGER.debug("retrieve all user tasks");
    try {
      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(taskTemplate, ALL)
              .source(new SearchSourceBuilder().query(constantScoreQuery(TASK_QUERY)));
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
      final QueryBuilder query =
          joinWithAnd(
              TASK_QUERY, termQuery(TaskTemplate.FLOW_NODE_INSTANCE_ID, flowNodeInstanceKey));
      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(taskTemplate, ALL)
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
  public List<SnapshotTaskVariableEntity> getUserTaskVariables(final long taskKey) {
    LOGGER.debug("Get UserTask Completed Variables by flowNodeInstanceKey {}", taskKey);

    final var userTaskKeyQuery =
        QueryBuilders.termQuery(SnapshotTaskVariableTemplate.TASK_ID, taskKey);
    try {
      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(snapshotTaskVariableTemplate, ALL)
              .source(new SearchSourceBuilder().query(constantScoreQuery(userTaskKeyQuery)));
      return ElasticsearchUtil.scroll(
          searchRequest, SnapshotTaskVariableEntity.class, objectMapper, esClient);
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining user task list: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}
