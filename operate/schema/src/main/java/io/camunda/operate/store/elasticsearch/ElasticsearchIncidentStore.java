/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.MAP_CLASS;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scrollAllStream;
import static io.camunda.operate.util.ElasticsearchUtil.sortOrder;
import static io.camunda.operate.util.ElasticsearchUtil.whereToSearch;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.NamedValue;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.IncidentStore;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.ScrollException;
import io.camunda.operate.util.ElasticsearchTenantHelper;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchIncidentStore implements IncidentStore {

  public static final QueryBuilder ACTIVE_INCIDENT_QUERY =
      termQuery(IncidentTemplate.STATE, IncidentState.ACTIVE);
  public static final Query ACTIVE_INCIDENT_QUERY_ES8 =
      ElasticsearchUtil.termsQuery(IncidentTemplate.STATE, IncidentState.ACTIVE);
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchIncidentStore.class);

  @Autowired private ElasticsearchClient es8Client;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private OperateProperties operateProperties;

  @Autowired private ElasticsearchTenantHelper tenantHelper;

  @Override
  public IncidentEntity getIncidentById(final Long incidentKey) {
    final var idQuery = ElasticsearchUtil.idsQuery(incidentKey.toString());

    final var queryEs8 =
        ElasticsearchUtil.constantScoreQuery(joinWithAnd(idQuery, ACTIVE_INCIDENT_QUERY_ES8));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(queryEs8);

    final var searchRequestEs8 =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(incidentTemplate, ONLY_RUNTIME))
            .query(tenantAwareQuery)
            .build();

    try {
      final var res = es8Client.search(searchRequestEs8, IncidentEntity.class);
      if (res.hits().hits().size() == 1) {
        return res.hits().hits().getFirst().source();
      } else if (res.hits().hits().size() > 1) {
        throw new NotFoundException(
            String.format("Could not find unique incident with key '%s'.", incidentKey));
      } else {
        throw new NotFoundException(
            String.format("Could not find incident with key '%s'.", incidentKey));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining incident: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<IncidentEntity> getIncidentsWithErrorTypesFor(
      final String treePath, final List<Map<ErrorType, Long>> errorTypes) {
    final var query =
        ElasticsearchUtil.constantScoreQuery(
            ElasticsearchUtil.joinWithAnd(
                ElasticsearchUtil.termsQuery(IncidentTemplate.TREE_PATH, treePath),
                ACTIVE_INCIDENT_QUERY_ES8));

    final String errorTypesAggName = "errorTypesAgg";

    final var errorTypesAgg =
        TermsAggregation.of(
                t ->
                    t.field(IncidentTemplate.ERROR_TYPE)
                        .size(ErrorType.values().length)
                        .order(NamedValue.of("_key", SortOrder.Asc)))
            ._toAggregation();

    final var searchRequestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(incidentTemplate, ONLY_RUNTIME))
            .query(query)
            .aggregations(errorTypesAggName, errorTypesAgg);

    try {
      final var firstResponse = new AtomicBoolean(true);

      return ElasticsearchUtil.scrollAllStream(
              es8Client, searchRequestBuilder, IncidentEntity.class)
          .peek(
              res -> {
                if (firstResponse.compareAndSet(true, false)) {
                  populateErrorTypes(errorTypes, res, errorTypesAggName);
                }
              })
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .toList();

    } catch (final ScrollException e) {
      final String message =
          String.format("Exception occurred, while obtaining incidents: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<IncidentEntity> getIncidentsByProcessInstanceKey(final Long processInstanceKey) {
    final var query =
        ElasticsearchUtil.constantScoreQuery(
            joinWithAnd(
                ElasticsearchUtil.termsQuery(
                    IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKey),
                ACTIVE_INCIDENT_QUERY_ES8));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchRequestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(incidentTemplate, ONLY_RUNTIME))
            .query(tenantAwareQuery)
            .sort(
                sortOrder(
                    IncidentTemplate.CREATION_TIME,
                    co.elastic.clients.elasticsearch._types.SortOrder.Asc));

    try {
      return scrollAllStream(es8Client, searchRequestBuilder, IncidentEntity.class)
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .toList();

    } catch (final ScrollException e) {
      final String message =
          String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<IncidentEntity> getIncidentsByErrorHashCode(final Integer incidentErrorHashCode) {
    final var query =
        ElasticsearchUtil.constantScoreQuery(
            joinWithAnd(
                ElasticsearchUtil.termsQuery(
                    IncidentTemplate.ERROR_MSG_HASH, incidentErrorHashCode),
                ACTIVE_INCIDENT_QUERY_ES8));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchRequestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(incidentTemplate, ONLY_RUNTIME))
            .query(tenantAwareQuery)
            .sort(
                sortOrder(
                    IncidentTemplate.CREATION_TIME,
                    co.elastic.clients.elasticsearch._types.SortOrder.Asc));

    try {
      return scrollAllStream(es8Client, searchRequestBuilder, IncidentEntity.class)
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .toList();
    } catch (final ScrollException e) {
      final String message =
          String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<Long, List<Long>> getIncidentKeysPerProcessInstance(
      final List<Long> processInstanceKeys) {
    final int batchSize = operateProperties.getElasticsearch().getBatchSize();

    final var query =
        ElasticsearchUtil.constantScoreQuery(
            ElasticsearchUtil.joinWithAnd(
                ElasticsearchUtil.termsQuery(
                    IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys),
                ACTIVE_INCIDENT_QUERY_ES8));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchRequestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(incidentTemplate, ONLY_RUNTIME))
            .query(tenantAwareQuery)
            .source(s -> s.filter(f -> f.includes(IncidentTemplate.PROCESS_INSTANCE_KEY)))
            .size(batchSize);

    try {
      final var resStream =
          ElasticsearchUtil.scrollAllStream(es8Client, searchRequestBuilder, MAP_CLASS);
      return resStream
          .flatMap(res -> res.hits().hits().stream())
          .collect(
              Collectors.groupingBy(
                  hit ->
                      Long.valueOf(
                          hit.source().get(IncidentTemplate.PROCESS_INSTANCE_KEY).toString()),
                  Collectors.mapping(hit -> Long.valueOf(hit.id()), Collectors.toList())));
    } catch (final ScrollException e) {
      final String message =
          String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private void populateErrorTypes(
      final List<Map<ErrorType, Long>> errorTypes,
      final ResponseBody<IncidentEntity> res,
      final String errorTypesAggName) {

    if (res.aggregations() == null) {
      return;
    }

    final var errorTypesAggRes = res.aggregations().get(errorTypesAggName);
    if (errorTypesAggRes == null) {
      return;
    }

    final var sterms = errorTypesAggRes.sterms();
    if (sterms == null || sterms.buckets() == null || sterms.buckets().array() == null) {
      return;
    }

    sterms
        .buckets()
        .array()
        .forEach(
            b -> {
              final var errorType = ErrorType.valueOf(b.key().stringValue());
              errorTypes.add(Map.of(errorType, b.docCount()));
            });
  }
}
