/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scrollWith;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.IncidentStore;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchIncidentStore implements IncidentStore {

  public static final QueryBuilder ACTIVE_INCIDENT_QUERY =
      termQuery(IncidentTemplate.STATE, IncidentState.ACTIVE);
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchIncidentStore.class);
  @Autowired private RestHighLevelClient esClient;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private OperateProperties operateProperties;

  @Override
  public IncidentEntity getIncidentById(final Long incidentKey) {
    final IdsQueryBuilder idsQ = idsQuery().addIds(incidentKey.toString());
    final ConstantScoreQueryBuilder query =
        constantScoreQuery(joinWithAnd(idsQ, ACTIVE_INCIDENT_QUERY));
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
            .source(new SearchSourceBuilder().query(query));
    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value == 1) {
        return ElasticsearchUtil.fromSearchHit(
            response.getHits().getHits()[0].getSourceAsString(),
            objectMapper,
            IncidentEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
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
    final TermQueryBuilder processInstanceQuery = termQuery(IncidentTemplate.TREE_PATH, treePath);

    final String errorTypesAggName = "errorTypesAgg";

    final TermsAggregationBuilder errorTypesAgg =
        terms(errorTypesAggName)
            .field(IncidentTemplate.ERROR_TYPE)
            .size(ErrorType.values().length)
            .order(BucketOrder.key(true));

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
            .source(
                new SearchSourceBuilder()
                    .query(
                        constantScoreQuery(
                            joinWithAnd(processInstanceQuery, ACTIVE_INCIDENT_QUERY)))
                    .aggregation(errorTypesAgg));

    try {
      return tenantAwareClient.search(
          searchRequest,
          () -> {
            return ElasticsearchUtil.scroll(
                searchRequest,
                IncidentEntity.class,
                objectMapper,
                esClient,
                null,
                aggs ->
                    ((Terms) aggs.get(errorTypesAggName))
                        .getBuckets()
                        .forEach(
                            b -> {
                              final ErrorType errorType = ErrorType.valueOf(b.getKeyAsString());
                              errorTypes.add(Map.of(errorType, b.getDocCount()));
                            }));
          });
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining incidents: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<IncidentEntity> getIncidentsByProcessInstanceKey(final Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery =
        termQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final ConstantScoreQueryBuilder query =
        constantScoreQuery(joinWithAnd(processInstanceKeyQuery, ACTIVE_INCIDENT_QUERY));

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
            .source(
                new SearchSourceBuilder()
                    .query(query)
                    .sort(IncidentTemplate.CREATION_TIME, SortOrder.ASC));

    try {
      return tenantAwareClient.search(
          searchRequest,
          () -> {
            return ElasticsearchUtil.scroll(
                searchRequest, IncidentEntity.class, objectMapper, esClient);
          });
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<IncidentEntity> getIncidentsByErrorHashCode(final Integer incidentErrorHashCode) {
    final TermQueryBuilder incidentErrorHashCodeQuery =
        termQuery(IncidentTemplate.ERROR_MSG_HASH, incidentErrorHashCode);
    final ConstantScoreQueryBuilder query =
        constantScoreQuery(joinWithAnd(incidentErrorHashCodeQuery, ACTIVE_INCIDENT_QUERY));

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
            .source(
                new SearchSourceBuilder()
                    .query(query)
                    .sort(IncidentTemplate.CREATION_TIME, SortOrder.ASC));

    try {
      return tenantAwareClient.search(
          searchRequest,
          () -> {
            return ElasticsearchUtil.scroll(
                searchRequest, IncidentEntity.class, objectMapper, esClient);
          });
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<Long, List<Long>> getIncidentKeysPerProcessInstance(
      final List<Long> processInstanceKeys) {
    final QueryBuilder processInstanceKeysQuery =
        constantScoreQuery(
            joinWithAnd(
                termsQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys),
                ACTIVE_INCIDENT_QUERY));
    final int batchSize = operateProperties.getElasticsearch().getBatchSize();

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
            .source(
                new SearchSourceBuilder()
                    .query(processInstanceKeysQuery)
                    .fetchSource(IncidentTemplate.PROCESS_INSTANCE_KEY, null)
                    .size(batchSize));

    final Map<Long, List<Long>> result = new HashMap<>();
    try {
      tenantAwareClient.search(
          searchRequest,
          () -> {
            scrollWith(
                searchRequest,
                esClient,
                searchHits -> {
                  for (final SearchHit hit : searchHits.getHits()) {
                    CollectionUtil.addToMap(
                        result,
                        Long.valueOf(
                            hit.getSourceAsMap()
                                .get(IncidentTemplate.PROCESS_INSTANCE_KEY)
                                .toString()),
                        Long.valueOf(hit.getId()));
                  }
                },
                null,
                null);
            return null;
          });
      return result;
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }
}
