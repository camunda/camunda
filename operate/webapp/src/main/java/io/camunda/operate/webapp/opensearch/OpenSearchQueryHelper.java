/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.exists;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.json;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.match;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchAll;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchNone;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.not;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.or;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.stringTerms;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.wildcardQuery;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.*;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.IncidentStore;
import io.camunda.operate.store.opensearch.dsl.QueryDSL;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.CollectionUtil;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.opensearch.client.opensearch._types.query_dsl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Conditional(OpensearchCondition.class)
@Component
public class OpenSearchQueryHelper {
  private static final String WILD_CARD = "*";

  @Autowired private OperateProperties operateProperties;

  @Autowired private DateTimeFormatter dateTimeFormatter;

  @Autowired private PermissionsService permissionsService;

  @Autowired private IncidentStore incidentStore;

  public Query createProcessInstancesQuery(final ListViewQueryDto query) {
    return constantScore(
        and(term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION), createQueryFragment(query)));
  }

  public Query createQueryFragment(final ListViewQueryDto query) {
    return createQueryFragment(query, RequestDSL.QueryType.ALL);
  }

  public Query createQueryFragment(
      final ListViewQueryDto query, final RequestDSL.QueryType queryType) {
    return and(
        runningFinishedQuery(query, queryType),
        createRetriesLeftQuery(query),
        activityIdQuery(query, queryType),
        idsQuery(query),
        errorMessageQuery(query),
        incidentErrorHashCodeQuery(query),
        dateRangeQuery(
            ListViewTemplate.START_DATE, query.getStartDateAfter(), query.getStartDateBefore()),
        dateRangeQuery(END_DATE, query.getEndDateAfter(), query.getEndDateBefore()),
        processDefinitionKeysQuery(query),
        bpmnProcessIdQuery(query),
        excludeIdsQuery(query),
        variablesQuery(query),
        batchOperationIdQuery(query),
        parentInstanceIdQuery(query),
        tenantIdQuery(query),
        readPermissionQuery());
  }

  private Query runningFinishedQuery(
      final ListViewQueryDto query, final RequestDSL.QueryType queryType) {
    final boolean active = query.isActive();
    final boolean incidents = query.isIncidents();
    final boolean running = query.isRunning();

    final boolean completed = query.isCompleted();
    final boolean canceled = query.isCanceled();
    final boolean finished = query.isFinished();

    if (!running && !finished) {
      // empty list should be returned
      return matchNone();
    }

    if (running && finished && active && incidents && completed && canceled) {
      // select all
      return null;
    }

    Query runningQuery = null;

    if (running && (active || incidents)) {
      // running query
      runningQuery = not(exists(END_DATE));

      final Query activeQuery = query.isActive() ? term(INCIDENT, false) : null;
      final Query incidentsQuery = query.isIncidents() ? term(INCIDENT, true) : null;

      if (query.getActivityId() == null && query.isActive() && query.isIncidents()) {
        // we request all running instances
      } else {
        // some of the queries may be null
        runningQuery = and(runningQuery, or(activeQuery, incidentsQuery));
      }
    }

    Query finishedQuery = null;

    if (finished && (completed || canceled)) {
      // add finished query
      finishedQuery = exists(END_DATE);

      final Query completedQuery =
          query.isCompleted() ? term(STATE, ProcessInstanceState.COMPLETED.toString()) : null;
      final Query canceledQuery =
          query.isCanceled() ? term(STATE, ProcessInstanceState.CANCELED.toString()) : null;

      if (query.getActivityId() == null && query.isCompleted() && query.isCanceled()) {
        // we request all finished instances
      } else {
        finishedQuery = and(finishedQuery, or(completedQuery, canceledQuery));
      }
    }

    final Query processInstanceQuery = or(runningQuery, finishedQuery);

    if (processInstanceQuery == null) {
      return matchNone();
    }

    return processInstanceQuery;
  }

  private Query createRetriesLeftQuery(final ListViewQueryDto query) {
    if (query.isRetriesLeft()) {
      final Query retriesLeftQuery = term(JOB_FAILED_WITH_RETRIES_LEFT, true);
      return QueryDSL.hasChildQuery(ACTIVITIES_JOIN_RELATION, retriesLeftQuery);
    }
    return null;
  }

  private Query activityIdQuery(final String activityId, final FlowNodeState state) {
    final Query query =
        and(
            term(ACTIVITY_STATE, state.name()),
            term(ACTIVITY_ID, activityId),
            state == FlowNodeState.COMPLETED
                ? term(ACTIVITY_TYPE, FlowNodeType.END_EVENT.name())
                : null);

    return QueryDSL.hasChildQuery(ACTIVITIES_JOIN_RELATION, query);
  }

  private Query activityIdIncidentQuery(final String activityId) {
    final Query query =
        and(
            term(ACTIVITY_STATE, FlowNodeState.ACTIVE.name()),
            term(ACTIVITY_ID, activityId),
            exists(ERROR_MSG));

    return QueryDSL.hasChildQuery(ACTIVITIES_JOIN_RELATION, query);
  }

  private Query activityIdQuery(
      final ListViewQueryDto query, final RequestDSL.QueryType queryType) {
    if (!StringUtils.hasLength(query.getActivityId())) {
      return null;
    }

    return or(
        query.isActive() ? activityIdQuery(query.getActivityId(), FlowNodeState.ACTIVE) : null,
        query.isIncidents() ? activityIdIncidentQuery(query.getActivityId()) : null,
        query.isCompleted()
            ? activityIdQuery(query.getActivityId(), FlowNodeState.COMPLETED)
            : null,
        query.isCanceled()
            ? activityIdQuery(query.getActivityId(), FlowNodeState.TERMINATED)
            : null);
  }

  private Query idsQuery(final ListViewQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getIds())) {
      return stringTerms(ListViewTemplate.ID, query.getIds());
    }
    return null;
  }

  private Query errorMessageQuery(final ListViewQueryDto query) {
    final String errorMessage = query.getErrorMessage();
    if (StringUtils.hasLength(errorMessage)) {
      if (errorMessage.contains(WILD_CARD)) {
        return QueryDSL.hasChildQuery(
            ACTIVITIES_JOIN_RELATION, wildcardQuery(ERROR_MSG, errorMessage.toLowerCase()));
      } else {
        return QueryDSL.hasChildQuery(
            ACTIVITIES_JOIN_RELATION, match(ERROR_MSG, errorMessage, Operator.And));
      }
    }
    return null;
  }

  private Query incidentErrorHashCodeQuery(final ListViewQueryDto query) {
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
      return matchNone();
    }

    final List<Query> shouldQueries = new ArrayList<>();
    for (final String error : errors) {
      final Query matchPhraseQuery =
          new MatchPhraseQuery.Builder().field(ERROR_MSG).query(error).build().toQuery();
      shouldQueries.add(matchPhraseQuery);
    }
    final Query errorMessagesQuery =
        new BoolQuery.Builder().should(shouldQueries).minimumShouldMatch("1").build().toQuery();

    return QueryDSL.hasChildQuery(ACTIVITIES_JOIN_RELATION, errorMessagesQuery);
  }

  private Query dateRangeQuery(
      final String field, final OffsetDateTime dateAfter, final OffsetDateTime dateBefore) {
    if (dateAfter != null || dateBefore != null) {
      final RangeQuery.Builder rangeQueryBuilder = new RangeQuery.Builder().field(field);
      if (dateAfter != null) {
        rangeQueryBuilder.gte(json(dateTimeFormatter.format(dateAfter)));
      }
      if (dateBefore != null) {
        rangeQueryBuilder.lt(json(dateTimeFormatter.format(dateBefore)));
      }
      rangeQueryBuilder.format(operateProperties.getOpensearch().getOsDateFormat());

      return rangeQueryBuilder.build()._toQuery();
    }
    return null;
  }

  private Query processDefinitionKeysQuery(final ListViewQueryDto query) {
    return CollectionUtil.isNotEmpty(query.getProcessIds())
        ? stringTerms(ListViewTemplate.PROCESS_KEY, query.getProcessIds())
        : null;
  }

  private Query bpmnProcessIdQuery(final ListViewQueryDto query) {
    if (!StringUtils.isEmpty(query.getBpmnProcessId())) {
      return and(
          term(ListViewTemplate.BPMN_PROCESS_ID, query.getBpmnProcessId()),
          query.getProcessVersion() != null
              ? term(ListViewTemplate.PROCESS_VERSION, query.getProcessVersion())
              : null);
    }
    return null;
  }

  private Query excludeIdsQuery(final ListViewQueryDto query) {
    return CollectionUtil.isNotEmpty(query.getExcludeIds())
        ? not(stringTerms(ListViewTemplate.ID, query.getExcludeIds()))
        : null;
  }

  private Query variablesQuery(final ListViewQueryDto query) {
    final VariablesQueryDto variablesQuery = query.getVariable();
    // We consider the query as non-empty if it is not null and has either a value or values
    final var nonEmptyQuery =
        variablesQuery != null
            && (StringUtils.hasLength(variablesQuery.getValue())
                || !ArrayUtils.isEmpty(variablesQuery.getValues()));
    if (nonEmptyQuery) {
      if (!StringUtils.hasLength(variablesQuery.getName())) {
        throw new InvalidRequestException("Variables query must provide not-null variable name.");
      }
      final Query valueQuery =
          variablesQuery.getValue() != null
              ? term(VAR_VALUE, variablesQuery.getValue())
              : stringTerms(VAR_VALUE, Arrays.asList(variablesQuery.getValues()));
      return QueryDSL.hasChildQuery(
          VARIABLES_JOIN_RELATION, and(term(VAR_NAME, variablesQuery.getName()), valueQuery));
    }
    return null;
  }

  private Query batchOperationIdQuery(final ListViewQueryDto query) {
    return query.getBatchOperationId() != null
        ? term(ListViewTemplate.BATCH_OPERATION_IDS, query.getBatchOperationId())
        : null;
  }

  private Query parentInstanceIdQuery(final ListViewQueryDto query) {
    return query.getParentInstanceId() != null
        ? term(ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY, query.getParentInstanceId())
        : null;
  }

  private Query tenantIdQuery(final ListViewQueryDto query) {
    return query.getTenantId() != null
        ? term(ListViewTemplate.TENANT_ID, query.getTenantId())
        : null;
  }

  private Query readPermissionQuery() {
    final var allowed =
        permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE);
    return allowed.isAll()
        ? matchAll()
        : stringTerms(ListViewTemplate.BPMN_PROCESS_ID, allowed.getIds());
  }
}
