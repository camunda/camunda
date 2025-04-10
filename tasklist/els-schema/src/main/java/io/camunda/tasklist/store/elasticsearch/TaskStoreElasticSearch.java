/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.util.CollectionUtil.asMap;
import static io.camunda.tasklist.util.CollectionUtil.getOrDefaultFromMap;
import static io.camunda.tasklist.util.ElasticsearchUtil.SCROLL_KEEP_ALIVE_MS;
import static io.camunda.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.tasklist.util.ElasticsearchUtil.mapSearchHits;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.WAIT_UNTIL;
import static org.elasticsearch.index.query.QueryBuilders.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.queries.Sort;
import io.camunda.tasklist.queries.TaskByVariables;
import io.camunda.tasklist.queries.TaskOrderBy;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.queries.TaskSortFields;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.store.util.TaskVariableSearchUtil;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.util.ElasticsearchUtil.QueryType;
import io.camunda.tasklist.views.TaskSearchView;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder.ScriptSortType;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class TaskStoreElasticSearch implements TaskStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskStoreElasticSearch.class);
  private static final Map<TaskState, String> SORT_FIELD_PER_STATE =
      Map.of(
          TaskState.CREATED, TaskTemplate.CREATION_TIME,
          TaskState.COMPLETED, TaskTemplate.COMPLETION_TIME,
          TaskState.CANCELED, TaskTemplate.COMPLETION_TIME);

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired private TaskVariableSearchUtil taskVariableSearchUtil;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private VariableStore variableStoreElasticSearch;

  @Autowired
  @Qualifier("tasklistSnapshotTaskVariableTemplate")
  private SnapshotTaskVariableTemplate taskVariableTemplate;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  private SearchHit getRawTaskByUserTaskKey(final String userTaskKey) {
    try {
      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(taskTemplate)
              .source(
                  SearchSourceBuilder.searchSource()
                      .query(termQuery(TaskTemplate.KEY, userTaskKey)));

      final var response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getHits().length == 1) {
        return response.getHits().getHits()[0];
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(
            String.format(
                "Unique %s with id %s was not found", taskTemplate.getIndexName(), userTaskKey));
      } else {
        throw new NotFoundException(
            String.format("%s with id %s was not found", taskTemplate.getIndexName(), userTaskKey));
      }
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  private String getRoutingToUpsertTask(final TaskEntity taskEntity) {
    final var taskId = taskEntity.getId();
    final var taskKey = String.valueOf(taskEntity.getKey());
    if (Objects.equals(taskId, taskKey)) {
      return taskId;
    } else {
      return taskEntity.getProcessInstanceId();
    }
  }

  @Override
  public TaskEntity getTask(final String id) {
    final var rawTask = getRawTaskByUserTaskKey(id);
    return fromSearchHit(rawTask.getSourceAsString(), objectMapper, TaskEntity.class);
  }

  @Override
  public List<String> getTaskIdsByProcessInstanceId(final String processInstanceId) {
    final var processInstanceQuery = termQuery(TaskTemplate.PROCESS_INSTANCE_ID, processInstanceId);
    final var flownodeInstanceQuery = existsQuery(TaskTemplate.FLOW_NODE_INSTANCE_ID);
    final var finalQuery =
        ElasticsearchUtil.joinWithAnd(processInstanceQuery, flownodeInstanceQuery);
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(taskTemplate)
            .source(
                SearchSourceBuilder.searchSource().query(finalQuery).fetchField(TaskTemplate.KEY));
    try {
      return ElasticsearchUtil.scrollUserTaskKeysToList(searchRequest, esClient);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public Map<String, String> getTaskIdsWithIndexByProcessDefinitionId(
      final String processDefinitionId) {
    final var processDefinitionQuery =
        termQuery(TaskTemplate.PROCESS_DEFINITION_ID, processDefinitionId);
    final var flownodeInstanceQuery = existsQuery(TaskTemplate.FLOW_NODE_INSTANCE_ID);
    final var finalQuery =
        ElasticsearchUtil.joinWithAnd(processDefinitionQuery, flownodeInstanceQuery);
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(taskTemplate)
            .source(
                SearchSourceBuilder.searchSource().query(finalQuery).fetchField(TaskTemplate.KEY));
    try {
      return ElasticsearchUtil.scrollIdsWithIndexToMap(searchRequest, esClient);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public List<TaskSearchView> getTasks(final TaskQuery query) {
    final List<TaskSearchView> response = queryTasks(query);

    // query one additional instance
    if (query.getSearchAfterOrEqual() != null || query.getSearchBeforeOrEqual() != null) {
      adjustResponse(response, query);
    }

    if (response.size() > 0
        && (query.getSearchAfter() != null || query.getSearchAfterOrEqual() != null)) {
      final TaskSearchView firstTask = response.get(0);
      firstTask.setFirst(checkTaskIsFirst(query, firstTask.getId()));
    }

    return response;
  }

  /**
   * Persist that task is completed even before the corresponding events are imported from Zeebe.
   */
  @Override
  public TaskEntity persistTaskCompletion(final TaskEntity taskBefore) {
    final SearchHit taskBeforeSearchHit =
        getRawTaskByUserTaskKey(String.valueOf(taskBefore.getKey()));

    final TaskEntity completedTask =
        makeCopyOf(taskBefore)
            .setState(TaskState.COMPLETED)
            .setCompletionTime(OffsetDateTime.now());

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
              .setIfPrimaryTerm(taskBeforeSearchHit.getPrimaryTerm())
              .routing(getRoutingToUpsertTask(completedTask));
      ElasticsearchUtil.executeUpdate(esClient, updateRequest);
    } catch (final Exception e) {
      // we're OK with not updating the task here, it will be marked as completed within import
      LOGGER.error(e.getMessage(), e);
    }
    return completedTask;
  }

  @Override
  public TaskEntity rollbackPersistTaskCompletion(final TaskEntity taskBefore) {
    final SearchHit taskBeforeSearchHit =
        getRawTaskByUserTaskKey(String.valueOf(taskBefore.getKey()));
    final TaskEntity completedTask = makeCopyOf(taskBefore).setCompletionTime(null);

    try {
      // update task with optimistic locking
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(TaskTemplate.STATE, completedTask.getState());
      updateFields.put(TaskTemplate.COMPLETION_TIME, null);

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
              .setIfPrimaryTerm(taskBeforeSearchHit.getPrimaryTerm())
              .routing(getRoutingToUpsertTask(completedTask));
      ElasticsearchUtil.executeUpdate(esClient, updateRequest);
    } catch (final Exception e) {
      LOGGER.error("Error when trying to rollback Task to CREATED state: {}", e.getMessage());
    }
    return completedTask;
  }

  @Override
  public TaskEntity persistTaskClaim(final TaskEntity taskBefore, final String assignee) {

    updateTask(String.valueOf(taskBefore.getKey()), asMap(TaskTemplate.ASSIGNEE, assignee));

    return makeCopyOf(taskBefore).setAssignee(assignee);
  }

  @Override
  public TaskEntity persistTaskUnclaim(final TaskEntity task) {
    updateTask(String.valueOf(task.getKey()), asMap(TaskTemplate.ASSIGNEE, null));
    return makeCopyOf(task).setAssignee(null);
  }

  @Override
  public List<TaskEntity> getTasksById(final List<String> ids) {
    try {
      final SearchHit[] response = getTasksRawResponse(ids);
      return mapSearchHits(response, objectMapper, TaskEntity.class);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void updateTaskLinkedForm(
      final TaskEntity task, final String formBpmnId, final long formVersion) {
    updateTask(
        String.valueOf(task.getKey()),
        asMap(TaskTemplate.FORM_ID, formBpmnId, TaskTemplate.FORM_VERSION, formVersion));
  }

  private SearchHit[] getTasksRawResponse(final List<String> ids) throws IOException {

    final QueryBuilder query = termsQuery(TaskTemplate.KEY, ids);

    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(taskTemplate)
            .source(new SearchSourceBuilder().query(constantScoreQuery(query)));

    final SearchResponse response = tenantAwareClient.search(request);
    if (response.getHits().getTotalHits().value > 0) {
      return response.getHits().getHits();
    } else {
      throw new NotFoundException(String.format("No tasks were found for ids %s", ids));
    }
  }

  private List<TaskSearchView> mapTasksFromEntity(final SearchResponse response) {
    return ElasticsearchUtil.mapSearchHits(
        response.getHits().getHits(),
        (sh) ->
            TaskSearchView.createFrom(
                ElasticsearchUtil.fromSearchHit(
                    sh.getSourceAsString(), objectMapper, TaskEntity.class),
                sh.getSortValues()));
  }

  /**
   * In case of searchAfterOrEqual and searchBeforeOrEqual add additional task either at the
   * beginning of the list, or at the end, to conform with "orEqual" part.
   *
   * @param response
   * @param request
   */
  private void adjustResponse(final List<TaskSearchView> response, final TaskQuery request) {
    String taskId = null;
    if (request.getSearchAfterOrEqual() != null) {
      taskId = request.getSearchAfterOrEqual()[1];
    } else if (request.getSearchBeforeOrEqual() != null) {
      taskId = request.getSearchBeforeOrEqual()[1];
    }

    final TaskQuery newRequest =
        request
            .createCopy()
            .setSearchAfter(null)
            .setSearchAfterOrEqual(null)
            .setSearchBefore(null)
            .setSearchBeforeOrEqual(null);

    final List<TaskSearchView> tasks = queryTasks(newRequest, taskId);
    if (tasks.size() > 0) {
      final TaskSearchView entity = tasks.get(0);
      entity.setFirst(false); // this was not the original query
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

  private List<TaskSearchView> queryTasks(final TaskQuery query) {
    return queryTasks(query, null);
  }

  private List<TaskSearchView> queryTasks(final TaskQuery query, final String taskId) {
    List<String> tasksIds = null;
    if (query.getTaskVariables() != null && query.getTaskVariables().length > 0) {
      tasksIds = getTasksContainsVarNameAndValue(query.getTaskVariables());
      if (tasksIds.isEmpty()) {
        return new ArrayList<>();
      }
    }

    if (taskId != null && !taskId.isEmpty()) {
      if (query.getTaskVariables() != null && query.getTaskVariables().length > 0) {
        tasksIds = tasksIds.stream().filter(id -> !id.equals(taskId)).collect(toList());
        if (tasksIds.isEmpty()) {
          return new ArrayList<>();
        }
      } else {
        tasksIds = new ArrayList<>();
        tasksIds.add(taskId);
      }
    }

    final QueryBuilder esQuery = buildQuery(query, tasksIds);
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
      final SearchResponse response =
          query.getTenantIds() == null
              ? tenantAwareClient.search(searchRequest)
              : tenantAwareClient.searchByTenantIds(searchRequest, Set.of(query.getTenantIds()));
      final List<TaskSearchView> tasks = mapTasksFromEntity(response);

      if (!tasks.isEmpty()) {
        if (query.getSearchBefore() != null || query.getSearchBeforeOrEqual() != null) {
          if (tasks.size() <= query.getPageSize()) {
            // last task will be the first in the whole list
            tasks.get(tasks.size() - 1).setFirst(true);
          } else {
            // remove last task
            tasks.remove(tasks.size() - 1);
          }
          Collections.reverse(tasks);
        } else if (query.getSearchAfter() == null && query.getSearchAfterOrEqual() == null) {
          tasks.get(0).setFirst(true);
        }
      }
      return tasks;
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining tasks: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private QueryType getQueryTypeByTaskState(final TaskState taskState) {
    return TaskState.CREATED == taskState ? QueryType.ONLY_RUNTIME : QueryType.ALL;
  }

  private boolean checkTaskIsFirst(final TaskQuery query, final String id) {
    final TaskQuery newRequest =
        query
            .createCopy()
            .setSearchAfter(null)
            .setSearchAfterOrEqual(null)
            .setSearchBefore(null)
            .setSearchBeforeOrEqual(null)
            .setPageSize(1);
    final List<TaskSearchView> tasks = queryTasks(newRequest, null);
    if (tasks.size() > 0) {
      return tasks.get(0).getId().equals(id);
    } else {
      return false;
    }
  }

  private QueryBuilder buildQuery(final TaskQuery query, final List<String> taskIds) {
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

    QueryBuilder assigneesQ = null;
    if (query.getAssignees() != null) {
      assigneesQ = termsQuery(TaskTemplate.ASSIGNEE, query.getAssignees());
    }

    TermsQueryBuilder taskIdsQuery = null;
    ExistsQueryBuilder flowNodeInstanceExistsQuery = null;
    if (taskIds != null) {
      taskIdsQuery = termsQuery(TaskTemplate.KEY, taskIds);
    } else {
      flowNodeInstanceExistsQuery = existsQuery(TaskTemplate.FLOW_NODE_INSTANCE_ID);
    }

    QueryBuilder taskDefinitionQ = null;
    if (query.getTaskDefinitionId() != null) {
      taskDefinitionQ = termQuery(TaskTemplate.FLOW_NODE_BPMN_ID, query.getTaskDefinitionId());
    }

    QueryBuilder candidateGroupQ = null;
    if (query.getCandidateGroup() != null) {
      candidateGroupQ = termQuery(TaskTemplate.CANDIDATE_GROUPS, query.getCandidateGroup());
    }

    QueryBuilder candidateGroupsQ = null;
    if (query.getCandidateGroups() != null) {
      candidateGroupsQ = termsQuery(TaskTemplate.CANDIDATE_GROUPS, query.getCandidateGroups());
    }

    QueryBuilder candidateUserQ = null;
    if (query.getCandidateUser() != null) {
      candidateUserQ = termQuery(TaskTemplate.CANDIDATE_USERS, query.getCandidateUser());
    }

    QueryBuilder candidateUsersQ = null;
    if (query.getCandidateUsers() != null) {
      candidateUsersQ = termsQuery(TaskTemplate.CANDIDATE_USERS, query.getCandidateUsers());
    }

    QueryBuilder candidateGroupsAndUserByCurrentUserQ = null;
    if (query.getTaskByCandidateUserOrGroups() != null) {
      candidateGroupsAndUserByCurrentUserQ =
          returnUserGroupBoolQuery(
              List.of(query.getTaskByCandidateUserOrGroups().getUserGroups()),
              query.getTaskByCandidateUserOrGroups().getUserName());
    }

    QueryBuilder processInstanceIdQ = null;
    if (query.getProcessInstanceId() != null) {
      processInstanceIdQ =
          termQuery(TaskTemplate.PROCESS_INSTANCE_ID, query.getProcessInstanceId());
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
              .gte(query.getFollowUpDate().getFrom())
              .lte(query.getFollowUpDate().getTo());
    }

    QueryBuilder dueDateQ = null;
    if (query.getDueDate() != null) {
      dueDateQ =
          rangeQuery(TaskTemplate.DUE_DATE)
              .from(query.getDueDate().getFrom())
              .lte(query.getDueDate().getTo());
    }
    QueryBuilder implementationQ = null;
    if (query.getImplementation() != null) {
      implementationQ = termQuery(TaskTemplate.IMPLEMENTATION, query.getImplementation());
    }

    final QueryBuilder priorityQ = buildPriorityQuery(query);

    QueryBuilder jointQ =
        joinWithAnd(
            stateQ,
            assignedQ,
            assigneeQ,
            assigneesQ,
            taskIdsQuery,
            flowNodeInstanceExistsQuery,
            taskDefinitionQ,
            candidateGroupQ,
            candidateGroupsQ,
            candidateUserQ,
            candidateUsersQ,
            candidateGroupsAndUserByCurrentUserQ,
            processInstanceIdQ,
            processDefinitionIdQ,
            followUpQ,
            dueDateQ,
            implementationQ,
            priorityQ);
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
  private void applySorting(final SearchSourceBuilder searchSourceBuilder, final TaskQuery query) {

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
        final TaskOrderBy orderBy = query.getSort()[i];
        final String field = orderBy.getField().toString();
        final SortOrder sortOrder =
            directSorting
                ? orderBy.getOrder().equals(Sort.DESC) ? SortOrder.DESC : SortOrder.ASC
                : orderBy.getOrder().equals(Sort.DESC) ? SortOrder.ASC : SortOrder.DESC;

        if (!orderBy.getField().equals(TaskSortFields.priority)) {
          searchSourceBuilder.sort(applyDateSortScript(orderBy.getOrder(), field, sortOrder));
        } else {
          searchSourceBuilder.sort(
              mapNullInSort(
                  TaskTemplate.PRIORITY, DEFAULT_PRIORITY, sortOrder, ScriptSortType.NUMBER));
        }
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

  private SortBuilder<?> applyDateSortScript(
      final Sort sorting, final String field, final SortOrder sortOrder) {
    final String nullDate;
    if (sorting.equals(Sort.ASC)) {
      nullDate = "2099-12-31";
    } else {
      nullDate = "1900-01-01";
    }
    final Script script =
        new Script(
            "def sf = new SimpleDateFormat(\"yyyy-MM-dd\"); "
                + "def nullDate=sf.parse('"
                + nullDate
                + "');"
                + "if(doc['"
                + field
                + "'].size() == 0){"
                + "nullDate.getTime().toString()"
                + "}else{"
                + "doc['"
                + field
                + "'].value.getMillis().toString()"
                + "}");
    return SortBuilders.scriptSort(script, ScriptSortType.STRING).order(sortOrder);
  }

  private void updateTask(final String taskId, final Map<String, Object> updateFields) {
    try {
      final SearchHit searchHit = getRawTaskByUserTaskKey(taskId);
      final var taskEntity =
          fromSearchHit(searchHit.getSourceAsString(), objectMapper, TaskEntity.class);
      // update task with optimistic locking
      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);
      final UpdateRequest updateRequest =
          new UpdateRequest()
              .index(taskTemplate.getFullQualifiedName())
              .id(searchHit.getId())
              .doc(jsonMap)
              .setRefreshPolicy(WAIT_UNTIL)
              .setIfSeqNo(searchHit.getSeqNo())
              .setIfPrimaryTerm(searchHit.getPrimaryTerm())
              .routing(getRoutingToUpsertTask(taskEntity));
      ElasticsearchUtil.executeUpdate(esClient, updateRequest);
    } catch (final Exception e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  private List<String> getTasksContainsVarNameAndValue(
      final TaskByVariables[] taskVariablesFilter) {
    final List<String> varNames =
        Arrays.stream(taskVariablesFilter).map(TaskByVariables::getName).collect(toList());
    final List<String> varValues =
        Arrays.stream(taskVariablesFilter).map(TaskByVariables::getValue).collect(toList());

    final List<String> processIdsCreatedFiltered =
        variableStoreElasticSearch.getProcessInstanceIdsWithMatchingVars(varNames, varValues);

    final List<String> tasksIdsCreatedFiltered =
        retrieveTaskIdByProcessInstanceId(processIdsCreatedFiltered, taskVariablesFilter);

    final List<String> taskIdsCompletedFiltered =
        getTasksIdsCompletedWithMatchingVars(varNames, varValues);

    return Stream.concat(tasksIdsCreatedFiltered.stream(), taskIdsCompletedFiltered.stream())
        .distinct()
        .collect(Collectors.toList());
  }

  private List<String> getTasksIdsCompletedWithMatchingVars(
      final List<String> varNames, final List<String> varValues) {
    final List<Set<String>> tasksIdsMatchingAllVars = new ArrayList<>();

    for (int i = 0; i < varNames.size(); i++) {
      final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
      boolQuery.must(QueryBuilders.termQuery(SnapshotTaskVariableTemplate.NAME, varNames.get(i)));
      boolQuery.must(QueryBuilders.termQuery(SnapshotTaskVariableTemplate.VALUE, varValues.get(i)));

      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(boolQuery)
              .fetchSource(SnapshotTaskVariableTemplate.TASK_ID, null);

      final SearchRequest searchRequest =
          new SearchRequest(taskVariableTemplate.getAlias()).source(searchSourceBuilder);
      searchRequest.scroll(new TimeValue(SCROLL_KEEP_ALIVE_MS));

      final Set<String> taskIds = new HashSet<>();

      try {
        SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
        String scrollId = searchResponse.getScrollId();

        List<String> scrollTaskIds =
            Arrays.stream(searchResponse.getHits().getHits())
                .map(hit -> (String) hit.getSourceAsMap().get(SnapshotTaskVariableTemplate.TASK_ID))
                .collect(Collectors.toList());

        taskIds.addAll(scrollTaskIds);

        while (scrollTaskIds.size() > 0) {
          final SearchScrollRequest scrollRequest =
              new SearchScrollRequest(scrollId).scroll(new TimeValue(SCROLL_KEEP_ALIVE_MS));

          searchResponse = esClient.scroll(scrollRequest, RequestOptions.DEFAULT);
          scrollId = searchResponse.getScrollId();
          scrollTaskIds =
              Arrays.stream(searchResponse.getHits().getHits())
                  .map(
                      hit ->
                          (String) hit.getSourceAsMap().get(SnapshotTaskVariableTemplate.TASK_ID))
                  .toList();
          taskIds.addAll(scrollTaskIds);
        }

        // Finalize the scroll to free the resources
        final ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
        clearScrollRequest.addScrollId(scrollId);
        esClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);

        tasksIdsMatchingAllVars.add(taskIds);

      } catch (final IOException e) {
        final String message =
            String.format(
                "Exception occurred while obtaining taskIds for variable %s: %s",
                varNames.get(i), e.getMessage());
        throw new TasklistRuntimeException(message, e);
      }
    }

    // Find intersection of all sets
    return new ArrayList<>(
        tasksIdsMatchingAllVars.stream()
            .reduce(
                (set1, set2) -> {
                  set1.retainAll(set2);
                  return set1;
                })
            .orElse(Collections.emptySet()));
  }

  private BoolQueryBuilder returnUserGroupBoolQuery(
      final List<String> userGroups, final String userName) {
    final SearchRequest searchRequest = new SearchRequest(taskTemplate.getFullQualifiedName());
    final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

    // Additional clause for TaskTemplate.ASSIGNEE
    boolQuery.should(QueryBuilders.termQuery(TaskTemplate.ASSIGNEE, userName));

    userGroups.forEach(
        group -> boolQuery.should(QueryBuilders.termsQuery(TaskTemplate.CANDIDATE_GROUPS, group)));

    boolQuery.should(QueryBuilders.termQuery(TaskTemplate.CANDIDATE_USERS, userName));

    // Consider the tasks that have no candidate users and groups
    boolQuery.should(
        QueryBuilders.boolQuery()
            .mustNot(QueryBuilders.existsQuery(TaskTemplate.CANDIDATE_USERS))
            .mustNot(QueryBuilders.existsQuery(TaskTemplate.CANDIDATE_GROUPS)));

    return boolQuery;
  }

  private List<String> retrieveTaskIdByProcessInstanceId(
      final List<String> processIds, final TaskByVariables[] taskVariablesFilter) {
    final List<String> taskIdsCreated = new ArrayList<>();
    final Map<String, String> variablesMap =
        IntStream.range(0, taskVariablesFilter.length)
            .boxed()
            .collect(
                Collectors.toMap(
                    i -> taskVariablesFilter[i].getName(), i -> taskVariablesFilter[i].getValue()));

    for (final String processId : processIds) {
      final List<String> taskIds = getTaskIdsByProcessInstanceId(processId);
      for (final String taskId : taskIds) {
        final TaskEntity taskEntity = getTask(taskId);
        if (taskEntity.getState() == TaskState.CREATED) {
          final List<VariableStore.GetVariablesRequest> request =
              Collections.singletonList(
                  VariableStore.GetVariablesRequest.createFrom(taskEntity)
                      .setVarNames(variablesMap.keySet().stream().toList()));
          if (taskVariableSearchUtil.checkIfVariablesExistInTask(request, variablesMap)) {
            taskIdsCreated.add(taskId);
          }
        }
      }
    }
    return taskIdsCreated;
  }

  private QueryBuilder buildPriorityQuery(final TaskQuery query) {
    if (query.getPriority() != null) {
      final var priority = query.getPriority();
      if (priority.getEq() != null) {
        return QueryBuilders.termQuery(TaskTemplate.PRIORITY, priority.getEq());
      } else {
        RangeQueryBuilder rangeBuilder = QueryBuilders.rangeQuery(TaskTemplate.PRIORITY);
        if (priority.getGt() != null) {
          rangeBuilder = rangeBuilder.gt(priority.getGt());
        }
        if (priority.getGte() != null) {
          rangeBuilder = rangeBuilder.gte(priority.getGte());
        }
        if (priority.getLt() != null) {
          rangeBuilder = rangeBuilder.lt(priority.getLt());
        }
        if (priority.getLte() != null) {
          rangeBuilder = rangeBuilder.lte(priority.getLte());
        }
        return rangeBuilder;
      }
    }
    return null;
  }

  private SortBuilder<?> mapNullInSort(
      final String field,
      final String defaultValue,
      final SortOrder order,
      final ScriptSortBuilder.ScriptSortType sortType) {
    final String nullHandlingScript =
        String.format(
            "if (doc['%s'].size() == 0) { %s } else { doc['%s'].value }",
            field, defaultValue, field);

    final Script script = new Script(nullHandlingScript);
    return SortBuilders.scriptSort(script, sortType).order(order);
  }
}
