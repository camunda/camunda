/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.store.IncidentStore;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.CollectionUtil;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.termAggregation;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;


@Conditional(OpensearchCondition.class)
@Component
public class OpensearchIncidentStore implements IncidentStore {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchIncidentStore.class);
  public static Query ACTIVE_INCIDENT_QUERY = TermQuery.of(q -> q.field(IncidentTemplate.STATE).value(FieldValue.of(IncidentState.ACTIVE.name())))._toQuery();

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private OperateProperties operateProperties;

  private Query activeIncidentConstantScore(Query q) {
    return constantScore(and(ACTIVE_INCIDENT_QUERY, q));
  }

  @Override
  public IncidentEntity getIncidentById(Long incidentKey) {
    var key = incidentKey.toString();
    var searchRequestBuilder = searchRequestBuilder(incidentTemplate, ONLY_RUNTIME)
      .query(withTenantCheck(activeIncidentConstantScore(ids(key))));
    return richOpenSearchClient.doc().searchUnique(searchRequestBuilder, IncidentEntity.class, key);
  }

  @Override
  public List<IncidentEntity> getIncidentsByProcessInstanceKey(Long processInstanceKey) {
    var searchRequestBuilder = searchRequestBuilder(incidentTemplate, ONLY_RUNTIME)
      .query(withTenantCheck(activeIncidentConstantScore(term(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKey))))
      .sort(sortOptions(IncidentTemplate.CREATION_TIME, SortOrder.Asc));

    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, IncidentEntity.class);
  }

  @Override
  public Map<Long, List<Long>> getIncidentKeysPerProcessInstance(List<Long> processInstanceKeys) {
    record Result(Long processInstanceKey){}
    final int batchSize = operateProperties.getOpensearch().getBatchSize();
    var searchRequestBuilder = searchRequestBuilder(incidentTemplate, RequestDSL.QueryType.ONLY_RUNTIME)
        .query(withTenantCheck(activeIncidentConstantScore(longTerms(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys))))
        .source(sourceInclude(IncidentTemplate.PROCESS_INSTANCE_KEY))
        .size(batchSize);
    final Map<Long, List<Long>> result = new HashMap<>();

    richOpenSearchClient.doc().search(searchRequestBuilder, Result.class).hits().hits().forEach(
      hit -> CollectionUtil.addToMap(result, hit.source().processInstanceKey(), Long.valueOf(hit.id()))
    );

    return result;
  }

  @Override
  public List<IncidentEntity> getIncidentsWithErrorTypesFor(String treePath, List<Map<ErrorType,Long>> errorTypes) {
    final String errorTypesAggName = "errorTypesAgg";
    var request = searchRequestBuilder(incidentTemplate, ONLY_RUNTIME)
        .query(withTenantCheck(constantScore(
            and(
                term(IncidentTemplate.TREE_PATH, treePath),
                ACTIVE_INCIDENT_QUERY
            )))).aggregations(
                Map.of(errorTypesAggName, termAggregation(IncidentTemplate.ERROR_TYPE, ErrorType.values().length,Map.of("_key", SortOrder.Asc))
                    ._toAggregation()));

    OpenSearchDocumentOperations.AggregatedResult<IncidentEntity> result = richOpenSearchClient.doc().scrollValuesAndAggregations(request, IncidentEntity.class);

    result.aggregates()
      .get(errorTypesAggName)
      .sterms()
      .buckets()
      .array()
      .forEach(b -> {
        ErrorType errorType = ErrorType.valueOf(b.key());
        errorTypes.add(Map.of(errorType, b.docCount()));
      });

    return result.values();
  }
}
