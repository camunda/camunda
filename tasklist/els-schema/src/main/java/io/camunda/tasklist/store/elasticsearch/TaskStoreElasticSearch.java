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
import static java.util.stream.Collectors.toList;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.ScriptSortType;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.UntypedRangeQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.queries.Sort;
import io.camunda.tasklist.queries.TaskByVariables;
import io.camunda.tasklist.queries.TaskOrderBy;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.queries.TaskSortFields;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.store.util.TaskVariableSearchUtil;
import io.camunda.tasklist.util.ElasticsearchTenantHelper;
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  @Qualifier("tasklistEs8Client")
  private ElasticsearchClient es8Client;

  @Autowired private ElasticsearchTenantHelper tenantHelper;

  @Autowired private TaskVariableSearchUtil taskVariableSearchUtil;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private VariableStore variableStoreElasticSearch;

  @Autowired private SnapshotTaskVariableTemplate taskVariableTemplate;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  private Hit<TaskEntity> getRawTaskByUserTaskKey(final String userTaskKey) {
    final var query = ElasticsearchUtil.termsQuery(TaskTemplate.KEY, userTaskKey);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var request =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(ElasticsearchUtil.whereToSearch(taskTemplate, QueryType.ALL))
            .query(tenantAwareQuery)
            .build();

    try {
      final var response = es8Client.search(request, TaskEntity.class);
      if (response.hits().hits().size() == 1) {
        return response.hits().hits().get(0);
      } else if (response.hits().total().value() > 1) {
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
    return rawTask.source();
  }

  @Override
  public List<String> getTaskIdsByProcessInstanceId(final String processInstanceId) {
    final var processInstanceQuery =
        ElasticsearchUtil.termsQuery(TaskTemplate.PROCESS_INSTANCE_ID, processInstanceId);
    final var flownodeInstanceQuery =
        ElasticsearchUtil.existsQuery(TaskTemplate.FLOW_NODE_INSTANCE_ID);
    final var finalQuery =
        ElasticsearchUtil.joinWithAnd(processInstanceQuery, flownodeInstanceQuery);

    final var searchRequestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(ElasticsearchUtil.whereToSearch(taskTemplate, QueryType.ALL))
            .query(finalQuery)
            .source(s -> s.filter(f -> f.includes(TaskTemplate.KEY)))
            .size(tasklistProperties.getElasticsearch().getBatchSize());

    try {
      return ElasticsearchUtil.scrollAllStream(
              es8Client, searchRequestBuilder, ElasticsearchUtil.MAP_CLASS)
          .flatMap(response -> response.hits().hits().stream())
          .map(hit -> hit.source())
          .filter(Objects::nonNull)
          .map(source -> String.valueOf(((Number) source.get(TaskTemplate.KEY)).longValue()))
          .toList();
    } catch (final Exception e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public Map<String, String> getTaskIdsWithIndexByProcessDefinitionId(
      final String processDefinitionId) {
    final var processDefinitionQuery =
        ElasticsearchUtil.termsQuery(TaskTemplate.PROCESS_DEFINITION_ID, processDefinitionId);
    final var flownodeInstanceQuery =
        ElasticsearchUtil.existsQuery(TaskTemplate.FLOW_NODE_INSTANCE_ID);
    final var finalQuery =
        ElasticsearchUtil.joinWithAnd(processDefinitionQuery, flownodeInstanceQuery);

    final var searchRequestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(ElasticsearchUtil.whereToSearch(taskTemplate, QueryType.ALL))
            .query(finalQuery)
            .source(s -> s.filter(f -> f.includes(TaskTemplate.KEY)));

    try {
      return ElasticsearchUtil.scrollIdsWithIndexToMap(es8Client, searchRequestBuilder);
    } catch (final Exception e) {
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
    final Hit<TaskEntity> taskBeforeHit =
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

      final var updateRequest =
          UpdateRequest.of(
              u ->
                  u.index(taskTemplate.getFullQualifiedName())
                      .id(taskBeforeHit.id())
                      .doc(updateFields)
                      .ifSeqNo(taskBeforeHit.seqNo())
                      .ifPrimaryTerm(taskBeforeHit.primaryTerm())
                      .routing(getRoutingToUpsertTask(completedTask))
                      .refresh(Refresh.WaitFor));

      es8Client.update(updateRequest, Object.class);
    } catch (final Exception e) {
      // we're OK with not updating the task here, it will be marked as completed within import
      LOGGER.error(e.getMessage(), e);
    }
    return completedTask;
  }

  @Override
  public TaskEntity rollbackPersistTaskCompletion(final TaskEntity taskBefore) {
    final Hit<TaskEntity> taskBeforeHit =
        getRawTaskByUserTaskKey(String.valueOf(taskBefore.getKey()));
    final TaskEntity completedTask = makeCopyOf(taskBefore).setCompletionTime(null);

    try {
      // update task with optimistic locking
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(TaskTemplate.STATE, completedTask.getState());
      updateFields.put(TaskTemplate.COMPLETION_TIME, null);

      final var updateRequest =
          UpdateRequest.of(
              u ->
                  u.index(taskTemplate.getFullQualifiedName())
                      .id(taskBeforeHit.id())
                      .doc(updateFields)
                      .ifSeqNo(taskBeforeHit.seqNo())
                      .ifPrimaryTerm(taskBeforeHit.primaryTerm())
                      .routing(getRoutingToUpsertTask(completedTask))
                      .refresh(Refresh.WaitFor));

      es8Client.update(updateRequest, Object.class);
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
    final var query = ElasticsearchUtil.termsQuery(TaskTemplate.KEY, ids);
    final var wrappedQuery = ElasticsearchUtil.constantScoreQuery(query);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(wrappedQuery);

    final var request =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(taskTemplate.getAlias())
            .query(tenantAwareQuery)
            .build();

    try {
      final var response = es8Client.search(request, TaskEntity.class);
      if (response.hits().total().value() > 0) {
        return response.hits().hits().stream()
            .map(hit -> hit.source())
            .filter(java.util.Objects::nonNull)
            .toList();
      } else {
        throw new NotFoundException(String.format("No tasks were found for ids %s", ids));
      }
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

  private List<TaskEntity> getActiveTasksByProcessInstanceIds(
      final List<String> processInstanceIds) {
    try {
      // the number of process instance ids may be large, so we need to chunk them
      return ElasticsearchUtil.scrollInChunks(
          es8Client,
          processInstanceIds,
          tasklistProperties.getElasticsearch().getMaxTermsCount(),
          this::buildSearchCreatedTasksByProcessInstanceIdsRequestEs8,
          TaskEntity.class);
    } catch (final Exception e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  private co.elastic.clients.elasticsearch.core.SearchRequest.Builder
      buildSearchCreatedTasksByProcessInstanceIdsRequestEs8(final List<String> processInstanceIds) {
    final var processInstanceIdsQuery =
        ElasticsearchUtil.termsQuery(TaskTemplate.PROCESS_INSTANCE_ID, processInstanceIds);
    final var stateQuery =
        ElasticsearchUtil.termsQuery(TaskTemplate.STATE, TaskState.CREATED.name());
    final var flowNodeInstanceQuery =
        ElasticsearchUtil.existsQuery(TaskTemplate.FLOW_NODE_INSTANCE_ID);
    final var query =
        ElasticsearchUtil.joinWithAnd(processInstanceIdsQuery, stateQuery, flowNodeInstanceQuery);

    return new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
        .index(ElasticsearchUtil.whereToSearch(taskTemplate, QueryType.ALL))
        .query(query)
        .size(tasklistProperties.getElasticsearch().getBatchSize());
  }

  private List<TaskSearchView> mapTasksFromEntity(final SearchResponse<TaskEntity> response) {
    return response.hits().hits().stream()
        .map(
            hit ->
                TaskSearchView.createFrom(
                    hit.source(), hit.sort().stream().map(f -> f._get()).toArray()))
        .collect(toList());
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

    final Query esQuery = buildQuery(query, tasksIds);

    // Apply tenant filtering
    final Query tenantAwareQuery =
        query.getTenantIds() == null
            ? tenantHelper.makeQueryTenantAware(esQuery)
            : tenantHelper.makeQueryTenantAware(esQuery, Set.of(query.getTenantIds()));

    // Build sort options and search after
    final List<SortOptions> sortOptions = buildSortOptions(query);
    final List<FieldValue> searchAfterValues = buildSearchAfterValues(query);

    // Determine page size
    final int size =
        (query.getSearchBefore() != null || query.getSearchBeforeOrEqual() != null)
            ? query.getPageSize() + 1
            : query.getPageSize();

    final var searchRequestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(
                ElasticsearchUtil.whereToSearch(
                    taskTemplate, getQueryTypeByTaskState(query.getState())))
            .query(tenantAwareQuery)
            .sort(sortOptions)
            .size(size);

    if (!searchAfterValues.isEmpty()) {
      searchRequestBuilder.searchAfter(searchAfterValues);
    }

    try {
      final SearchResponse<TaskEntity> response =
          es8Client.search(searchRequestBuilder.build(), TaskEntity.class);
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

  private Query buildQuery(final TaskQuery query, final List<String> taskIds) {
    Query stateQ =
        ElasticsearchUtil.mustNotQuery(
            ElasticsearchUtil.termsQuery(TaskTemplate.STATE, TaskState.CANCELED.name()));
    if (query.getState() != null) {
      stateQ = ElasticsearchUtil.termsQuery(TaskTemplate.STATE, query.getState().name());
    }

    Query assignedQ = null;
    Query assigneeQ = null;
    if (query.getAssigned() != null) {
      if (query.getAssigned()) {
        assignedQ = ElasticsearchUtil.existsQuery(TaskTemplate.ASSIGNEE);
      } else {
        assignedQ =
            ElasticsearchUtil.mustNotQuery(ElasticsearchUtil.existsQuery(TaskTemplate.ASSIGNEE));
      }
    }
    if (query.getAssignee() != null) {
      assigneeQ = ElasticsearchUtil.termsQuery(TaskTemplate.ASSIGNEE, query.getAssignee());
    }

    Query assigneesQ = null;
    if (query.getAssignees() != null) {
      assigneesQ =
          ElasticsearchUtil.termsQuery(TaskTemplate.ASSIGNEE, Arrays.asList(query.getAssignees()));
    }

    Query taskIdsQuery = null;
    Query flowNodeInstanceExistsQuery = null;
    if (taskIds != null) {
      taskIdsQuery = ElasticsearchUtil.termsQuery(TaskTemplate.KEY, taskIds);
    } else {
      flowNodeInstanceExistsQuery =
          ElasticsearchUtil.existsQuery(TaskTemplate.FLOW_NODE_INSTANCE_ID);
    }

    Query taskDefinitionQ = null;
    if (query.getTaskDefinitionId() != null) {
      taskDefinitionQ =
          ElasticsearchUtil.termsQuery(TaskTemplate.FLOW_NODE_BPMN_ID, query.getTaskDefinitionId());
    }

    Query candidateGroupQ = null;
    if (query.getCandidateGroup() != null) {
      candidateGroupQ =
          ElasticsearchUtil.termsQuery(TaskTemplate.CANDIDATE_GROUPS, query.getCandidateGroup());
    }

    Query candidateGroupsQ = null;
    if (query.getCandidateGroups() != null) {
      candidateGroupsQ =
          ElasticsearchUtil.termsQuery(
              TaskTemplate.CANDIDATE_GROUPS, Arrays.asList(query.getCandidateGroups()));
    }

    Query candidateUserQ = null;
    if (query.getCandidateUser() != null) {
      candidateUserQ =
          ElasticsearchUtil.termsQuery(TaskTemplate.CANDIDATE_USERS, query.getCandidateUser());
    }

    Query candidateUsersQ = null;
    if (query.getCandidateUsers() != null) {
      candidateUsersQ =
          ElasticsearchUtil.termsQuery(
              TaskTemplate.CANDIDATE_USERS, Arrays.asList(query.getCandidateUsers()));
    }

    Query candidateGroupsAndUserByCurrentUserQ = null;
    if (query.getTaskByCandidateUserOrGroups() != null) {
      candidateGroupsAndUserByCurrentUserQ =
          buildUserGroupBoolQuery(
              List.of(query.getTaskByCandidateUserOrGroups().getUserGroups()),
              query.getTaskByCandidateUserOrGroups().getUserName());
    }

    Query processInstanceIdQ = null;
    if (query.getProcessInstanceId() != null) {
      processInstanceIdQ =
          ElasticsearchUtil.termsQuery(
              TaskTemplate.PROCESS_INSTANCE_ID, query.getProcessInstanceId());
    }

    Query processDefinitionIdQ = null;
    if (query.getProcessDefinitionId() != null) {
      processDefinitionIdQ =
          ElasticsearchUtil.termsQuery(
              TaskTemplate.PROCESS_DEFINITION_ID, query.getProcessDefinitionId());
    }

    Query followUpQ = null;
    if (query.getFollowUpDate() != null) {
      followUpQ =
          buildRangeQuery(
              TaskTemplate.FOLLOW_UP_DATE,
              query.getFollowUpDate().getFrom(),
              query.getFollowUpDate().getTo(),
              true,
              true);
    }

    Query dueDateQ = null;
    if (query.getDueDate() != null) {
      dueDateQ =
          buildRangeQuery(
              TaskTemplate.DUE_DATE,
              query.getDueDate().getFrom(),
              query.getDueDate().getTo(),
              false,
              true);
    }

    Query implementationQ = null;
    if (query.getImplementation() != null) {
      implementationQ =
          ElasticsearchUtil.termsQuery(
              TaskTemplate.IMPLEMENTATION, query.getImplementation().name());
    }

    final Query priorityQ = buildPriorityQuery(query);

    Query jointQ =
        ElasticsearchUtil.joinWithAnd(
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
      jointQ = ElasticsearchUtil.matchAllQueryEs8();
    }
    return ElasticsearchUtil.constantScoreQuery(jointQ);
  }

  private Query buildRangeQuery(
      final String field,
      final Date from,
      final Date to,
      final boolean includeFrom,
      final boolean includeTo) {
    final var rangeBuilder = new UntypedRangeQuery.Builder().field(field);
    if (from != null) {
      if (includeFrom) {
        rangeBuilder.gte(JsonData.of(from));
      } else {
        rangeBuilder.gt(JsonData.of(from));
      }
    }
    if (to != null) {
      if (includeTo) {
        rangeBuilder.lte(JsonData.of(to));
      } else {
        rangeBuilder.lt(JsonData.of(to));
      }
    }
    return Query.of(q -> q.range(r -> r.untyped(rangeBuilder.build())));
  }

  /** Builds sort options list based on query parameters. */
  private List<SortOptions> buildSortOptions(final TaskQuery query) {
    final List<SortOptions> sortOptions = new ArrayList<>();

    final boolean isSortOnRequest = query.getSort() != null;

    final boolean directSorting = isDirectSorting(query);

    final SortOrder sort2Order = directSorting ? SortOrder.Asc : SortOrder.Desc;

    if (isSortOnRequest) {
      for (int i = 0; i < query.getSort().length; i++) {
        final TaskOrderBy orderBy = query.getSort()[i];
        final String field = orderBy.getField().toString();
        final SortOrder sortOrder =
            directSorting
                ? orderBy.getOrder().equals(Sort.DESC) ? SortOrder.Desc : SortOrder.Asc
                : orderBy.getOrder().equals(Sort.DESC) ? SortOrder.Asc : SortOrder.Desc;

        if (!orderBy.getField().equals(TaskSortFields.priority)) {
          sortOptions.add(applyDateSortScript(orderBy.getOrder(), field, sortOrder));
        } else {
          sortOptions.add(
              mapNullInSort(
                  TaskTemplate.PRIORITY, DEFAULT_PRIORITY, sortOrder, ScriptSortType.Number));
        }
      }
    } else {
      final String sort1Field =
          getOrDefaultFromMap(SORT_FIELD_PER_STATE, query.getState(), DEFAULT_SORT_FIELD);
      if (directSorting) {
        sortOptions.add(ElasticsearchUtil.sortOrder(sort1Field, SortOrder.Desc, "_last"));
      } else {
        sortOptions.add(ElasticsearchUtil.sortOrder(sort1Field, SortOrder.Asc, "_first"));
      }
    }

    sortOptions.add(ElasticsearchUtil.sortOrder(TaskTemplate.KEY, sort2Order));

    return sortOptions;
  }

  /**
   * Builds search after values list based on query parameters. In case of searchAfterOrEqual and
   * searchBeforeOrEqual, this method will ignore "orEqual" part.
   */
  private List<FieldValue> buildSearchAfterValues(final TaskQuery query) {
    final Object[] querySearchAfter;
    if (isDirectSorting(query)) {
      if (query.getSearchAfter() != null) {
        querySearchAfter = query.getSearchAfter();
      } else {
        querySearchAfter = query.getSearchAfterOrEqual();
      }
    } else {
      if (query.getSearchBefore() != null) {
        querySearchAfter = query.getSearchBefore();
      } else {
        querySearchAfter = query.getSearchBeforeOrEqual();
      }
    }
    return ElasticsearchUtil.searchAfterToFieldValues(querySearchAfter);
  }

  private boolean isDirectSorting(final TaskQuery query) {
    return query.getSearchAfter() != null
        || query.getSearchAfterOrEqual() != null
        || (query.getSearchBefore() == null && query.getSearchBeforeOrEqual() == null);
  }

  private SortOptions applyDateSortScript(
      final Sort sorting, final String field, final SortOrder sortOrder) {
    final String nullDate;
    if (sorting.equals(Sort.ASC)) {
      nullDate = "2099-12-31";
    } else {
      nullDate = "1900-01-01";
    }
    final String scriptSource =
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
            + "}";

    return SortOptions.of(
        s ->
            s.script(
                sc ->
                    sc.type(ScriptSortType.String)
                        .order(sortOrder)
                        .script(script -> script.source(scriptSource))));
  }

  private SortOptions mapNullInSort(
      final String field,
      final String defaultValue,
      final SortOrder order,
      final ScriptSortType sortType) {
    final String nullHandlingScript =
        String.format(
            "if (doc['%s'].size() == 0) { %s } else { doc['%s'].value }",
            field, defaultValue, field);

    return SortOptions.of(
        s ->
            s.script(
                sc ->
                    sc.type(sortType)
                        .order(order)
                        .script(script -> script.source(nullHandlingScript))));
  }

  private void updateTask(final String taskId, final Map<String, Object> updateFields) {
    try {
      final Hit<TaskEntity> taskHit = getRawTaskByUserTaskKey(taskId);
      final var taskEntity = taskHit.source();

      // update task with optimistic locking
      final var updateRequest =
          UpdateRequest.of(
              u ->
                  u.index(taskTemplate.getFullQualifiedName())
                      .id(taskHit.id())
                      .doc(updateFields)
                      .ifSeqNo(taskHit.seqNo())
                      .ifPrimaryTerm(taskHit.primaryTerm())
                      .routing(getRoutingToUpsertTask(taskEntity))
                      .refresh(co.elastic.clients.elasticsearch._types.Refresh.WaitFor));

      es8Client.update(updateRequest, Object.class);
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
        variableStoreElasticSearch
            .getProcessInstanceKeysWithMatchingVars(varNames, varValues)
            .stream()
            .map(String::valueOf)
            .toList();

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
    final var tasksIdsMatchingAllVars = new ArrayList<Set<String>>();

    for (var i = 0; i < varNames.size(); i++) {
      final var nameQuery =
          ElasticsearchUtil.termsQuery(SnapshotTaskVariableTemplate.NAME, varNames.get(i));
      final var valueQuery =
          ElasticsearchUtil.termsQuery(SnapshotTaskVariableTemplate.VALUE, varValues.get(i));
      final var boolQuery = ElasticsearchUtil.joinWithAnd(nameQuery, valueQuery);

      final var searchRequestBuilder =
          new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
              .index(taskVariableTemplate.getAlias())
              .query(boolQuery)
              .source(s -> s.filter(f -> f.includes(SnapshotTaskVariableTemplate.TASK_ID)));

      final var taskIds = new HashSet<String>();

      try {
        final var scrollStream =
            ElasticsearchUtil.scrollAllStream(
                es8Client, searchRequestBuilder, ElasticsearchUtil.MAP_CLASS);

        scrollStream
            .flatMap(response -> response.hits().hits().stream())
            .forEach(
                hit -> {
                  final var source = hit.source();
                  if (source != null) {
                    final var taskId = (String) source.get(SnapshotTaskVariableTemplate.TASK_ID);
                    if (taskId != null) {
                      taskIds.add(taskId);
                    }
                  }
                });

        tasksIdsMatchingAllVars.add(taskIds);

      } catch (final Exception e) {
        final var message =
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

  private Query buildUserGroupBoolQuery(final List<String> userGroups, final String userName) {
    final List<Query> shouldQueries = new ArrayList<>();

    // Additional clause for TaskTemplate.ASSIGNEE
    shouldQueries.add(ElasticsearchUtil.termsQuery(TaskTemplate.ASSIGNEE, userName));

    userGroups.forEach(
        group ->
            shouldQueries.add(ElasticsearchUtil.termsQuery(TaskTemplate.CANDIDATE_GROUPS, group)));

    shouldQueries.add(ElasticsearchUtil.termsQuery(TaskTemplate.CANDIDATE_USERS, userName));

    // Consider the tasks that have no candidate users and groups
    shouldQueries.add(
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.mustNot(ElasticsearchUtil.existsQuery(TaskTemplate.CANDIDATE_USERS))
                            .mustNot(
                                ElasticsearchUtil.existsQuery(TaskTemplate.CANDIDATE_GROUPS)))));

    return Query.of(q -> q.bool(b -> b.should(shouldQueries).minimumShouldMatch("1")));
  }

  private List<String> retrieveTaskIdByProcessInstanceId(
      final List<String> processIds, final TaskByVariables[] taskVariablesFilter) {
    final var variablesMap =
        Arrays.stream(taskVariablesFilter)
            .collect(Collectors.toMap(TaskByVariables::getName, TaskByVariables::getValue));
    final var tasks = getActiveTasksByProcessInstanceIds(processIds);
    final var request =
        tasks.stream()
            .map(
                task ->
                    VariableStore.GetVariablesRequest.createFrom(task)
                        .setVarNames(variablesMap.keySet().stream().toList()))
            .toList();
    return taskVariableSearchUtil.getTaskIdsContainingVariables(request, variablesMap);
  }

  private Query buildPriorityQuery(final TaskQuery query) {
    if (query.getPriority() != null) {
      final var priority = query.getPriority();
      if (priority.getEq() != null) {
        return ElasticsearchUtil.termsQuery(TaskTemplate.PRIORITY, priority.getEq());
      } else {
        final var rangeBuilder = new UntypedRangeQuery.Builder().field(TaskTemplate.PRIORITY);
        if (priority.getGt() != null) {
          rangeBuilder.gt(JsonData.of(priority.getGt()));
        }
        if (priority.getGte() != null) {
          rangeBuilder.gte(JsonData.of(priority.getGte()));
        }
        if (priority.getLt() != null) {
          rangeBuilder.lt(JsonData.of(priority.getLt()));
        }
        if (priority.getLte() != null) {
          rangeBuilder.lte(JsonData.of(priority.getLte()));
        }
        return Query.of(q -> q.range(r -> r.untyped(rangeBuilder.build())));
      }
    }
    return null;
  }
}
