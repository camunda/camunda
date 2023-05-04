/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.es.reader;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.webapp.rest.dto.listview.*;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import static io.camunda.operate.schema.templates.ListViewTemplate.INCIDENT;
import static org.apache.lucene.search.join.ScoreMode.None;
import static io.camunda.operate.util.ElasticsearchUtil.createMatchNoneQuery;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithOr;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_STATE;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_TYPE;
import static io.camunda.operate.schema.templates.ListViewTemplate.END_DATE;
import static io.camunda.operate.schema.templates.ListViewTemplate.ERROR_MSG;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.STATE;
import static io.camunda.operate.schema.templates.ListViewTemplate.VARIABLES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.VAR_NAME;
import static io.camunda.operate.schema.templates.ListViewTemplate.VAR_VALUE;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.join.query.JoinQueryBuilders.hasChildQuery;

@Component
public class ListViewReader {

  private static final String WILD_CARD = "*";

  private static final Logger logger = LoggerFactory.getLogger(ListViewReader.class);

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  @Autowired
  private OperationReader operationReader;

  @Autowired(required = false)
  private PermissionsService permissionsService;

  /**
   * Queries process instances by different criteria (with pagination).
   * @param processInstanceRequest
   * @return
   */
  public ListViewResponseDto queryProcessInstances(ListViewRequestDto processInstanceRequest) {
    ListViewResponseDto result = new ListViewResponseDto();

    List<ProcessInstanceForListViewEntity> processInstanceEntities = queryListView(processInstanceRequest, result);
    List<Long> processInstanceKeys = CollectionUtil
        .map(processInstanceEntities, processInstanceEntity -> Long.valueOf(processInstanceEntity.getId()));

    final Map<Long, List<OperationEntity>> operationsPerProcessInstance = operationReader.getOperationsPerProcessInstanceKey(processInstanceKeys);

    final List<ListViewProcessInstanceDto> processInstanceDtoList = ListViewProcessInstanceDto.createFrom(processInstanceEntities, operationsPerProcessInstance, objectMapper);
    result.setProcessInstances(processInstanceDtoList);
    return result;
  }

  public List<ProcessInstanceForListViewEntity> queryListView(
      ListViewRequestDto processInstanceRequest, ListViewResponseDto result) {

    final QueryBuilder query = createRequestQuery(processInstanceRequest.getQuery());

    logger.debug("Process instance search request: \n{}", query.toString());

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        .query(query);

    applySorting(searchSourceBuilder, processInstanceRequest);

    SearchRequest searchRequest = createSearchRequest(processInstanceRequest.getQuery())
        .source(searchSourceBuilder);

    logger.debug("Search request will search in: \n{}", searchRequest.indices());

    try {
      SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      result.setTotalCount(response.getHits().getTotalHits().value);

      List<ProcessInstanceForListViewEntity> processInstanceEntities = ElasticsearchUtil.mapSearchHits(response.getHits().getHits(),
          (sh) -> {
            ProcessInstanceForListViewEntity entity = ElasticsearchUtil.fromSearchHit(sh.getSourceAsString(), objectMapper, ProcessInstanceForListViewEntity.class);
            entity.setSortValues(sh.getSortValues());
            return entity;
          });
      if (processInstanceRequest.getSearchBefore() != null) {
        Collections.reverse(processInstanceEntities);
      }
      return processInstanceEntities;
    } catch (IOException e) {
      final String message = String
          .format("Exception occurred, while obtaining instances list: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private void applySorting(SearchSourceBuilder searchSourceBuilder, ListViewRequestDto request) {

    String sortBy = getSortBy(request);

    final boolean directSorting = request.getSearchAfter() != null || request.getSearchBefore() == null;
    if (request.getSorting() != null) {
      SortBuilder sort1;
      SortOrder sort1DirectOrder = SortOrder.fromString(request.getSorting().getSortOrder());
      if (directSorting) {
        sort1 = SortBuilders.fieldSort(sortBy).order(sort1DirectOrder)
            .missing("_last");
      } else {
        sort1 = SortBuilders.fieldSort(sortBy)
            .order(reverseOrder(sort1DirectOrder)).missing("_first");
      }
      searchSourceBuilder.sort(sort1);
    }

    SortBuilder sort2;
    Object[] querySearchAfter;
    if (directSorting) { //this sorting is also the default one for 1st page
      sort2 = SortBuilders.fieldSort(ListViewTemplate.KEY).order(SortOrder.ASC);
      querySearchAfter = request.getSearchAfter(objectMapper); //may be null
    } else { //searchBefore != null
      //reverse sorting
      sort2 = SortBuilders.fieldSort(ListViewTemplate.KEY).order(SortOrder.DESC);
      querySearchAfter = request.getSearchBefore(objectMapper);
    }

    searchSourceBuilder
        .sort(sort2)
        .size(request.getPageSize());
    if (querySearchAfter != null) {
      searchSourceBuilder.searchAfter(querySearchAfter);
    }
  }

  private String getSortBy(final ListViewRequestDto request) {
    if (request.getSorting() != null) {
      String sortBy = request.getSorting().getSortBy();
      if (sortBy.equals(ListViewRequestDto.SORT_BY_PARENT_INSTANCE_ID)) {
        sortBy = ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY;
      } else if (sortBy.equals(ListViewTemplate.ID)) {
        //we sort by id as numbers, not as strings
        sortBy = ListViewTemplate.KEY;
      }
      return sortBy;
    }
    return null;
  }

  private SortOrder reverseOrder(final SortOrder sortOrder) {
    if (sortOrder.equals(SortOrder.ASC)) {
      return SortOrder.DESC;
    } else {
      return SortOrder.ASC;
    }
  }

  private SearchRequest createSearchRequest(ListViewQueryDto processInstanceRequest) {
    if (processInstanceRequest.isFinished()) {
      return ElasticsearchUtil.createSearchRequest(listViewTemplate, ALL);
    }
    return ElasticsearchUtil.createSearchRequest(listViewTemplate, ONLY_RUNTIME);
  }

  private QueryBuilder createRequestQuery(ListViewQueryDto request) {
    final QueryBuilder query = createQueryFragment(request);

    final TermQueryBuilder isProcessInstanceQuery = termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);
    final QueryBuilder queryBuilder = joinWithAnd(isProcessInstanceQuery, query);

    return constantScoreQuery(queryBuilder);
  }

  public ConstantScoreQueryBuilder createProcessInstancesQuery(ListViewQueryDto query) {
    final TermQueryBuilder isProcessInstanceQuery = termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);
    final QueryBuilder queryBuilder = joinWithAnd(isProcessInstanceQuery, createQueryFragment(query));
    return constantScoreQuery(queryBuilder);
  }

  public QueryBuilder createQueryFragment(ListViewQueryDto query) {
    return createQueryFragment(query, ALL);
  }

  public QueryBuilder createQueryFragment(ListViewQueryDto query, ElasticsearchUtil.QueryType queryType) {
    return joinWithAnd(
        createRunningFinishedQuery(query, queryType),
        createActivityIdQuery(query, queryType),
        createIdsQuery(query),
        createErrorMessageQuery(query),
        createStartDateQuery(query),
        createEndDateQuery(query),
        createProcessDefinitionKeysQuery(query),
        createBpmnProcessIdQuery(query),
        createExcludeIdsQuery(query),
        createVariablesQuery(query),
        createBatchOperatioIdQuery(query),
        createParentInstanceIdQuery(query),
        createReadPermissionQuery()
    );
  }

  private QueryBuilder createReadPermissionQuery() {
    return permissionsService == null ? null : permissionsService.createQueryForProcessesByPermission(IdentityPermission.READ);
  }

  private QueryBuilder createBatchOperatioIdQuery(ListViewQueryDto query) {
    if (query.getBatchOperationId() != null) {
      return termQuery(ListViewTemplate.BATCH_OPERATION_IDS, query.getBatchOperationId());
    }
    return null;
  }

  private QueryBuilder createParentInstanceIdQuery(ListViewQueryDto query) {
    if (query.getParentInstanceId() != null) {
      return termQuery(ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY, query.getParentInstanceId());
    }
    return null;
  }

  private QueryBuilder createProcessDefinitionKeysQuery(ListViewQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getProcessIds())) {
      return termsQuery(ListViewTemplate.PROCESS_KEY, query.getProcessIds());
    }
    return null;
  }

  private QueryBuilder createBpmnProcessIdQuery(ListViewQueryDto query) {
    if (!StringUtils.isEmpty(query.getBpmnProcessId())) {
      final TermQueryBuilder bpmnProcessIdQ = termQuery(ListViewTemplate.BPMN_PROCESS_ID, query.getBpmnProcessId());
      TermQueryBuilder versionQ = null;
      if (query.getProcessVersion() != null) {
        versionQ = termQuery(ListViewTemplate.PROCESS_VERSION, query.getProcessVersion());
      }
      return joinWithAnd(bpmnProcessIdQ, versionQ);
    }
    return null;
  }

  private QueryBuilder createVariablesQuery(ListViewQueryDto query) {
    VariablesQueryDto variablesQuery = query.getVariable();
    if (variablesQuery != null && !StringUtils.isEmpty(variablesQuery.getName())) {
      if (variablesQuery.getName() == null) {
        throw new InvalidRequestException("Variables query must provide not-null variable name.");
      }
      return hasChildQuery(VARIABLES_JOIN_RELATION,  joinWithAnd(termQuery(VAR_NAME, variablesQuery.getName()), termQuery(VAR_VALUE, variablesQuery.getValue())), None);
    }
    return null;
  }

  private QueryBuilder createExcludeIdsQuery(ListViewQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getExcludeIds())) {
      return boolQuery().mustNot(termsQuery(ListViewTemplate.ID, query.getExcludeIds()));
    }
    return null;
  }

  private QueryBuilder createEndDateQuery(ListViewQueryDto query) {
    if (query.getEndDateAfter() != null || query.getEndDateBefore() != null) {
      final RangeQueryBuilder rangeQueryBuilder = rangeQuery(ListViewTemplate.END_DATE);
      if (query.getEndDateAfter() != null) {
        rangeQueryBuilder.gte(dateTimeFormatter.format(query.getEndDateAfter()));
      }
      if (query.getEndDateBefore() != null) {
        rangeQueryBuilder.lt(dateTimeFormatter.format(query.getEndDateBefore()));
      }
      rangeQueryBuilder.format(operateProperties.getElasticsearch().getElsDateFormat());
      return rangeQueryBuilder;
    }
    return null;
  }

  private QueryBuilder createStartDateQuery(ListViewQueryDto query) {
    if (query.getStartDateAfter() != null || query.getStartDateBefore() != null) {
      final RangeQueryBuilder rangeQueryBuilder = rangeQuery(ListViewTemplate.START_DATE);
      if (query.getStartDateAfter() != null) {
        rangeQueryBuilder.gte(dateTimeFormatter.format(query.getStartDateAfter()));
      }
      if (query.getStartDateBefore() != null) {
        rangeQueryBuilder.lt(dateTimeFormatter.format(query.getStartDateBefore()));
      }
      rangeQueryBuilder.format(operateProperties.getElasticsearch().getElsDateFormat());

      return rangeQueryBuilder;
    }
    return null;
  }

  private QueryBuilder createErrorMessageAsAndMatchQuery(String errorMessage) {
    return hasChildQuery(ACTIVITIES_JOIN_RELATION,QueryBuilders.matchQuery(ERROR_MSG, errorMessage).operator(Operator.AND), None);
  }

  private QueryBuilder createErrorMessageAsWildcardQuery(String errorMessage) {
    return hasChildQuery(ACTIVITIES_JOIN_RELATION,QueryBuilders.wildcardQuery(ERROR_MSG, errorMessage), None);
  }

  private QueryBuilder createErrorMessageQuery(ListViewQueryDto query) {
    String errorMessage = query.getErrorMessage();
    if (!StringUtils.isEmpty(errorMessage)) {
      if(errorMessage.contains(WILD_CARD)) {
        return createErrorMessageAsWildcardQuery(errorMessage.toLowerCase());
      }else {
        return createErrorMessageAsAndMatchQuery(errorMessage);
      }
    }
    return null;
  }

  private QueryBuilder createIdsQuery(ListViewQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getIds())) {
      return termsQuery(ListViewTemplate.ID, query.getIds());
    }
    return null;
  }

  private QueryBuilder createRunningFinishedQuery(ListViewQueryDto query, ElasticsearchUtil.QueryType queryType) {

    boolean active = query.isActive();
    boolean incidents = query.isIncidents();
    boolean running = query.isRunning();

    boolean completed = query.isCompleted();
    boolean canceled = query.isCanceled();
    boolean finished = query.isFinished();

    if (!running && !finished) {
      //empty list should be returned
      return createMatchNoneQuery();
    }

    if (running && finished && active && incidents && completed && canceled) {
      //select all
      return null;
    }

    QueryBuilder runningQuery = null;

    if (running && (active || incidents)) {
      //running query
      runningQuery = boolQuery().mustNot(existsQuery(END_DATE));

      QueryBuilder activeQuery = createActiveQuery(query);
      QueryBuilder incidentsQuery = createIncidentsQuery(query);

      if (query.getActivityId() == null && query.isActive() && query.isIncidents()) {
         //we request all running instances
      } else {
        //some of the queries may be null
        runningQuery = joinWithAnd(runningQuery,
          joinWithOr(activeQuery, incidentsQuery));
      }
    }

    QueryBuilder finishedQuery = null;

    if (finished && (completed || canceled)) {

      //add finished query
      finishedQuery = existsQuery(END_DATE);

      QueryBuilder completedQuery = createCompletedQuery(query);
      QueryBuilder canceledQuery = createCanceledQuery(query);

      if (query.getActivityId() == null && query.isCompleted() && query.isCanceled()) {
        //we request all finished instances
      } else {
        finishedQuery = joinWithAnd(finishedQuery, joinWithOr(completedQuery, canceledQuery));
      }
    }

    final QueryBuilder processInstanceQuery = joinWithOr(runningQuery, finishedQuery);

    if (processInstanceQuery == null) {
      return createMatchNoneQuery();
    }

    return processInstanceQuery;

  }

  private QueryBuilder createActivityIdQuery(ListViewQueryDto query, ElasticsearchUtil.QueryType queryType) {
    if (StringUtils.isEmpty(query.getActivityId())) {
      return null;
    }
    QueryBuilder activeActivityIdQuery = null;
    if (query.isActive()) {
      activeActivityIdQuery = createActivityIdQuery(query.getActivityId(), FlowNodeState.ACTIVE);
    }
    QueryBuilder incidentActivityIdQuery = null;
    if (query.isIncidents()) {
      incidentActivityIdQuery = createActivityIdIncidentQuery(query.getActivityId());
    }
    QueryBuilder completedActivityIdQuery = null;
    if (query.isCompleted()) {
      completedActivityIdQuery = createActivityIdQuery(query.getActivityId(), FlowNodeState.COMPLETED);
    }
    QueryBuilder canceledActivityIdQuery = null;
    if (query.isCanceled()) {
      canceledActivityIdQuery = createActivityIdQuery(query.getActivityId(), FlowNodeState.TERMINATED);
    }
    return joinWithOr(activeActivityIdQuery, incidentActivityIdQuery, completedActivityIdQuery, canceledActivityIdQuery);
  }

  private QueryBuilder createCanceledQuery(ListViewQueryDto query) {
    if (query.isCanceled()) {
      return termQuery(STATE, ProcessInstanceState.CANCELED.toString());
    }
    return null;
  }

  private QueryBuilder createCompletedQuery(ListViewQueryDto query) {
    if (query.isCompleted()) {
      return termQuery(STATE, ProcessInstanceState.COMPLETED.toString());
    }
    return null;
  }

  private QueryBuilder createIncidentsQuery(ListViewQueryDto query) {
    if (query.isIncidents()) {
      return termQuery(INCIDENT, true);
    }
    return null;
  }

  private QueryBuilder createActiveQuery(ListViewQueryDto query) {
    if (query.isActive()) {
      return termQuery(INCIDENT, false);
    }
    return null;

  }

  private QueryBuilder createActivityIdQuery(String activityId, FlowNodeState state) {
    final QueryBuilder activitiesQuery = termQuery(ACTIVITY_STATE, state.name());
    final QueryBuilder activityIdQuery = termQuery(ACTIVITY_ID, activityId);
    QueryBuilder activityIsEndNodeQuery = null;
    if (state.equals(FlowNodeState.COMPLETED)) {
      activityIsEndNodeQuery = termQuery(ACTIVITY_TYPE, FlowNodeType.END_EVENT.name());
    }

    return hasChildQuery(ACTIVITIES_JOIN_RELATION,  joinWithAnd(activitiesQuery, activityIdQuery, activityIsEndNodeQuery), None);
  }

  private QueryBuilder createActivityIdIncidentQuery(String activityId) {
    final QueryBuilder activitiesQuery = termQuery(ACTIVITY_STATE, FlowNodeState.ACTIVE.name());
    final QueryBuilder activityIdQuery = termQuery(ACTIVITY_ID, activityId);
    final ExistsQueryBuilder incidentExists = existsQuery(ERROR_MSG);

    return hasChildQuery(ACTIVITIES_JOIN_RELATION,  joinWithAnd(activitiesQuery, activityIdQuery, incidentExists), None);
  }

}
