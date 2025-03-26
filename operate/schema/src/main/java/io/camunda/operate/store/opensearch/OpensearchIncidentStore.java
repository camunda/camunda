/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.termAggregation;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.IncidentStore;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchIncidentStore implements IncidentStore {

  public static final Query ACTIVE_INCIDENT_QUERY =
      TermQuery.of(
              q ->
                  q.field(IncidentTemplate.STATE).value(FieldValue.of(IncidentState.ACTIVE.name())))
          ._toQuery();
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchIncidentStore.class);
  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private OperateProperties operateProperties;

  private Query activeIncidentConstantScore(final Query q) {
    return constantScore(and(ACTIVE_INCIDENT_QUERY, q));
  }

  @Override
  public IncidentEntity getIncidentById(final Long incidentKey) {
    final var key = incidentKey.toString();
    final var searchRequestBuilder =
        searchRequestBuilder(incidentTemplate, ONLY_RUNTIME)
            .query(withTenantCheck(activeIncidentConstantScore(ids(key))));
    return richOpenSearchClient.doc().searchUnique(searchRequestBuilder, IncidentEntity.class, key);
  }

  @Override
  public List<IncidentEntity> getIncidentsWithErrorTypesFor(
      final String treePath, final List<Map<ErrorType, Long>> errorTypes) {
    final String errorTypesAggName = "errorTypesAgg";
    final var request =
        searchRequestBuilder(incidentTemplate, ONLY_RUNTIME)
            .query(
                withTenantCheck(
                    constantScore(
                        and(term(IncidentTemplate.TREE_PATH, treePath), ACTIVE_INCIDENT_QUERY))))
            .aggregations(
                Map.of(
                    errorTypesAggName,
                    termAggregation(
                            IncidentTemplate.ERROR_TYPE,
                            ErrorType.values().length,
                            Map.of("_key", SortOrder.Asc))
                        ._toAggregation()));

    final OpenSearchDocumentOperations.AggregatedResult<IncidentEntity> result =
        richOpenSearchClient.doc().scrollValuesAndAggregations(request, IncidentEntity.class);

    result
        .aggregates()
        .get(errorTypesAggName)
        .sterms()
        .buckets()
        .array()
        .forEach(
            b -> {
              final ErrorType errorType = ErrorType.valueOf(b.key());
              errorTypes.add(Map.of(errorType, b.docCount()));
            });

    return result.values();
  }

  @Override
  public List<IncidentEntity> getIncidentsByProcessInstanceKey(final Long processInstanceKey) {
    final var searchRequestBuilder =
        searchRequestBuilder(incidentTemplate, ONLY_RUNTIME)
            .query(
                withTenantCheck(
                    activeIncidentConstantScore(
                        term(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKey))))
            .sort(sortOptions(IncidentTemplate.CREATION_TIME, SortOrder.Asc));

    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, IncidentEntity.class);
  }

  @Override
  public List<IncidentEntity> getIncidentsByErrorHashCode(final Integer incidentErrorHashCode) {
    final var searchRequestBuilder =
        searchRequestBuilder(incidentTemplate, ONLY_RUNTIME)
            .query(
                withTenantCheck(
                    activeIncidentConstantScore(
                        term(IncidentTemplate.ERROR_MSG_HASH, incidentErrorHashCode))))
            .sort(sortOptions(IncidentTemplate.CREATION_TIME, SortOrder.Asc));

    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, IncidentEntity.class);
  }

  @Override
  public Map<Long, List<Long>> getIncidentKeysPerProcessInstance(
      final List<Long> processInstanceKeys) {
    record Result(Long processInstanceKey) {}
    final int batchSize = operateProperties.getOpensearch().getBatchSize();
    final var searchRequestBuilder =
        searchRequestBuilder(incidentTemplate, RequestDSL.QueryType.ONLY_RUNTIME)
            .query(
                withTenantCheck(
                    activeIncidentConstantScore(
                        longTerms(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys))))
            .source(sourceInclude(IncidentTemplate.PROCESS_INSTANCE_KEY))
            .size(batchSize);
    final Map<Long, List<Long>> result = new HashMap<>();

    richOpenSearchClient
        .doc()
        .search(searchRequestBuilder, Result.class)
        .hits()
        .hits()
        .forEach(
            hit ->
                CollectionUtil.addToMap(
                    result, hit.source().processInstanceKey(), Long.valueOf(hit.id())));

    return result;
  }
}
