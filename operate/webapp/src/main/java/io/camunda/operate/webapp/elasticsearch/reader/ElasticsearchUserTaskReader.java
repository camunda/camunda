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
import static io.camunda.operate.util.ElasticsearchUtil.whereToSearch;
import static org.elasticsearch.index.query.QueryBuilders.*;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.ScrollException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.reader.UserTaskReader;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.SnapshotTaskVariableEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchUserTaskReader extends AbstractReader implements UserTaskReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchUserTaskReader.class);
  private static final TermQueryBuilder TASK_QUERY =
      termQuery(TaskTemplate.JOIN_FIELD_NAME, TaskJoinRelationshipType.TASK.getType());
  private static final Query TASK_QUERY_ES8 =
      ElasticsearchUtil.termsQuery(
          TaskTemplate.JOIN_FIELD_NAME, TaskJoinRelationshipType.TASK.getType());

  private final TaskTemplate taskTemplate;
  private final SnapshotTaskVariableTemplate snapshotTaskVariableTemplate;

  public ElasticsearchUserTaskReader(
      final TaskTemplate taskTemplate,
      final SnapshotTaskVariableTemplate snapshotTaskVariableTemplate) {
    this.taskTemplate = taskTemplate;
    this.snapshotTaskVariableTemplate = snapshotTaskVariableTemplate;
  }

  @Override
  public List<TaskEntity> getUserTasks() {
    LOGGER.debug("retrieve all user tasks");
    try {
      final var searchRequestBuilder =
          new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
              .index(whereToSearch(taskTemplate, ALL))
              .query(ElasticsearchUtil.constantScoreQuery(TASK_QUERY_ES8));

      return ElasticsearchUtil.scrollAllStream(es8client, searchRequestBuilder, TaskEntity.class)
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .toList();
    } catch (final ScrollException e) {
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

    final var query =
        ElasticsearchUtil.constantScoreQuery(
            ElasticsearchUtil.termsQuery(SnapshotTaskVariableTemplate.TASK_ID, taskKey));

    final var searchRequestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(snapshotTaskVariableTemplate, ALL))
            .query(query);
    try {
      return ElasticsearchUtil.scrollAllStream(
              es8client, searchRequestBuilder, SnapshotTaskVariableEntity.class)
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .toList();
    } catch (final ScrollException e) {
      final String message =
          String.format("Exception occurred, while obtaining user task list: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}
