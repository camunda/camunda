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
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithOr;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.*;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.DateRangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
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
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
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

  public SearchRequest.Builder createSearchRequest(final ListViewQueryDto processInstanceRequest) {
    final String index;
    if (processInstanceRequest.isFinished()) {
      index = ElasticsearchUtil.whereToSearch(listViewTemplate, ALL);
    } else {
      index = ElasticsearchUtil.whereToSearch(listViewTemplate, ONLY_RUNTIME);
    }
    return new SearchRequest.Builder().index(index);
  }

  public Query createRequestQuery(final ListViewQueryDto request) {
    final Query query = createQueryFragment(request);

    final Query isProcessInstanceQuery =
        ElasticsearchUtil.termsQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);
    final Query queryBuilder = joinWithAnd(isProcessInstanceQuery, query);

    return ElasticsearchUtil.constantScoreQuery(queryBuilder);
  }

  public Query createQueryFragment(final ListViewQueryDto query) {
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
        createBatchOperationIdQuery(query),
        createParentInstanceIdQuery(query),
        createTenantIdQuery(query),
        createReadPermissionQuery());
  }

  private Query createReadPermissionQuery() {
    final var allowed =
        permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE);
    return allowed.isAll()
        ? ElasticsearchUtil.matchAllQuery()
        : ElasticsearchUtil.termsQuery(ListViewTemplate.BPMN_PROCESS_ID, allowed.getIds());
  }

  private Query createBatchOperationIdQuery(final ListViewQueryDto query) {
    if (query.getBatchOperationId() != null) {
      return ElasticsearchUtil.termsQuery(
          ListViewTemplate.BATCH_OPERATION_IDS, query.getBatchOperationId());
    }
    return null;
  }

  private Query createParentInstanceIdQuery(final ListViewQueryDto query) {
    if (query.getParentInstanceId() != null) {
      return ElasticsearchUtil.termsQuery(
          ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY, query.getParentInstanceId());
    }
    return null;
  }

  private Query createTenantIdQuery(final ListViewQueryDto query) {
    if (query.getTenantId() != null) {
      return ElasticsearchUtil.termsQuery(ListViewTemplate.TENANT_ID, query.getTenantId());
    }
    return null;
  }

  private Query createProcessDefinitionKeysQuery(final ListViewQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getProcessIds())) {
      return ElasticsearchUtil.termsQuery(ListViewTemplate.PROCESS_KEY, query.getProcessIds());
    }
    return null;
  }

  private Query createBpmnProcessIdQuery(final ListViewQueryDto query) {
    if (!StringUtils.isEmpty(query.getBpmnProcessId())) {
      final Query bpmnProcessIdQ =
          ElasticsearchUtil.termsQuery(ListViewTemplate.BPMN_PROCESS_ID, query.getBpmnProcessId());
      Query versionQ = null;
      if (query.getProcessVersion() != null) {
        versionQ =
            ElasticsearchUtil.termsQuery(
                ListViewTemplate.PROCESS_VERSION, query.getProcessVersion());
      }
      return joinWithAnd(bpmnProcessIdQ, versionQ);
    }
    return null;
  }

  private Query createVariablesQuery(final ListViewQueryDto query) {
    final VariablesQueryDto variablesQuery = query.getVariable();
    if (variablesQuery == null) {
      return null;
    }

    if (variablesQuery.getName() == null) {
      throw new InvalidRequestException("Variables query must provide not-null variable name.");
    }

    final List<String> values;
    if (StringUtils.hasLength(variablesQuery.getValue())) {
      values = List.of(variablesQuery.getValue());
    } else if (!ArrayUtils.isEmpty(variablesQuery.getValues())) {
      values = Arrays.asList(variablesQuery.getValues());
    } else {
      return null;
    }

    return ElasticsearchUtil.hasChildQuery(
        VARIABLES_JOIN_RELATION,
        joinWithAnd(
            ElasticsearchUtil.termsQuery(VAR_NAME, variablesQuery.getName()),
            ElasticsearchUtil.termsQuery(VAR_VALUE, values)),
        ChildScoreMode.None);
  }

  private Query createExcludeIdsQuery(final ListViewQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getExcludeIds())) {
      return Query.of(
          q ->
              q.bool(
                  b ->
                      b.mustNot(
                          ElasticsearchUtil.termsQuery(
                              ListViewTemplate.ID, query.getExcludeIds()))));
    }
    return null;
  }

  private Query createEndDateQuery(final ListViewQueryDto query) {
    if (query.getEndDateAfter() != null || query.getEndDateBefore() != null) {
      final var builder = new DateRangeQuery.Builder();
      builder.field(ListViewTemplate.END_DATE);
      builder.format(operateProperties.getElasticsearch().getElsDateFormat());
      if (query.getEndDateAfter() != null) {
        builder.gte(dateTimeFormatter.format(query.getEndDateAfter()));
      }
      if (query.getEndDateBefore() != null) {
        builder.lt(dateTimeFormatter.format(query.getEndDateBefore()));
      }
      return builder.build()._toRangeQuery()._toQuery();
    }
    return null;
  }

  private Query createStartDateQuery(final ListViewQueryDto query) {
    if (query.getStartDateAfter() != null || query.getStartDateBefore() != null) {
      final var builder = new DateRangeQuery.Builder();
      builder.field(ListViewTemplate.START_DATE);
      builder.format(operateProperties.getElasticsearch().getElsDateFormat());
      if (query.getStartDateAfter() != null) {
        builder.gte(dateTimeFormatter.format(query.getStartDateAfter()));
      }
      if (query.getStartDateBefore() != null) {
        builder.lt(dateTimeFormatter.format(query.getStartDateBefore()));
      }
      return builder.build()._toRangeQuery()._toQuery();
    }
    return null;
  }

  private Query createErrorMessageAsAndMatchQuery(final String errorMessage) {
    return ElasticsearchUtil.hasChildQuery(
        ACTIVITIES_JOIN_RELATION,
        Query.of(q -> q.match(m -> m.field(ERROR_MSG).query(errorMessage).operator(Operator.And))),
        ChildScoreMode.None);
  }

  private Query createErrorMessageAsWildcardQuery(final String errorMessage) {
    return ElasticsearchUtil.hasChildQuery(
        ACTIVITIES_JOIN_RELATION,
        Query.of(q -> q.wildcard(w -> w.field(ERROR_MSG).value(errorMessage))),
        ChildScoreMode.None);
  }

  private Query createErrorMessageQuery(final ListViewQueryDto query) {
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

  private Query createIncidentErrorHashCodeQuery(final ListViewQueryDto query) {
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
      return ElasticsearchUtil.createMatchNoneQueryEs8().build()._toQuery();
    }

    final var boolBuilder = new BoolQuery.Builder();
    for (final String error : errors) {
      boolBuilder.should(Query.of(sq -> sq.matchPhrase(mp -> mp.field(ERROR_MSG).query(error))));
    }
    boolBuilder.minimumShouldMatch("1");

    return ElasticsearchUtil.hasChildQuery(
        ACTIVITIES_JOIN_RELATION, boolBuilder.build()._toQuery(), ChildScoreMode.None);
  }

  private Query createIdsQuery(final ListViewQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getIds())) {
      return ElasticsearchUtil.termsQuery(ListViewTemplate.ID, query.getIds());
    }
    return null;
  }

  private Query createRunningFinishedQuery(final ListViewQueryDto query) {

    final boolean active = query.isActive();
    final boolean incidents = query.isIncidents();
    final boolean running = query.isRunning();

    final boolean completed = query.isCompleted();
    final boolean canceled = query.isCanceled();
    final boolean finished = query.isFinished();

    if (!running && !finished) {
      // empty list should be returned
      return ElasticsearchUtil.createMatchNoneQueryEs8().build()._toQuery();
    }

    if (running && finished && active && incidents && completed && canceled) {
      // select all
      return null;
    }

    Query runningQuery = null;

    if (running && (active || incidents)) {
      // running query
      runningQuery = Query.of(q -> q.bool(b -> b.mustNot(ElasticsearchUtil.existsQuery(END_DATE))));

      final Query activeQuery = createActiveQuery(query);
      final Query incidentsQuery = createIncidentsQuery(query);

      if (query.getActivityId() == null && query.isActive() && query.isIncidents()) {
        // we request all running instances
      } else {
        // some of the queries may be null
        runningQuery = joinWithAnd(runningQuery, joinWithOr(activeQuery, incidentsQuery));
      }
    }

    Query finishedQuery = null;

    if (finished && (completed || canceled)) {

      // add finished query
      finishedQuery = ElasticsearchUtil.existsQuery(END_DATE);

      final Query completedQuery = createCompletedQuery(query);
      final Query canceledQuery = createCanceledQuery(query);

      if (query.getActivityId() == null && query.isCompleted() && query.isCanceled()) {
        // we request all finished instances
      } else {
        finishedQuery = joinWithAnd(finishedQuery, joinWithOr(completedQuery, canceledQuery));
      }
    }

    final Query processInstanceQuery = joinWithOr(runningQuery, finishedQuery);

    if (processInstanceQuery == null) {
      return ElasticsearchUtil.createMatchNoneQueryEs8().build()._toQuery();
    }

    return processInstanceQuery;
  }

  private Query createRetriesLeftQuery(final ListViewQueryDto query) {
    if (query.isRetriesLeft()) {
      final Query retriesLeftQuery =
          ElasticsearchUtil.termsQuery(JOB_FAILED_WITH_RETRIES_LEFT, true);
      return ElasticsearchUtil.hasChildQuery(
          ACTIVITIES_JOIN_RELATION, retriesLeftQuery, ChildScoreMode.None);
    }
    return null;
  }

  private Query createActivityIdQuery(final ListViewQueryDto query) {
    if (StringUtils.isEmpty(query.getActivityId())) {
      return null;
    }
    Query activeActivityIdQuery = null;
    if (query.isActive()) {
      activeActivityIdQuery = createActivityIdQuery(query.getActivityId(), FlowNodeState.ACTIVE);
    }
    Query incidentActivityIdQuery = null;
    if (query.isIncidents()) {
      incidentActivityIdQuery = createActivityIdIncidentQuery(query.getActivityId());
    }
    Query completedActivityIdQuery = null;
    if (query.isCompleted()) {
      completedActivityIdQuery =
          createActivityIdQuery(query.getActivityId(), FlowNodeState.COMPLETED);
    }
    Query canceledActivityIdQuery = null;
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

  private Query createCanceledQuery(final ListViewQueryDto query) {
    if (query.isCanceled()) {
      return ElasticsearchUtil.termsQuery(STATE, ProcessInstanceState.CANCELED.toString());
    }
    return null;
  }

  private Query createCompletedQuery(final ListViewQueryDto query) {
    if (query.isCompleted()) {
      return ElasticsearchUtil.termsQuery(STATE, ProcessInstanceState.COMPLETED.toString());
    }
    return null;
  }

  private Query createIncidentsQuery(final ListViewQueryDto query) {
    if (query.isIncidents()) {
      return ElasticsearchUtil.termsQuery(INCIDENT, true);
    }
    return null;
  }

  private Query createActiveQuery(final ListViewQueryDto query) {
    if (query.isActive()) {
      return ElasticsearchUtil.termsQuery(INCIDENT, false);
    }
    return null;
  }

  private Query createActivityIdQuery(final String activityId, final FlowNodeState state) {
    final Query activitiesQuery = ElasticsearchUtil.termsQuery(ACTIVITY_STATE, state.name());
    final Query activityIdQuery = ElasticsearchUtil.termsQuery(ACTIVITY_ID, activityId);
    final Query activityIsEndNodeQuery;
    if (state.equals(FlowNodeState.COMPLETED)) {
      activityIsEndNodeQuery =
          ElasticsearchUtil.termsQuery(ACTIVITY_TYPE, FlowNodeType.END_EVENT.name());
    } else {
      activityIsEndNodeQuery = null;
    }

    return ElasticsearchUtil.hasChildQuery(
        ACTIVITIES_JOIN_RELATION,
        joinWithAnd(activitiesQuery, activityIdQuery, activityIsEndNodeQuery),
        ChildScoreMode.None);
  }

  private Query createActivityIdIncidentQuery(final String activityId) {
    final Query activitiesQuery =
        ElasticsearchUtil.termsQuery(ACTIVITY_STATE, FlowNodeState.ACTIVE.name());
    final Query activityIdQuery = ElasticsearchUtil.termsQuery(ACTIVITY_ID, activityId);
    final Query activityHasIncident = ElasticsearchUtil.termsQuery(INCIDENT, true);

    return ElasticsearchUtil.hasChildQuery(
        ACTIVITIES_JOIN_RELATION,
        joinWithAnd(activitiesQuery, activityIdQuery, activityHasIncident),
        ChildScoreMode.None);
  }

  /** Setter for PermissionsService for testing purposes. */
  void setPermissionsService(final PermissionsService permissionsService) {
    this.permissionsService = permissionsService;
  }
}
