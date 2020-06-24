/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.es.reader;

import static io.zeebe.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.zeebe.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.es.schema.templates.TaskTemplate;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.webapp.graphql.entity.TaskDTO;
import io.zeebe.tasklist.webapp.graphql.entity.TaskQueryDTO;
import io.zeebe.tasklist.webapp.rest.exception.NotFoundException;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskReader.class);

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private ObjectMapper objectMapper;

  public TaskDTO getTask(String id, List<String> fieldNames) {

    // TODO #104 define list of fields

    // TODO specity sourceFields to fetch
    final GetRequest getRequest = new GetRequest(taskTemplate.getAlias()).id(id);

    try {
      final GetResponse response = esClient.get(getRequest, RequestOptions.DEFAULT);
      if (!response.isExists()) {
        throw new NotFoundException(String.format("Task with id %s was not found", id));
      }
      final TaskEntity taskEntity =
          fromSearchHit(response.getSourceAsString(), objectMapper, TaskEntity.class);
      return TaskDTO.createFrom(taskEntity);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining task: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new TasklistRuntimeException(message, e);
    }
  }

  public List<TaskDTO> getTasks(TaskQueryDTO query, List<String> fieldNames) {

    final QueryBuilder esQuery = buildQuery(query);

    // TODO #104 define list of fields

    // TODO we can play around with query type here (2nd parameter), e.g. when we select for only
    // active tasks
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(taskTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(esQuery)
                    .sort(TaskTemplate.CREATION_TIME, SortOrder.DESC)
                //            .fetchSource(fieldNames.toArray(String[]::new), null)
                );

    try {
      final List<TaskEntity> taskEntities =
          ElasticsearchUtil.scroll(searchRequest, TaskEntity.class, objectMapper, esClient);
      return TaskDTO.createFrom(taskEntities);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining tasks: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new TasklistRuntimeException(message, e);
    }
  }

  private QueryBuilder buildQuery(TaskQueryDTO query) {
    QueryBuilder stateQ = null;
    if (query.getState() != null) {
      stateQ = termQuery(TaskTemplate.STATE, query.getState());
    }
    QueryBuilder assignedQ = null;
    QueryBuilder assigneeQ = null;
    if (query.getAssigned() != null) {
      if (query.getAssigned()) {
        assignedQ = existsQuery(TaskTemplate.ASSIGNEE);
        if (query.getAssignee() != null) {
          assigneeQ = termQuery(TaskTemplate.ASSIGNEE, query.getAssignee());
        }
      } else {
        assignedQ = boolQuery().mustNot(existsQuery(TaskTemplate.ASSIGNEE));
      }
    }
    QueryBuilder jointQ = joinWithAnd(stateQ, assignedQ, assigneeQ);
    if (jointQ == null) {
      jointQ = matchAllQuery();
    }
    return constantScoreQuery(jointQ);
  }
}
