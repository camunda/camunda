/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.schema.indices.ProcessInstanceDependant.PROCESS_INSTANCE_ID;
import static io.camunda.tasklist.util.CollectionUtil.asMap;
import static io.camunda.tasklist.util.CollectionUtil.getOrDefaultFromMap;
import static io.camunda.tasklist.util.ElasticsearchUtil.SCROLL_KEEP_ALIVE_MS;
import static io.camunda.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.tasklist.util.ElasticsearchUtil.mapSearchHits;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.WAIT_UNTIL;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.queries.Sort;
import io.camunda.tasklist.queries.TaskByVariables;
import io.camunda.tasklist.queries.TaskOrderBy;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.store.util.TaskVariableSearchUtil;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.views.TaskSearchView;
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
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired private TaskVariableSearchUtil taskVariableSearchUtil;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private VariableStore variableStoreElasticSearch;

  @Autowired private TaskVariableTemplate taskVariableTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Override
  public TaskEntity getTask(final String id) {
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

  public List<TaskEntity> getTasksById(List<String> ids) {
    try {
      final SearchHit[] response = getTasksRawResponse(ids);
      return mapSearchHits(response, objectMapper, TaskEntity.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private SearchHit[] getTasksRawResponse(final List<String> ids) throws IOException {

    final QueryBuilder query = idsQuery().addIds(Arrays.toString(ids.toArray()));

    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(taskTemplate)
            .source(new SearchSourceBuilder().query(constantScoreQuery(query)));

    final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
    if (response.getHits().getTotalHits().value > 0) {
      return response.getHits().getHits();
    } else {
      throw new NotFoundException(String.format("No tasks were found for ids %s", ids.toString()));
    }
  }

  private SearchHit getTaskRawResponse(final String id) throws IOException {
    final QueryBuilder query = idsQuery().addIds(id);

    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(taskTemplate)
            .source(new SearchSourceBuilder().query(constantScoreQuery(query)));

    final SearchResponse response = tenantAwareClient.search(request);
    if (response.getHits().getTotalHits().value == 1) {
      return response.getHits().getHits()[0];
    } else if (response.getHits().getTotalHits().value > 1) {
      throw new NotFoundException(String.format("Unique task with id %s was not found", id));
    } else {
      throw new NotFoundException(String.format("Task with id %s was not found", id));
    }
  }

  @Override
  public List<String> getTaskIdsByProcessInstanceId(String processInstanceId) {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(taskTemplate)
            .source(
                SearchSourceBuilder.searchSource()
                    .query(termQuery(PROCESS_INSTANCE_ID, processInstanceId))
                    .fetchField(TaskTemplate.ID));
    try {
      return ElasticsearchUtil.scrollIdsToList(searchRequest, esClient);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  private List<TaskSearchView> mapTasksFromEntity(SearchResponse response) {
    return ElasticsearchUtil.mapSearchHits(
        response.getHits().getHits(),
        (sh) ->
            TaskSearchView.createFrom(
                ElasticsearchUtil.fromSearchHit(
                    sh.getSourceAsString(), objectMapper, TaskEntity.class),
                sh.getSortValues()));
  }

  @Override
  public List<TaskSearchView> getTasks(TaskQuery query) {
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

  private List<TaskSearchView> queryTasks(final TaskQuery query, String taskId) {
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
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining tasks: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private static ElasticsearchUtil.QueryType getQueryTypeByTaskState(TaskState taskState) {
    return TaskState.CREATED == taskState
        ? ElasticsearchUtil.QueryType.ONLY_RUNTIME
        : ElasticsearchUtil.QueryType.ALL;
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

  private QueryBuilder buildQuery(TaskQuery query, List<String> taskIds) {
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
    if (taskIds != null) {
      idsQuery = idsQuery().addIds(taskIds.toArray(new String[0]));
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
  private void applySorting(SearchSourceBuilder searchSourceBuilder, TaskQuery query) {

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
        final SortOrder sortOrder;
        final SortBuilder sortBuilder;

        final String nullDate;
        if (orderBy.getOrder().equals(Sort.ASC)) {
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
        if (directSorting) {
          sortOrder = orderBy.getOrder().equals(Sort.DESC) ? SortOrder.DESC : SortOrder.ASC;
        } else {
          sortOrder = orderBy.getOrder().equals(Sort.DESC) ? SortOrder.ASC : SortOrder.DESC;
        }
        sortBuilder =
            SortBuilders.scriptSort(script, ScriptSortBuilder.ScriptSortType.STRING)
                .order(sortOrder);
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
  @Override
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

  @Override
  public TaskEntity persistTaskClaim(TaskEntity taskBefore, String assignee) {

    updateTask(taskBefore.getId(), asMap(TaskTemplate.ASSIGNEE, assignee));

    return taskBefore.makeCopy().setAssignee(assignee);
  }

  @Override
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

  private List<String> getTasksContainsVarNameAndValue(TaskByVariables[] taskVariablesFilter) {
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
      List<String> varNames, List<String> varValues) {
    final List<Set<String>> tasksIdsMatchingAllVars = new ArrayList<>();

    for (int i = 0; i < varNames.size(); i++) {
      final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
      boolQuery.must(QueryBuilders.termQuery(VariableIndex.NAME, varNames.get(i)));
      boolQuery.must(QueryBuilders.termQuery(VariableIndex.VALUE, varValues.get(i)));

      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(boolQuery)
              .fetchSource(TaskVariableTemplate.TASK_ID, null);

      final SearchRequest searchRequest =
          new SearchRequest(taskVariableTemplate.getAlias()).source(searchSourceBuilder);
      searchRequest.scroll(new TimeValue(SCROLL_KEEP_ALIVE_MS));

      final Set<String> taskIds = new HashSet<>();

      try {
        SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
        String scrollId = searchResponse.getScrollId();

        List<String> scrollTaskIds =
            Arrays.stream(searchResponse.getHits().getHits())
                .map(hit -> (String) hit.getSourceAsMap().get(TaskVariableTemplate.TASK_ID))
                .collect(Collectors.toList());

        taskIds.addAll(scrollTaskIds);

        while (scrollTaskIds.size() > 0) {
          final SearchScrollRequest scrollRequest =
              new SearchScrollRequest(scrollId).scroll(new TimeValue(SCROLL_KEEP_ALIVE_MS));

          searchResponse = esClient.scroll(scrollRequest, RequestOptions.DEFAULT);
          scrollId = searchResponse.getScrollId();
          scrollTaskIds =
              Arrays.stream(searchResponse.getHits().getHits())
                  .map(hit -> (String) hit.getSourceAsMap().get(TaskVariableTemplate.TASK_ID))
                  .toList();
          taskIds.addAll(scrollTaskIds);
        }

        // Finalize the scroll to free the resources
        final ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
        clearScrollRequest.addScrollId(scrollId);
        esClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);

        tasksIdsMatchingAllVars.add(taskIds);

      } catch (IOException e) {
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

  private List<String> retrieveTaskIdByProcessInstanceId(
      List<String> processIds, TaskByVariables[] taskVariablesFilter) {
    final List<String> taskIdsCreated = new ArrayList<>();
    final Map<String, String> variablesMap =
        IntStream.range(0, taskVariablesFilter.length)
            .boxed()
            .collect(
                Collectors.toMap(
                    i -> taskVariablesFilter[i].getName(), i -> taskVariablesFilter[i].getValue()));

    for (String processId : processIds) {
      final List<String> taskIds = getTaskIdsByProcessInstanceId(processId);
      for (String taskId : taskIds) {
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
}
