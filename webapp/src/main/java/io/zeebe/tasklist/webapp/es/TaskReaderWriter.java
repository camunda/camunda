/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.es;

import static io.zeebe.tasklist.util.CollectionUtil.asMap;
import static io.zeebe.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.zeebe.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.entities.TaskState;
import io.zeebe.tasklist.es.schema.templates.TaskTemplate;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.webapp.graphql.entity.TaskDTO;
import io.zeebe.tasklist.webapp.graphql.entity.TaskQueryDTO;
import io.zeebe.tasklist.webapp.rest.exception.NotFoundException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskReaderWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskReaderWriter.class);

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private ObjectMapper objectMapper;

  public TaskEntity getTask(String id) {
    return getTask(id, null);
  }

  private TaskEntity getTask(final String id, List<String> fieldNames) {
    // TODO #104 define list of fields and specify sourceFields to fetch
    final GetResponse response = getTaskRawResponse(id);
    if (!response.isExists()) {
      throw new NotFoundException(String.format("Task with id %s was not found", id));
    }

    return fromSearchHit(response.getSourceAsString(), objectMapper, TaskEntity.class);
  }

  /**
   * @param id
   * @param fieldNames list of field names to return. When null, return all fields.
   * @return
   */
  public TaskDTO getTaskDTO(String id, List<String> fieldNames) {
    final TaskEntity taskEntity = getTask(id, fieldNames);

    return TaskDTO.createFrom(taskEntity);
  }

  @NotNull
  public GetResponse getTaskRawResponse(final String id) {
    final GetRequest getRequest = new GetRequest(taskTemplate.getAlias()).id(id);

    try {
      return esClient.get(getRequest, RequestOptions.DEFAULT);
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
      } else {
        assignedQ = boolQuery().mustNot(existsQuery(TaskTemplate.ASSIGNEE));
      }
    }
    if (query.getAssignee() != null) {
      assigneeQ = termQuery(TaskTemplate.ASSIGNEE, query.getAssignee());
    }
    QueryBuilder jointQ = joinWithAnd(stateQ, assignedQ, assigneeQ);
    if (jointQ == null) {
      jointQ = matchAllQuery();
    }
    return constantScoreQuery(jointQ);
  }

  /**
   * Persist that task is completed even before the corresponding events are imported from Zeebe.
   *
   * @param taskBeforeRawResponse
   */
  public void persistTaskCompletion(GetResponse taskBeforeRawResponse) {
    try {
      // update task with optimistic locking
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(TaskTemplate.STATE, TaskState.COMPLETED);
      updateFields.put(TaskTemplate.COMPLETION_TIME, OffsetDateTime.now());

      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);
      final UpdateRequest updateRequest =
          new UpdateRequest(
                  taskTemplate.getMainIndexName(),
                  ElasticsearchUtil.ES_INDEX_TYPE,
                  taskBeforeRawResponse.getId())
              .doc(jsonMap)
              .setIfSeqNo(taskBeforeRawResponse.getSeqNo())
              .setIfPrimaryTerm(taskBeforeRawResponse.getPrimaryTerm());
      ElasticsearchUtil.executeUpdate(esClient, updateRequest);
    } catch (Exception e) {
      // we're OK with not updating the task here, it will be marked as completed within import
      LOGGER.error(e.getMessage(), e);
    }
  }

  public void persistTaskAssignee(TaskDTO task, final String currentUser) {
    TaskValidator taskValidator = null;
    if (currentUser != null) {
      taskValidator = TaskValidator.CAN_CLAIM;
    } else {
      taskValidator = TaskValidator.CAN_UNCLAIM;
    }
    updateTask(task.getId(), currentUser, taskValidator, asMap(TaskTemplate.ASSIGNEE, currentUser));
  }

  public void updateTask(
      final String taskId,
      final String currentUser,
      final TaskValidator taskValidator,
      final Map<String, Object> updateFields) {
    final GetResponse taskRawResponse = getTaskRawResponse(taskId);
    if (taskRawResponse.isExists()) {
      try {
        final TaskEntity taskBefore =
            fromSearchHit(taskRawResponse.getSourceAsString(), objectMapper, TaskEntity.class);
        // update task with optimistic locking
        // format date fields properly
        taskValidator.validate(taskBefore, currentUser);
        final Map<String, Object> jsonMap =
            objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);
        final UpdateRequest updateRequest =
            new UpdateRequest(
                    taskTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, taskId)
                .doc(jsonMap)
                .setIfSeqNo(taskRawResponse.getSeqNo())
                .setIfPrimaryTerm(taskRawResponse.getPrimaryTerm());
        ElasticsearchUtil.executeUpdate(esClient, updateRequest);
      } catch (Exception e) {
        throw new TasklistRuntimeException(e.getMessage(), e);
      }
    }
  }
}
