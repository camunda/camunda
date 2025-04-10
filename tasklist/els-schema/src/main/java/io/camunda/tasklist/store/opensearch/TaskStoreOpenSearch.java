/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static io.camunda.tasklist.util.CollectionUtil.asMap;
import static io.camunda.tasklist.util.CollectionUtil.getOrDefaultFromMap;
import static io.camunda.tasklist.util.OpenSearchUtil.SCROLL_KEEP_ALIVE_MS;
import static io.camunda.tasklist.util.OpenSearchUtil.joinQueryBuilderWithAnd;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
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
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.util.OpenSearchUtil.QueryType;
import io.camunda.tasklist.views.TaskSearchView;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.SnapshotTaskVariableEntity;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.ScriptSortType;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.Query.Builder;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.util.ObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class TaskStoreOpenSearch implements TaskStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskStoreOpenSearch.class);
  private static final Map<TaskState, String> SORT_FIELD_PER_STATE =
      Map.of(
          TaskState.CREATED, TaskTemplate.CREATION_TIME,
          TaskState.COMPLETED, TaskTemplate.COMPLETION_TIME,
          TaskState.CANCELED, TaskTemplate.COMPLETION_TIME);

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient osClient;

  @Autowired private TenantAwareOpenSearchClient tenantAwareClient;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private VariableStore variableStoreElasticSearch;

  @Autowired
  @Qualifier("tasklistSnapshotTaskVariableTemplate")
  private SnapshotTaskVariableTemplate taskVariableTemplate;

  @Autowired private TaskVariableSearchUtil taskVariableSearchUtil;

  @Override
  public TaskEntity getTask(final String id) {
    try {
      final var rawTask = getTaskRawResponse(id);
      return rawTask.source();
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public List<String> getTaskIdsByProcessInstanceId(final String processInstanceId) {
    final Query.Builder flowNodeInstanceQuery = new Query.Builder();
    flowNodeInstanceQuery.exists(t -> t.field(TaskTemplate.FLOW_NODE_INSTANCE_ID));

    final Query.Builder processInstanceIdQuery = new Query.Builder();
    processInstanceIdQuery.term(
        t -> t.field(TaskTemplate.PROCESS_INSTANCE_ID).value(FieldValue.of(processInstanceId)));

    final SearchRequest.Builder searchRequest =
        OpenSearchUtil.createSearchRequest(taskTemplate)
            .query(q -> joinQueryBuilderWithAnd(flowNodeInstanceQuery, processInstanceIdQuery))
            .fields(f -> f.field(TaskTemplate.KEY));

    try {
      return OpenSearchUtil.scrollUserTaskKeysToList(searchRequest, osClient);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public Map<String, String> getTaskIdsWithIndexByProcessDefinitionId(
      final String processDefinitionId) {
    final Query.Builder flowNodeInstanceQuery = new Query.Builder();
    flowNodeInstanceQuery.exists(t -> t.field(TaskTemplate.FLOW_NODE_INSTANCE_ID));

    final Query.Builder processInstanceIdQuery = new Query.Builder();
    processInstanceIdQuery.term(
        t -> t.field(TaskTemplate.PROCESS_DEFINITION_ID).value(FieldValue.of(processDefinitionId)));

    final SearchRequest.Builder searchRequest =
        OpenSearchUtil.createSearchRequest(taskTemplate)
            .query(q -> joinQueryBuilderWithAnd(flowNodeInstanceQuery, processInstanceIdQuery))
            .fields(f -> f.field(TaskTemplate.KEY));
    try {
      return OpenSearchUtil.scrollIdsWithIndexToMap(searchRequest, osClient);
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
    final Hit taskBeforeSearchHit;
    try {
      taskBeforeSearchHit = getTaskRawResponse(String.valueOf(taskBefore.getKey()));
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }

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
      final UpdateRequest.Builder updateRequest = new UpdateRequest.Builder();
      updateRequest
          .index(taskTemplate.getFullQualifiedName())
          .id(taskBeforeSearchHit.id())
          .doc(jsonMap)
          .refresh(Refresh.WaitFor)
          .ifSeqNo(taskBeforeSearchHit.seqNo())
          .ifPrimaryTerm(taskBeforeSearchHit.primaryTerm())
          .routing(taskBeforeSearchHit.routing());
      OpenSearchUtil.executeUpdate(osClient, updateRequest.build());
    } catch (final Exception e) {
      // we're OK with not updating the task here, it will be marked as completed within import
      LOGGER.error(e.getMessage(), e);
    }
    return completedTask;
  }

  @Override
  public TaskEntity rollbackPersistTaskCompletion(final TaskEntity taskBefore) {
    final Hit taskBeforeSearchHit;
    try {
      taskBeforeSearchHit = getTaskRawResponse(String.valueOf(taskBefore.getKey()));
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }

    final TaskEntity completedTask = makeCopyOf(taskBefore).setCompletionTime(null);

    try {
      // update task with optimistic locking
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(TaskTemplate.STATE, completedTask.getState());
      updateFields.put(TaskTemplate.COMPLETION_TIME, completedTask.getCompletionTime());

      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);
      final UpdateRequest.Builder updateRequest = new UpdateRequest.Builder();
      updateRequest
          .index(taskTemplate.getFullQualifiedName())
          .id(taskBeforeSearchHit.id())
          .doc(jsonMap)
          .refresh(Refresh.WaitFor)
          .ifSeqNo(taskBeforeSearchHit.seqNo())
          .ifPrimaryTerm(taskBeforeSearchHit.primaryTerm())
          .routing(taskBeforeSearchHit.routing());
      OpenSearchUtil.executeUpdate(osClient, updateRequest.build());
    } catch (final Exception e) {
      // we're OK with not updating the task here, it will be marked as completed within import
      LOGGER.error(e.getMessage(), e);
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
      final List<Hit<TaskEntity>> response = getTasksRawResponse(ids);
      return response.stream().map(Hit::source).collect(Collectors.toList());
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

    final Query.Builder esQuery = buildQuery(query, tasksIds);
    // TODO #104 define list of fields

    // TODO we can play around with query type here (2nd parameter), e.g. when we select for only
    // active tasks
    final SearchRequest.Builder sourceBuilder =
        OpenSearchUtil.createSearchRequest(taskTemplate, getQueryTypeByTaskState(query.getState()));
    sourceBuilder.query(esQuery.build());
    applySorting(sourceBuilder, query);

    try {
      final SearchResponse<TaskEntity> response =
          query.getTenantIds() == null
              ? tenantAwareClient.search(sourceBuilder, TaskEntity.class)
              : tenantAwareClient.searchByTenantIds(
                  sourceBuilder, TaskEntity.class, Set.of(query.getTenantIds()));
      final List<TaskSearchView> tasks = mapTasksFromEntity(response);

      if (tasks.size() > 0) {
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

  private List<TaskSearchView> mapTasksFromEntity(final SearchResponse<TaskEntity> response) {
    return response.hits().hits().stream()
        .map(sh -> TaskSearchView.createFrom(sh.source(), sh.sort().toArray(new String[0])))
        .toList();
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

  private Query.Builder buildQuery(final TaskQuery query, final List<String> taskIds) {
    final Query.Builder stateQ = new Query.Builder();
    stateQ.bool(
        b ->
            b.mustNot(
                mn ->
                    mn.term(
                        t ->
                            t.field(TaskTemplate.STATE)
                                .value(FieldValue.of(TaskState.CANCELED.name())))));
    if (query.getState() != null) {
      stateQ.term(t -> t.field(TaskTemplate.STATE).value(FieldValue.of(query.getState().name())));
    }
    Query.Builder assignedQ = null;
    Query.Builder assigneeQ = null;
    if (query.getAssigned() != null) {
      assignedQ = new Query.Builder();
      if (query.getAssigned()) {
        assignedQ.exists(e -> e.field(TaskTemplate.ASSIGNEE));
      } else {
        assignedQ.bool(b -> b.mustNot(mn -> mn.exists(e -> e.field(TaskTemplate.ASSIGNEE))));
      }
    }
    if (query.getAssignee() != null) {
      assigneeQ = new Query.Builder();
      assigneeQ.term(t -> t.field(TaskTemplate.ASSIGNEE).value(FieldValue.of(query.getAssignee())));
    }

    Query.Builder assigneesQ = null;
    if (query.getAssignees() != null) {
      assigneesQ = new Query.Builder();
      assigneesQ.terms(
          t ->
              t.field(TaskTemplate.ASSIGNEE)
                  .terms(
                      terms ->
                          terms.value(
                              Arrays.stream(query.getAssignees())
                                  .map(m -> FieldValue.of(m))
                                  .toList())));
    }

    Query.Builder taskIdsQuery = null;
    Query.Builder flowNodeInstanceExistsQuery = null;
    if (taskIds != null) {
      final var terms = taskIds.stream().map(FieldValue::of).toList();
      taskIdsQuery = new Query.Builder();
      taskIdsQuery.terms(t -> t.field(TaskTemplate.KEY).terms(v -> v.value(terms)));
    } else {
      flowNodeInstanceExistsQuery = new Builder();
      flowNodeInstanceExistsQuery.exists(t -> t.field(TaskTemplate.FLOW_NODE_INSTANCE_ID));
    }

    Query.Builder taskDefinitionQ = null;
    if (query.getTaskDefinitionId() != null) {
      taskDefinitionQ = new Query.Builder();
      taskDefinitionQ.term(
          t ->
              t.field(TaskTemplate.FLOW_NODE_BPMN_ID)
                  .value(FieldValue.of(query.getTaskDefinitionId())));
    }

    Query.Builder candidateGroupQ = null;
    if (query.getCandidateGroup() != null) {
      candidateGroupQ = new Query.Builder();
      candidateGroupQ.term(
          t ->
              t.field(TaskTemplate.CANDIDATE_GROUPS)
                  .value(FieldValue.of(query.getCandidateGroup())));
    }

    Query.Builder candidateGroupsQ = null;
    if (query.getCandidateGroups() != null) {
      candidateGroupsQ = new Query.Builder();
      candidateGroupsQ.terms(
          t ->
              t.field(TaskTemplate.CANDIDATE_GROUPS)
                  .terms(
                      terms ->
                          terms.value(
                              Arrays.stream(query.getCandidateGroups())
                                  .map(m -> FieldValue.of(m))
                                  .toList())));
    }

    Query.Builder candidateUserQ = null;
    if (query.getCandidateUser() != null) {
      candidateUserQ = new Query.Builder();
      candidateUserQ.term(
          t ->
              t.field(TaskTemplate.CANDIDATE_USERS).value(FieldValue.of(query.getCandidateUser())));
    }

    Query.Builder candidateUsersQ = null;
    if (query.getCandidateUsers() != null) {
      candidateUsersQ = new Query.Builder();
      candidateUsersQ.terms(
          t ->
              t.field(TaskTemplate.CANDIDATE_USERS)
                  .terms(
                      terms ->
                          terms.value(
                              Arrays.stream(query.getCandidateUsers())
                                  .map(m -> FieldValue.of(m))
                                  .toList())));
    }

    Query.Builder candidateGroupsAndUserByCurrentUserQ = null;
    if (query.getTaskByCandidateUserOrGroups() != null) {
      candidateGroupsAndUserByCurrentUserQ =
          returnUserGroupBoolQuery(
              List.of(query.getTaskByCandidateUserOrGroups().getUserGroups()),
              query.getTaskByCandidateUserOrGroups().getUserName());
    }

    Query.Builder processInstanceIdQ = null;
    if (query.getProcessInstanceId() != null) {
      processInstanceIdQ = new Query.Builder();
      processInstanceIdQ.term(
          t ->
              t.field(TaskTemplate.PROCESS_INSTANCE_ID)
                  .value(FieldValue.of(query.getProcessInstanceId())));
    }

    Query.Builder processDefinitionIdQ = null;
    if (query.getProcessDefinitionId() != null) {
      processDefinitionIdQ = new Query.Builder();
      processDefinitionIdQ.term(
          t ->
              t.field(TaskTemplate.PROCESS_DEFINITION_ID)
                  .value(FieldValue.of(query.getProcessDefinitionId())));
    }

    Query.Builder followUpQ = null;
    if (query.getFollowUpDate() != null) {
      followUpQ = new Query.Builder();
      followUpQ.range(
          r ->
              r.field(TaskTemplate.FOLLOW_UP_DATE)
                  .gte(JsonData.of(query.getFollowUpDate().getFrom()))
                  .lte(JsonData.of(query.getFollowUpDate().getTo())));
    }

    Query.Builder dueDateQ = null;
    if (query.getDueDate() != null) {
      dueDateQ = new Query.Builder();
      dueDateQ.range(
          r ->
              r.field(TaskTemplate.DUE_DATE)
                  .gte(JsonData.of(query.getDueDate().getFrom()))
                  .lte(JsonData.of(query.getDueDate().getTo())));
    }

    Query.Builder implementationQ = null;
    if (query.getImplementation() != null) {
      implementationQ = new Query.Builder();
      implementationQ.term(
          t ->
              t.field(TaskTemplate.IMPLEMENTATION)
                  .value(FieldValue.of(query.getImplementation().name())));
    }

    final Query.Builder priorityQ = buildPriorityQuery(query);

    final Query.Builder jointQ =
        joinQueryBuilderWithAnd(
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
      jointQ.matchAll(new MatchAllQuery.Builder().build());
    }
    final Query.Builder result = new Query.Builder();
    result.constantScore(cs -> cs.filter(jointQ.build()));
    return result;
  }

  private Query.Builder returnUserGroupBoolQuery(
      final List<String> userGroups, final String userName) {
    final Query.Builder userNameAssigneeQ = new Query.Builder();
    userNameAssigneeQ.term(t -> t.field(TaskTemplate.ASSIGNEE).value(FieldValue.of(userName)));

    final Query.Builder userNameCandidateUserQ = new Query.Builder();
    userNameCandidateUserQ.term(
        t -> t.field(TaskTemplate.CANDIDATE_USERS).value(FieldValue.of(userName)));

    Query.Builder userNameCandidateGroupsQ = null;
    for (final String group : userGroups) {
      final Query.Builder singleUserNameCandidateGroupQ = new Query.Builder();
      singleUserNameCandidateGroupQ.term(
          t -> t.field(TaskTemplate.CANDIDATE_GROUPS).value(FieldValue.of(group)));

      if (userNameCandidateGroupsQ == null) {
        userNameCandidateGroupsQ = singleUserNameCandidateGroupQ;
      } else {
        userNameCandidateGroupsQ =
            OpenSearchUtil.joinQueryBuilderWithOr(
                userNameCandidateGroupsQ, singleUserNameCandidateGroupQ);
      }
    }

    final Query.Builder shouldNotContainCandidateUserQ = new Query.Builder();
    shouldNotContainCandidateUserQ.bool(
        b -> b.mustNot(mn -> mn.exists(e -> e.field(TaskTemplate.CANDIDATE_USERS))));

    final Query.Builder shouldNotContainCandidateGroupQ = new Query.Builder();
    shouldNotContainCandidateGroupQ.bool(
        b -> b.mustNot(mn -> mn.exists(e -> e.field(TaskTemplate.CANDIDATE_GROUPS))));

    final Query.Builder shouldNotContainCandidateUserAndGroupQ =
        OpenSearchUtil.joinQueryBuilderWithAnd(
            shouldNotContainCandidateUserQ, shouldNotContainCandidateGroupQ);

    return OpenSearchUtil.joinQueryBuilderWithOr(
        userNameAssigneeQ,
        userNameCandidateUserQ,
        userNameCandidateGroupsQ,
        shouldNotContainCandidateUserAndGroupQ);
  }

  /**
   * In case of searchAfterOrEqual and searchBeforeOrEqual, this method will ignore "orEqual" part.
   *
   * @param searchRequestBuilder
   * @param query
   */
  private void applySorting(
      final SearchRequest.Builder searchRequestBuilder, final TaskQuery query) {

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

    final SortOptions sort2;
    String[] querySearchAfter = null; // may be null
    if (directSorting) { // this sorting is also the default one for 1st page
      sort2 =
          new SortOptions.Builder()
              .field(f -> f.field(TaskTemplate.KEY).order(SortOrder.Asc))
              .build();
      if (query.getSearchAfter() != null) {
        querySearchAfter = query.getSearchAfter();
      } else if (query.getSearchAfterOrEqual() != null) {
        querySearchAfter = query.getSearchAfterOrEqual();
      }
    } else { // searchBefore != null
      // reverse sorting
      sort2 =
          new SortOptions.Builder()
              .field(f -> f.field(TaskTemplate.KEY).order(SortOrder.Desc))
              .build();
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
                ? orderBy.getOrder().equals(Sort.DESC) ? SortOrder.Desc : SortOrder.Asc
                : orderBy.getOrder().equals(Sort.DESC) ? SortOrder.Asc : SortOrder.Desc;

        if (!orderBy.getField().equals(TaskSortFields.priority)) {
          searchRequestBuilder.sort(applyDateSortScript(orderBy.getOrder(), field, sortOrder));
        } else {
          searchRequestBuilder.sort(
              mapNullInSort(
                  TaskTemplate.PRIORITY, DEFAULT_PRIORITY, sortOrder, ScriptSortType.Number));
        }
      }

    } else {
      final String sort1Field;
      final SortOptions sort1;

      sort1Field = getOrDefaultFromMap(SORT_FIELD_PER_STATE, query.getState(), DEFAULT_SORT_FIELD);
      if (directSorting) {
        sort1 =
            new SortOptions.Builder()
                .field(
                    FieldSort.of(
                        f ->
                            f.field(sort1Field)
                                .order(SortOrder.Desc)
                                .missing(FieldValue.of("_last"))))
                .build();
      } else {
        sort1 =
            new SortOptions.Builder()
                .field(
                    FieldSort.of(
                        f ->
                            f.field(sort1Field)
                                .order(SortOrder.Asc)
                                .missing(FieldValue.of("_first"))))
                .build();
      }
      searchRequestBuilder.sort(sort1);
    }

    searchRequestBuilder.sort(sort2);
    // for searchBefore[orEqual] we will increase size by 1 to fill ou isFirst flag
    if (query.getSearchBefore() != null || query.getSearchBeforeOrEqual() != null) {
      searchRequestBuilder.size(query.getPageSize() + 1);
    } else {
      searchRequestBuilder.size(query.getPageSize());
    }
    if (querySearchAfter != null) {
      searchRequestBuilder.searchAfter(Arrays.stream(querySearchAfter).toList());
    }
  }

  private Function<SortOptions.Builder, ObjectBuilder<SortOptions>> applyDateSortScript(
      final Sort sorting, final String field, final SortOrder sortOrder) {
    final String nullDate;
    if (sorting.equals(Sort.ASC)) {
      nullDate = "2099-12-31";
    } else {
      nullDate = "1900-01-01";
    }
    final Script.Builder scriptBuilder = new Script.Builder();
    scriptBuilder.inline(
        in ->
            in.source(
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
                    + "}"));
    return s ->
        s.script(
            script ->
                script.script(scriptBuilder.build()).order(sortOrder).type(ScriptSortType.String));
  }

  private void updateTask(final String taskId, final Map<String, Object> updateFields) {
    try {
      final Hit searchHit = getTaskRawResponse(taskId);
      // update task with optimistic locking
      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);
      final UpdateRequest.Builder updateRequest = new UpdateRequest.Builder();
      updateRequest
          .index(taskTemplate.getFullQualifiedName())
          .id(searchHit.id())
          .doc(jsonMap)
          .refresh(Refresh.WaitFor)
          .ifSeqNo(searchHit.seqNo())
          .ifPrimaryTerm(searchHit.primaryTerm())
          .routing(searchHit.routing());
      OpenSearchUtil.executeUpdate(osClient, updateRequest.build());
    } catch (final Exception e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  private Hit<TaskEntity> getTaskRawResponse(final String id) throws IOException {
    final SearchRequest.Builder request =
        OpenSearchUtil.createSearchRequest(taskTemplate)
            .query(q -> q.term(t -> t.field(TaskTemplate.KEY).value(FieldValue.of(id))));

    final SearchResponse<TaskEntity> response = tenantAwareClient.search(request, TaskEntity.class);

    if (response.hits().total().value() == 1L) {
      return response.hits().hits().get(0);
    } else if (response.hits().total().value() > 1L) {
      throw new NotFoundException(String.format("Unique task with id %s was not found", id));
    } else {
      throw new NotFoundException(String.format("task with id %s was not found", id));
    }
  }

  private List<Hit<TaskEntity>> getTasksRawResponse(final List<String> ids) throws IOException {
    final var idTerms = ids.stream().map(FieldValue::of).toList();
    final SearchRequest.Builder request =
        OpenSearchUtil.createSearchRequest(taskTemplate)
            .query(s -> s.terms(f -> f.field(TaskTemplate.KEY).terms(t -> t.value(idTerms))));

    final SearchResponse<TaskEntity> response = tenantAwareClient.search(request, TaskEntity.class);
    if (response.hits().total().value() > 0L) {
      return response.hits().hits();
    } else {
      throw new NotFoundException(String.format("No tasks were found for ids %s", ids.toString()));
    }
  }

  private List<String> getTasksIdsCompletedWithMatchingVars(
      final List<String> varNames, final List<String> varValues) {

    final List<Set<String>> listOfTaskIdsSets = new ArrayList<>();

    for (int i = 0; i < varNames.size(); i++) {
      final Query.Builder nameQ = new Query.Builder();
      final int finalI = i;
      nameQ.terms(
          terms ->
              terms
                  .field(SnapshotTaskVariableTemplate.NAME)
                  .terms(
                      t ->
                          t.value(Collections.singletonList(FieldValue.of(varNames.get(finalI))))));

      final Query.Builder valueQ = new Query.Builder();
      valueQ.terms(
          terms ->
              terms
                  .field(SnapshotTaskVariableTemplate.VALUE)
                  .terms(
                      t ->
                          t.value(
                              Collections.singletonList(FieldValue.of(varValues.get(finalI))))));

      final Query boolQuery = OpenSearchUtil.joinWithAnd(nameQ, valueQ);

      final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();
      searchRequestBuilder
          .index(taskVariableTemplate.getAlias())
          .query(q -> q.constantScore(cs -> cs.filter(boolQuery)))
          .scroll(timeBuilder -> timeBuilder.time(SCROLL_KEEP_ALIVE_MS));

      final Set<String> taskIdsForCurrentVar = new HashSet<>();

      try {
        SearchResponse<SnapshotTaskVariableEntity> response =
            osClient.search(searchRequestBuilder.build(), SnapshotTaskVariableEntity.class);

        List<String> scrollTaskIds =
            response.hits().hits().stream()
                .map(hit -> hit.source().getTaskId())
                .collect(Collectors.toList());

        taskIdsForCurrentVar.addAll(scrollTaskIds);

        final String scrollId = response.scrollId();

        while (!scrollTaskIds.isEmpty()) {
          final ScrollRequest scrollRequest =
              ScrollRequest.of(
                  builder ->
                      builder
                          .scrollId(scrollId)
                          .scroll(new Time.Builder().time(SCROLL_KEEP_ALIVE_MS).build()));

          response = osClient.scroll(scrollRequest, SnapshotTaskVariableEntity.class);
          scrollTaskIds =
              response.hits().hits().stream()
                  .map(hit -> hit.source().getTaskId())
                  .collect(Collectors.toList());

          taskIdsForCurrentVar.addAll(scrollTaskIds);
        }

        OpenSearchUtil.clearScroll(scrollId, osClient);

        listOfTaskIdsSets.add(taskIdsForCurrentVar);

      } catch (final IOException e) {
        final String message =
            String.format("Exception occurred while obtaining taskIds: %s", e.getMessage());
        throw new TasklistRuntimeException(message, e);
      }
    }

    // Find the intersection of all sets
    return new ArrayList<>(
        listOfTaskIdsSets.stream()
            .reduce(
                (set1, set2) -> {
                  set1.retainAll(set2);
                  return set1;
                })
            .orElse(Collections.emptySet()));
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

  private Query.Builder buildPriorityQuery(final TaskQuery query) {
    if (query.getPriority() != null) {
      final var priority = query.getPriority();
      if (priority.getEq() != null) {
        return new Query.Builder()
                .term(
                    t ->
                        t.field(TaskTemplate.PRIORITY)
                            .value(FieldValue.of(((Integer) priority.getEq()))))
                .build()
                .toBuilder();
      } else {
        return new Query.Builder()
                .range(
                    r -> {
                      r = r.field(TaskTemplate.PRIORITY);
                      if (priority.getGt() != null) {
                        r = r.gt(JsonData.of(priority.getGt()));
                      }
                      if (priority.getGte() != null) {
                        r = r.gte(JsonData.of(priority.getGte()));
                      }
                      if (priority.getLt() != null) {
                        r = r.lt(JsonData.of(priority.getLt()));
                      }
                      if (priority.getLte() != null) {
                        r = r.lte(JsonData.of(priority.getLte()));
                      }
                      return r;
                    })
                .build()
                .toBuilder();
      }
    }
    return null;
  }

  private Function<SortOptions.Builder, ObjectBuilder<SortOptions>> mapNullInSort(
      final String field,
      final String defaultValue,
      final SortOrder order,
      final ScriptSortType sortType) {
    final String nullHandlingScript =
        String.format(
            "if (doc['%s'].size() == 0) { %s } else { doc['%s'].value }",
            field, defaultValue, field);

    final Script script = new Script.Builder().inline(i -> i.source(nullHandlingScript)).build();
    return f -> f.script(s -> s.script(script).order(order).type(sortType));
  }
}
