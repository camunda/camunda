/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.BUSINESS_VALUE_OVERVIEW_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexOperationBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.repository.BusinessValueOverviewRepository;
import io.camunda.optimize.service.db.schema.index.BusinessValueOverviewIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class BusinessValueOverviewRepositoryES implements BusinessValueOverviewRepository {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BusinessValueOverviewRepositoryES.class);

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public BusinessValueOverviewRepositoryES(
      final OptimizeElasticsearchClient esClient, final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public void bulkUpsert(
      final List<BusinessValueOverviewDto> rows, final boolean refreshImmediately) {
    if (rows == null || rows.isEmpty()) {
      return;
    }
    final BulkRequest bulkRequest =
        BulkRequest.of(
            b -> {
              if (refreshImmediately) {
                b.refresh(Refresh.True);
              }
              rows.forEach(
                  row ->
                      b.operations(
                          op ->
                              op.index(
                                  OptimizeIndexOperationBuilderES.of(
                                      i ->
                                          i.optimizeIndex(
                                                  esClient, BUSINESS_VALUE_OVERVIEW_INDEX_NAME)
                                              .id(
                                                  BusinessValueOverviewRepository.documentId(
                                                      row.getTenantId(),
                                                      row.getProcessDefinitionKey(),
                                                      row.getMetricRange()))
                                              .document(row)))));
              return b;
            });
    esClient.doBulkRequest(bulkRequest, BUSINESS_VALUE_OVERVIEW_INDEX_NAME, false);
  }

  @Override
  public Optional<BusinessValueOverviewDto> getByKey(
      final String tenantId, final String processDefinitionKey, final MetricRange metricRange) {
    final String documentId =
        BusinessValueOverviewRepository.documentId(tenantId, processDefinitionKey, metricRange);
    try {
      final var response =
          esClient.get(
              OptimizeGetRequestBuilderES.of(
                  g ->
                      g.optimizeIndex(esClient, BUSINESS_VALUE_OVERVIEW_INDEX_NAME).id(documentId)),
              BusinessValueOverviewDto.class);
      return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
    } catch (final IOException e) {
      final String errorMessage =
          String.format("Could not read business-value overview row for id [%s].", documentId);
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public List<BusinessValueOverviewDto> readByRange(
      final MetricRange metricRange, final Collection<String> tenantIds) {
    if (metricRange == null) {
      throw new IllegalArgumentException("metricRange must not be null");
    }
    if (tenantIds != null && tenantIds.isEmpty()) {
      return List.of();
    }
    final List<FieldValue> tenantFieldValues =
        tenantIds == null ? null : tenantIds.stream().map(FieldValue::of).toList();
    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, BUSINESS_VALUE_OVERVIEW_INDEX_NAME)
                    .query(
                        q ->
                            q.bool(
                                bool -> {
                                  bool.must(
                                      m ->
                                          m.term(
                                              t ->
                                                  t.field(BusinessValueOverviewIndex.METRIC_RANGE)
                                                      .value(metricRange.getId())));
                                  if (tenantFieldValues != null) {
                                    bool.must(
                                        m ->
                                            m.terms(
                                                t ->
                                                    t.field(BusinessValueOverviewIndex.TENANT_ID)
                                                        .terms(tt -> tt.value(tenantFieldValues))));
                                  }
                                  return bool;
                                }))
                    .size(LIST_FETCH_LIMIT));
    final SearchResponse<BusinessValueOverviewDto> response;
    try {
      response = esClient.search(searchRequest, BusinessValueOverviewDto.class);
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Could not read business-value overview rows for range [%s].", metricRange.getId());
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    final List<BusinessValueOverviewDto> rows =
        ElasticsearchReaderUtil.mapHits(
            response.hits(), BusinessValueOverviewDto.class, objectMapper);
    if (rows.size() >= LIST_FETCH_LIMIT) {
      throw new OptimizeRuntimeException(
          String.format(
              "business-value overview readByRange returned %d rows for range [%s], "
                  + "hitting the LIST_FETCH_LIMIT cap. Pagination must be introduced before "
                  + "the read path can rely on complete results.",
              rows.size(), metricRange.getId()));
    }
    return rows;
  }
}
