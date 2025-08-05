/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.createMatchNoneQuery;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithOr;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.*;
import static org.apache.lucene.search.join.ScoreMode.None;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.join.query.JoinQueryBuilders.hasChildQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.IncidentStore;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.VariablesQueryDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Conditional(ElasticsearchCondition.class)
@Component
public class QueryHelper {
  public static final String WILD_CARD = "*";

  @Autowired private OperateProperties operateProperties;

  @Autowired private DateTimeFormatter dateTimeFormatter;

  @Autowired private PermissionsService permissionsService;

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private IncidentStore incidentStore;

  public SearchRequest createSearchRequest(final ListViewQueryDto processInstanceRequest) {
    if (processInstanceRequest.isFinished()) {
      return ElasticsearchUtil.createSearchRequest(listViewTemplate, ALL);
    }
    return ElasticsearchUtil.createSearchRequest(listViewTemplate, ONLY_RUNTIME);
  }

  public QueryBuilder createRequestQuery(final ListViewQueryDto request) {
    final QueryBuilder query = createQueryFragment(request);

    final TermQueryBuilder isProcessInstanceQuery =
        termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);
    final QueryBuilder queryBuilder = joinWithAnd(isProcessInstanceQuery, query);

    return constantScoreQuery(queryBuilder);
  }

  public ConstantScoreQueryBuilder createProcessInstancesQuery(final ListViewQueryDto query) {
    final TermQueryBuilder isProcessInstanceQuery =
        termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);
    final QueryBuilder queryBuilder =
        joinWithAnd(isProcessInstanceQuery, createQueryFragment(query));
    return constantScoreQuery(queryBuilder);
  }

  public QueryBuilder createQueryFragment(final ListViewQueryDto query) {
    return joinWithAnd(
        createRunningFinishedQuery(query),
        createRetriesLeftQuery(query),
        createActivityIdQuery(query),
        createIdsQuery(query),
        createErrorMessageQuery(query),
        createIncidentErrorHashCodeQuery(query),
        createStartDateQuery(query),
        createEndDateQuery(query),
        createProcessDefinitionKeysQuery(query),
        createBpmnProcessIdQuery(query),
        createExcludeIdsQuery(query),
        createVariablesQuery(query),
        createVariablesInQuery(query),
        createBatchOperationIdQuery(query),
        createParentInstanceIdQuery(query),
        createTenantIdQuery(query),
        createReadPermissionQuery());
  }

  private QueryBuilder createReadPermissionQuery() {
    if (!permissionsService.permissionsEnabled()) {
      return null;
    }
    final var allowed =
        permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE);
    if (allowed == null) {
      return null;
    }
    return allowed.isAll()
        ? QueryBuilders.matchAllQuery()
        : termsQuery(ListViewTemplate.BPMN_PROCESS_ID, allowed.getIds());
  }

  private QueryBuilder createBatchOperationIdQuery(final ListViewQueryDto query) {
    if (query.getBatchOperationId() != null) {
      return termQuery(ListViewTemplate.BATCH_OPERATION_IDS, query.getBatchOperationId());
    }
    return null;
  }

  private QueryBuilder createParentInstanceIdQuery(final ListViewQueryDto query) {
    if (query.getParentInstanceId() != null) {
      return termQuery(ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY, query.getParentInstanceId());
    }
    return null;
  }

  private QueryBuilder createTenantIdQuery(final ListViewQueryDto query) {
    if (query.getTenantId() != null) {
      return termQuery(ListViewTemplate.TENANT_ID, query.getTenantId());
    }
    return null;
  }

  private QueryBuilder createProcessDefinitionKeysQuery(final ListViewQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getProcessIds())) {
      return termsQuery(ListViewTemplate.PROCESS_KEY, query.getProcessIds());
    }
    return null;
  }

  private QueryBuilder createBpmnProcessIdQuery(final ListViewQueryDto query) {
    if (!StringUtils.isEmpty(query.getBpmnProcessId())) {
      final TermQueryBuilder bpmnProcessIdQ =
          termQuery(ListViewTemplate.BPMN_PROCESS_ID, query.getBpmnProcessId());
      TermQueryBuilder versionQ = null;
      if (query.getProcessVersion() != null) {
        versionQ = termQuery(ListViewTemplate.PROCESS_VERSION, query.getProcessVersion());
      }
      return joinWithAnd(bpmnProcessIdQ, versionQ);
    }
    return null;
  }

  private QueryBuilder createVariablesQuery(final ListViewQueryDto query) {
    final VariablesQueryDto variablesQuery = query.getVariable();
    if (variablesQuery != null && StringUtils.hasLength(variablesQuery.getValue())) {
      if (variablesQuery.getName() == null) {
        throw new InvalidRequestException("Variables query must provide not-null variable name.");
      }
      return hasChildQuery(
          VARIABLES_JOIN_RELATION,
          joinWithAnd(
              termQuery(VAR_NAME, variablesQuery.getName()),
              termQuery(VAR_VALUE, variablesQuery.getValue())),
          None);
    }
    return null;
  }

  private QueryBuilder createVariablesInQuery(final ListViewQueryDto query) {
    final VariablesQueryDto variablesQuery = query.getVariable();
    if (variablesQuery != null && !ArrayUtils.isEmpty(variablesQuery.getValues())) {
      if (variablesQuery.getName() == null) {
        throw new InvalidRequestException("Variables query must provide not-null variable name.");
      }
      return hasChildQuery(
          VARIABLES_JOIN_RELATION,
          joinWithAnd(
              termQuery(VAR_NAME, variablesQuery.getName()),
              termsQuery(VAR_VALUE, variablesQuery.getValues())),
          None);
    }
    return null;
  }

  private QueryBuilder createExcludeIdsQuery(final ListViewQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getExcludeIds())) {
      return boolQuery().mustNot(termsQuery(ListViewTemplate.ID, query.getExcludeIds()));
    }
    return null;
  }

  private QueryBuilder createEndDateQuery(final ListViewQueryDto query) {
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

  private QueryBuilder createStartDateQuery(final ListViewQueryDto query) {
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

  private QueryBuilder createErrorMessageAsAndMatchQuery(final String errorMessage) {
    return hasChildQuery(
        ACTIVITIES_JOIN_RELATION,
        QueryBuilders.matchQuery(ERROR_MSG, errorMessage).operator(Operator.AND),
        None);
  }

  private QueryBuilder createErrorMessageAsWildcardQuery(final String errorMessage) {
    return hasChildQuery(
        ACTIVITIES_JOIN_RELATION, QueryBuilders.wildcardQuery(ERROR_MSG, errorMessage), None);
  }

  private QueryBuilder createErrorMessageQuery(final ListViewQueryDto query) {
    final String errorMessage = query.getErrorMessage();
    if (!StringUtils.isEmpty(errorMessage)) {
      if (errorMessage.contains(WILD_CARD)) {
        return createErrorMessageAsWildcardQuery(errorMessage.toLowerCase());
      } else {
        return createErrorMessageAsAndMatchQuery(errorMessage);
      }
    }
    return null;
  }

  private QueryBuilder createIncidentErrorHashCodeQuery(final ListViewQueryDto query) {
    final Integer incidentErrorHashCode = query.getIncidentErrorHashCode();
    if (incidentErrorHashCode == null) {
      return null;
    }

    final List<IncidentEntity> incidents =
        incidentStore.getIncidentsByErrorHashCode(incidentErrorHashCode);
    final List<String> errors =
        (incidents == null)
            ? null
            : incidents.stream().map(IncidentEntity::getErrorMessage).toList();
    if ((errors == null) || errors.isEmpty()) {
      return createMatchNoneQuery();
    }

    final BoolQueryBuilder errorMessagesQuery = boolQuery();
    for (final String error : errors) {
      errorMessagesQuery.should(QueryBuilders.matchPhraseQuery(ERROR_MSG, error));
    }
    errorMessagesQuery.minimumShouldMatch(1);

    return hasChildQuery(ACTIVITIES_JOIN_RELATION, errorMessagesQuery, None);
  }

  private QueryBuilder createIdsQuery(final ListViewQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getIds())) {
      return termsQuery(ListViewTemplate.ID, query.getIds());
    }
    return null;
  }

  private QueryBuilder createRunningFinishedQuery(final ListViewQueryDto query) {

    final boolean active = query.isActive();
    final boolean incidents = query.isIncidents();
    final boolean running = query.isRunning();

    final boolean completed = query.isCompleted();
    final boolean canceled = query.isCanceled();
    final boolean finished = query.isFinished();

    if (!running && !finished) {
      // empty list should be returned
      return createMatchNoneQuery();
    }

    if (running && finished && active && incidents && completed && canceled) {
      // select all
      return null;
    }

    QueryBuilder runningQuery = null;

    if (running && (active || incidents)) {
      // running query
      runningQuery = boolQuery().mustNot(existsQuery(END_DATE));

      final QueryBuilder activeQuery = createActiveQuery(query);
      final QueryBuilder incidentsQuery = createIncidentsQuery(query);

      if (query.getActivityId() == null && query.isActive() && query.isIncidents()) {
        // we request all running instances
      } else {
        // some of the queries may be null
        runningQuery = joinWithAnd(runningQuery, joinWithOr(activeQuery, incidentsQuery));
      }
    }

    QueryBuilder finishedQuery = null;

    if (finished && (completed || canceled)) {

      // add finished query
      finishedQuery = existsQuery(END_DATE);

      final QueryBuilder completedQuery = createCompletedQuery(query);
      final QueryBuilder canceledQuery = createCanceledQuery(query);

      if (query.getActivityId() == null && query.isCompleted() && query.isCanceled()) {
        // we request all finished instances
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

  private QueryBuilder createRetriesLeftQuery(final ListViewQueryDto query) {
    if (query.isRetriesLeft()) {
      final QueryBuilder retriesLeftQuery = termQuery(JOB_FAILED_WITH_RETRIES_LEFT, true);
      return hasChildQuery(ACTIVITIES_JOIN_RELATION, retriesLeftQuery, None);
    }
    return null;
  }

  private QueryBuilder createActivityIdQuery(final ListViewQueryDto query) {
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
      completedActivityIdQuery =
          createActivityIdQuery(query.getActivityId(), FlowNodeState.COMPLETED);
    }
    QueryBuilder canceledActivityIdQuery = null;
    if (query.isCanceled()) {
      canceledActivityIdQuery =
          createActivityIdQuery(query.getActivityId(), FlowNodeState.TERMINATED);
    }
    return joinWithOr(
        activeActivityIdQuery,
        incidentActivityIdQuery,
        completedActivityIdQuery,
        canceledActivityIdQuery);
  }

  private QueryBuilder createCanceledQuery(final ListViewQueryDto query) {
    if (query.isCanceled()) {
      return termQuery(STATE, ProcessInstanceState.CANCELED.toString());
    }
    return null;
  }

  private QueryBuilder createCompletedQuery(final ListViewQueryDto query) {
    if (query.isCompleted()) {
      return termQuery(STATE, ProcessInstanceState.COMPLETED.toString());
    }
    return null;
  }

  private QueryBuilder createIncidentsQuery(final ListViewQueryDto query) {
    if (query.isIncidents()) {
      return termQuery(INCIDENT, true);
    }
    return null;
  }

  private QueryBuilder createActiveQuery(final ListViewQueryDto query) {
    if (query.isActive()) {
      return termQuery(INCIDENT, false);
    }
    return null;
  }

  private QueryBuilder createActivityIdQuery(final String activityId, final FlowNodeState state) {
    final QueryBuilder activitiesQuery = termQuery(ACTIVITY_STATE, state.name());
    final QueryBuilder activityIdQuery = termQuery(ACTIVITY_ID, activityId);
    QueryBuilder activityIsEndNodeQuery = null;
    if (state.equals(FlowNodeState.COMPLETED)) {
      activityIsEndNodeQuery = termQuery(ACTIVITY_TYPE, FlowNodeType.END_EVENT.name());
    }

    return hasChildQuery(
        ACTIVITIES_JOIN_RELATION,
        joinWithAnd(activitiesQuery, activityIdQuery, activityIsEndNodeQuery),
        None);
  }

  private QueryBuilder createActivityIdIncidentQuery(final String activityId) {
    final QueryBuilder activitiesQuery = termQuery(ACTIVITY_STATE, FlowNodeState.ACTIVE.name());
    final QueryBuilder activityIdQuery = termQuery(ACTIVITY_ID, activityId);
    final ExistsQueryBuilder incidentExists = existsQuery(ERROR_MSG);

    return hasChildQuery(
        ACTIVITIES_JOIN_RELATION,
        joinWithAnd(activitiesQuery, activityIdQuery, incidentExists),
        None);
  }
}
