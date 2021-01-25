/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.es;

import static io.zeebe.tasklist.util.CollectionUtil.asMap;
import static io.zeebe.tasklist.util.CollectionUtil.getOrDefaultFromMap;
import static io.zeebe.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.zeebe.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.WAIT_UNTIL;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskReaderWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskReaderWriter.class);

  private static final Map<TaskState, String> SORT_FIELD_PER_STATE =
      Map.of(
          TaskState.CREATED,
          TaskTemplate.CREATION_TIME,
          TaskState.COMPLETED,
          TaskTemplate.COMPLETION_TIME,
          TaskState.CANCELED,
          TaskTemplate.COMPLETION_TIME);
  private static final String DEFAULT_SORT_FIELD = TaskTemplate.CREATION_TIME;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private ObjectMapper objectMapper;

  public TaskEntity getTask(String id) {
    return getTask(id, null);
  }

  private TaskEntity getTask(final String id, List<String> fieldNames) {
    try {
      // TODO #104 define list of fields and specify sourceFields to fetch
      final SearchHit response = getTaskRawResponse(id);
      return fromSearchHit(response.getSourceAsString(), objectMapper, TaskEntity.class);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  /**
   * @param id
   * @param fieldNames list of field names to return. When null, return all fields.
   * @return
   */
  public TaskDTO getTaskDTO(String id, List<String> fieldNames) {
    final TaskEntity taskEntity = getTask(id, fieldNames);

    return TaskDTO.createFrom(taskEntity, objectMapper);
  }

  @NotNull
  public SearchHit getTaskRawResponse(final String id) throws IOException {

    final QueryBuilder query = idsQuery().addIds(String.valueOf(id));

    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(taskTemplate)
            .source(new SearchSourceBuilder().query(constantScoreQuery(query)));

    final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
    if (response.getHits().totalHits == 1) {
      return response.getHits().getHits()[0];
    } else if (response.getHits().totalHits > 1) {
      throw new NotFoundException(String.format("Unique task with id %s was not found", id));
    } else {
      throw new NotFoundException(String.format("Task with id %s was not found", id));
    }
  }

  public List<TaskDTO> getTasks(TaskQueryDTO query, List<String> fieldNames) {
    final QueryBuilder esQuery = buildQuery(query);
    // TODO #104 define list of fields

    // TODO we can play around with query type here (2nd parameter), e.g. when we select for only
    // active tasks
    final SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(esQuery);
    applySorting(sourceBuilder, query);
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(taskTemplate)
            .source(
                sourceBuilder
                //  .fetchSource(fieldNames.toArray(String[]::new), null)
                );

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

      final List<TaskDTO> tasks =
          ElasticsearchUtil.mapSearchHits(
              response.getHits().getHits(),
              (sh) -> {
                final TaskDTO entity =
                    TaskDTO.createFrom(
                        ElasticsearchUtil.fromSearchHit(
                            sh.getSourceAsString(), objectMapper, TaskEntity.class),
                        sh.getSortValues(),
                        objectMapper);
                return entity;
              });
      if (query.getSearchBefore() != null) {
        Collections.reverse(tasks);
      }
      return tasks;
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining tasks: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private QueryBuilder buildQuery(TaskQueryDTO query) {
    QueryBuilder stateQ = boolQuery().mustNot(termQuery(TaskTemplate.STATE, TaskState.CANCELED));
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

  private void applySorting(SearchSourceBuilder searchSourceBuilder, TaskQueryDTO query) {

    final boolean directSorting = query.getSearchAfter() != null || query.getSearchBefore() == null;

    final String sort1Field =
        getOrDefaultFromMap(SORT_FIELD_PER_STATE, query.getState(), DEFAULT_SORT_FIELD);

    final SortBuilder sort1;
    if (directSorting) {
      sort1 = SortBuilders.fieldSort(sort1Field).order(SortOrder.DESC).missing("_last");
    } else {
      sort1 = SortBuilders.fieldSort(sort1Field).order(SortOrder.ASC).missing("_first");
    }

    final SortBuilder sort2;
    final Object[] querySearchAfter;
    if (directSorting) { // this sorting is also the default one for 1st page
      sort2 = SortBuilders.fieldSort(TaskTemplate.KEY).order(SortOrder.ASC);
      querySearchAfter = query.getSearchAfter(); // may be null
    } else { // searchBefore != null
      // reverse sorting
      sort2 = SortBuilders.fieldSort(TaskTemplate.KEY).order(SortOrder.DESC);
      querySearchAfter = query.getSearchBefore();
    }

    searchSourceBuilder.sort(sort1).sort(sort2).size(query.getPageSize());
    if (querySearchAfter != null) {
      searchSourceBuilder.searchAfter(querySearchAfter);
    }
  }

  /**
   * Persist that task is completed even before the corresponding events are imported from Zeebe.
   *
   * @param taskBeforeSearchHit
   */
  public TaskEntity persistTaskCompletion(SearchHit taskBeforeSearchHit) {
    final TaskEntity taskBefore =
        fromSearchHit(taskBeforeSearchHit.getSourceAsString(), objectMapper, TaskEntity.class);
    taskBefore.setState(TaskState.COMPLETED);
    taskBefore.setCompletionTime(OffsetDateTime.now());
    try {
      // update task with optimistic locking
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(TaskTemplate.STATE, taskBefore.getState());
      updateFields.put(TaskTemplate.COMPLETION_TIME, taskBefore.getCompletionTime());

      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);
      final UpdateRequest updateRequest =
          new UpdateRequest(
                  taskTemplate.getMainIndexName(),
                  ElasticsearchUtil.ES_INDEX_TYPE,
                  taskBeforeSearchHit.getId())
              .doc(jsonMap)
              .setRefreshPolicy(WAIT_UNTIL)
              .setIfSeqNo(taskBeforeSearchHit.getSeqNo())
              .setIfPrimaryTerm(taskBeforeSearchHit.getPrimaryTerm());
      ElasticsearchUtil.executeUpdate(esClient, updateRequest);
    } catch (Exception e) {
      // we're OK with not updating the task here, it will be marked as completed within import
      LOGGER.error(e.getMessage(), e);
    }
    return taskBefore;
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

  private void updateTask(
      final String taskId,
      final String currentUser,
      final TaskValidator taskValidator,
      final Map<String, Object> updateFields) {
    try {
      final SearchHit searchHit = getTaskRawResponse(taskId);
      final TaskEntity taskBefore =
          fromSearchHit(searchHit.getSourceAsString(), objectMapper, TaskEntity.class);
      // update task with optimistic locking
      // format date fields properly
      taskValidator.validate(taskBefore, currentUser);
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);
      final UpdateRequest updateRequest =
          new UpdateRequest(
                  taskTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, taskId)
              .doc(jsonMap)
              .setRefreshPolicy(WAIT_UNTIL)
              .setIfSeqNo(searchHit.getSeqNo())
              .setIfPrimaryTerm(searchHit.getPrimaryTerm());
      ElasticsearchUtil.executeUpdate(esClient, updateRequest);
    } catch (Exception e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }
}
