/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es;

import static io.camunda.tasklist.schema.indices.ProcessInstanceDependant.PROCESS_INSTANCE_ID;
import static io.camunda.tasklist.util.CollectionUtil.asMap;
import static io.camunda.tasklist.util.CollectionUtil.getOrDefaultFromMap;
import static io.camunda.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.WAIT_UNTIL;
import static org.elasticsearch.index.query.QueryBuilders.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.util.ElasticsearchUtil.QueryType;
import io.camunda.tasklist.webapp.graphql.entity.*;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
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
import org.elasticsearch.index.query.IdsQueryBuilder;
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
          TaskState.CREATED, TaskTemplate.CREATION_TIME,
          TaskState.COMPLETED, TaskTemplate.COMPLETION_TIME,
          TaskState.CANCELED, TaskTemplate.COMPLETION_TIME);
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
  private SearchHit getTaskRawResponse(final String id) throws IOException {

    final QueryBuilder query = idsQuery().addIds(id);

    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(taskTemplate)
            .source(new SearchSourceBuilder().query(constantScoreQuery(query)));

    final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
    if (response.getHits().getTotalHits().value == 1) {
      return response.getHits().getHits()[0];
    } else if (response.getHits().getTotalHits().value > 1) {
      throw new NotFoundException(String.format("Unique task with id %s was not found", id));
    } else {
      throw new NotFoundException(String.format("Task with id %s was not found", id));
    }
  }

  public List<String> getTaskIdsByProcessInstanceId(String processInstanceId) throws IOException {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(taskTemplate)
            .source(
                SearchSourceBuilder.searchSource()
                    .query(termQuery(PROCESS_INSTANCE_ID, processInstanceId))
                    .fetchField(TaskTemplate.ID));
    return ElasticsearchUtil.scrollIdsToList(searchRequest, esClient);
  }

  public List<TaskDTO> getTaskByProcessInstanceId(String processInstanceId) {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(taskTemplate)
            .source(
                SearchSourceBuilder.searchSource()
                    .query(termQuery(PROCESS_INSTANCE_ID, processInstanceId)));
    final SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
    return mapTasksFromEntity(response);
  }

  private List<TaskDTO> mapTasksFromEntity(SearchResponse response) {

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

    return tasks;
  }

  public List<TaskDTO> getTasks(TaskQueryDTO query, List<String> fieldNames) {
    final List<TaskDTO> response = queryTasks(query, fieldNames);

    // query one additional instance
    if (query.getSearchAfterOrEqual() != null || query.getSearchBeforeOrEqual() != null) {
      adjustResponse(response, query, fieldNames);
    }

    if (response.size() > 0
        && (query.getSearchAfter() != null || query.getSearchAfterOrEqual() != null)) {
      final TaskDTO firstTask = response.get(0);
      firstTask.setIsFirst(checkTaskIsFirst(query, firstTask.getId()));
    }

    return response;
  }

  /**
   * In case of searchAfterOrEqual and searchBeforeOrEqual add additional task either at the
   * beginning of the list, or at the end, to conform with "orEqual" part.
   *
   * @param response
   * @param request
   */
  private void adjustResponse(
      final List<TaskDTO> response, final TaskQueryDTO request, List<String> fieldNames) {
    String taskId = null;
    if (request.getSearchAfterOrEqual() != null) {
      taskId = request.getSearchAfterOrEqual()[1];
    } else if (request.getSearchBeforeOrEqual() != null) {
      taskId = request.getSearchBeforeOrEqual()[1];
    }

    final TaskQueryDTO newRequest =
        request
            .createCopy()
            .setSearchAfter(null)
            .setSearchAfterOrEqual(null)
            .setSearchBefore(null)
            .setSearchBeforeOrEqual(null);

    final List<TaskDTO> tasks = queryTasks(newRequest, fieldNames, taskId);
    if (tasks.size() > 0) {
      final TaskDTO entity = tasks.get(0);
      entity.setIsFirst(false); // this was not the original query
      if (request.getSearchAfterOrEqual() != null) {
        // insert at the beginning of the list and remove the last element
        if (response.size() == request.getPageSize()) {
          response.remove(response.size() - 1);
        }
        response.add(0, entity);
      } else if (request.getSearchBeforeOrEqual() != null) {
        // insert at the end of the list and remove the first element
        if (response.size() == request.getPageSize()) {
          response.remove(0);
        }
        response.add(entity);
      }
    }
  }

  private List<TaskDTO> queryTasks(final TaskQueryDTO query, List<String> fieldNames) {
    return queryTasks(query, fieldNames, null);
  }

  private List<TaskDTO> queryTasks(
      final TaskQueryDTO query, List<String> fieldNames, String taskId) {
    final QueryBuilder esQuery = buildQuery(query, taskId);
    // TODO #104 define list of fields

    // TODO we can play around with query type here (2nd parameter), e.g. when we select for only
    // active tasks
    final SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(esQuery);
    applySorting(sourceBuilder, query);

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(
                taskTemplate, getQueryTypeByTaskState(query.getState()))
            .source(sourceBuilder);
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final List<TaskDTO> tasks = mapTasksFromEntity(response);

      if (tasks.size() > 0) {
        if (query.getSearchBefore() != null || query.getSearchBeforeOrEqual() != null) {
          if (tasks.size() <= query.getPageSize()) {
            // last task will be the first in the whole list
            tasks.get(tasks.size() - 1).setIsFirst(true);
          } else {
            // remove last task
            tasks.remove(tasks.size() - 1);
          }
          Collections.reverse(tasks);
        } else if (query.getSearchAfter() == null && query.getSearchAfterOrEqual() == null) {
          tasks.get(0).setIsFirst(true);
        }
      }
      return tasks;
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining tasks: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private static QueryType getQueryTypeByTaskState(TaskState taskState) {
    return TaskState.CREATED == taskState ? QueryType.ONLY_RUNTIME : QueryType.ALL;
  }

  private boolean checkTaskIsFirst(final TaskQueryDTO query, final String id) {
    final TaskQueryDTO newRequest =
        query
            .createCopy()
            .setSearchAfter(null)
            .setSearchAfterOrEqual(null)
            .setSearchBefore(null)
            .setSearchBeforeOrEqual(null)
            .setPageSize(1);
    final List<TaskDTO> tasks = queryTasks(newRequest, null, null);
    if (tasks.size() > 0) {
      return tasks.get(0).getId().equals(id);
    } else {
      return false;
    }
  }

  private QueryBuilder buildQuery(TaskQueryDTO query, String taskId) {
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
    IdsQueryBuilder idsQuery = null;
    if (taskId != null) {
      idsQuery = idsQuery().addIds(taskId);
    }

    QueryBuilder taskDefinitionQ = null;
    if (query.getTaskDefinitionId() != null) {
      taskDefinitionQ = termQuery(TaskTemplate.FLOW_NODE_BPMN_ID, query.getTaskDefinitionId());
    }

    QueryBuilder candidateGroupQ = null;
    if (query.getCandidateGroup() != null) {
      candidateGroupQ = termQuery(TaskTemplate.CANDIDATE_GROUPS, query.getCandidateGroup());
    }

    QueryBuilder candidateUserQ = null;
    if (query.getCandidateUser() != null) {
      candidateUserQ = termQuery(TaskTemplate.CANDIDATE_USERS, query.getCandidateUser());
    }

    QueryBuilder processInstanceIdQ = null;
    if (query.getProcessInstanceId() != null) {
      processInstanceIdQ = termQuery(PROCESS_INSTANCE_ID, query.getProcessInstanceId());
    }

    QueryBuilder processDefinitionIdQ = null;
    if (query.getProcessDefinitionId() != null) {
      processDefinitionIdQ =
          termQuery(TaskTemplate.PROCESS_DEFINITION_ID, query.getProcessDefinitionId());
    }

    QueryBuilder followUpQ = null;
    if (query.getFollowUpDate() != null) {
      followUpQ =
          rangeQuery(TaskTemplate.FOLLOW_UP_DATE)
              .from(query.getFollowUpDate().getFrom())
              .to(query.getFollowUpDate().getTo());
    }

    QueryBuilder dueDateQ = null;
    if (query.getDueDate() != null) {
      dueDateQ =
          rangeQuery(TaskTemplate.DUE_DATE)
              .from(query.getDueDate().getFrom())
              .to(query.getDueDate().getTo());
    }

    QueryBuilder jointQ =
        joinWithAnd(
            stateQ,
            assignedQ,
            assigneeQ,
            idsQuery,
            taskDefinitionQ,
            candidateGroupQ,
            candidateUserQ,
            processInstanceIdQ,
            processDefinitionIdQ,
            followUpQ,
            dueDateQ);
    if (jointQ == null) {
      jointQ = matchAllQuery();
    }
    return constantScoreQuery(jointQ);
  }

  /**
   * In case of searchAfterOrEqual and searchBeforeOrEqual, this method will ignore "orEqual" part.
   *
   * @param searchSourceBuilder
   * @param query
   */
  private void applySorting(SearchSourceBuilder searchSourceBuilder, TaskQueryDTO query) {

    final boolean isSortOnRequest;
    if (query.getSort() != null) {
      isSortOnRequest = true;
    } else {
      isSortOnRequest = false;
    }

    final boolean directSorting =
        query.getSearchAfter() != null
            || query.getSearchAfterOrEqual() != null
            || (query.getSearchBefore() == null && query.getSearchBeforeOrEqual() == null);

    final SortBuilder sort2;
    Object[] querySearchAfter = null; // may be null
    if (directSorting) { // this sorting is also the default one for 1st page
      sort2 = SortBuilders.fieldSort(TaskTemplate.KEY).order(SortOrder.ASC);
      if (query.getSearchAfter() != null) {
        querySearchAfter = query.getSearchAfter();
      } else if (query.getSearchAfterOrEqual() != null) {
        querySearchAfter = query.getSearchAfterOrEqual();
      }
    } else { // searchBefore != null
      // reverse sorting
      sort2 = SortBuilders.fieldSort(TaskTemplate.KEY).order(SortOrder.DESC);
      if (query.getSearchBefore() != null) {
        querySearchAfter = query.getSearchBefore();
      } else if (query.getSearchBeforeOrEqual() != null) {
        querySearchAfter = query.getSearchBeforeOrEqual();
      }
    }

    if (isSortOnRequest) {
      for (int i = 0; i < query.getSort().length; i++) {
        final TaskOrderByDTO orderByDTO = query.getSort()[i];
        final String field = orderByDTO.getField().toString();
        final SortOrder sortOrder;
        final SortBuilder sortBuilder;
        if (directSorting) {
          sortOrder = orderByDTO.getOrder().equals(Sort.DESC) ? SortOrder.DESC : SortOrder.ASC;

        } else {
          sortOrder = orderByDTO.getOrder().equals(Sort.DESC) ? SortOrder.ASC : SortOrder.DESC;
        }
        sortBuilder = SortBuilders.fieldSort(field).order(sortOrder).missing("_last");
        searchSourceBuilder.sort(sortBuilder);
      }
    } else {
      final String sort1Field;
      final SortBuilder sort1;

      sort1Field = getOrDefaultFromMap(SORT_FIELD_PER_STATE, query.getState(), DEFAULT_SORT_FIELD);
      if (directSorting) {
        sort1 = SortBuilders.fieldSort(sort1Field).order(SortOrder.DESC).missing("_last");
      } else {
        sort1 = SortBuilders.fieldSort(sort1Field).order(SortOrder.ASC).missing("_first");
      }
      searchSourceBuilder.sort(sort1);
    }

    searchSourceBuilder.sort(sort2);
    // for searchBefore[orEqual] we will increase size by 1 to fill ou isFirst flag
    if (query.getSearchBefore() != null || query.getSearchBeforeOrEqual() != null) {
      searchSourceBuilder.size(query.getPageSize() + 1);
    } else {
      searchSourceBuilder.size(query.getPageSize());
    }
    if (querySearchAfter != null) {
      searchSourceBuilder.searchAfter(querySearchAfter);
    }
  }

  /**
   * Persist that task is completed even before the corresponding events are imported from Zeebe.
   */
  public TaskEntity persistTaskCompletion(final TaskEntity taskBefore) {
    final SearchHit taskBeforeSearchHit;
    try {
      taskBeforeSearchHit = this.getTaskRawResponse(taskBefore.getId());
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }

    final TaskEntity completedTask =
        taskBefore.makeCopy().setState(TaskState.COMPLETED).setCompletionTime(OffsetDateTime.now());

    try {
      // update task with optimistic locking
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(TaskTemplate.STATE, completedTask.getState());
      updateFields.put(TaskTemplate.COMPLETION_TIME, completedTask.getCompletionTime());

      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);
      final UpdateRequest updateRequest =
          new UpdateRequest()
              .index(taskTemplate.getFullQualifiedName())
              .id(taskBeforeSearchHit.getId())
              .doc(jsonMap)
              .setRefreshPolicy(WAIT_UNTIL)
              .setIfSeqNo(taskBeforeSearchHit.getSeqNo())
              .setIfPrimaryTerm(taskBeforeSearchHit.getPrimaryTerm());
      ElasticsearchUtil.executeUpdate(esClient, updateRequest);
    } catch (Exception e) {
      // we're OK with not updating the task here, it will be marked as completed within import
      LOGGER.error(e.getMessage(), e);
    }
    return completedTask;
  }

  public TaskEntity persistTaskClaim(TaskEntity taskBefore, String assignee) {

    updateTask(taskBefore.getId(), asMap(TaskTemplate.ASSIGNEE, assignee));

    return taskBefore.makeCopy().setAssignee(assignee);
  }

  public TaskEntity persistTaskUnclaim(TaskEntity task) {
    updateTask(task.getId(), asMap(TaskTemplate.ASSIGNEE, null));
    return task.makeCopy().setAssignee(null);
  }

  private void updateTask(final String taskId, final Map<String, Object> updateFields) {
    try {
      final SearchHit searchHit = getTaskRawResponse(taskId);
      // update task with optimistic locking
      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);
      final UpdateRequest updateRequest =
          new UpdateRequest()
              .index(taskTemplate.getFullQualifiedName())
              .id(taskId)
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
