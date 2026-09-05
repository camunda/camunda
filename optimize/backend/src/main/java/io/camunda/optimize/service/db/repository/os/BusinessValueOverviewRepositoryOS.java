/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.DatabaseConstants.BUSINESS_VALUE_OVERVIEW_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.stringTerms;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static java.lang.String.format;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.BusinessValueOverviewRepository;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.index.BusinessValueOverviewIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class BusinessValueOverviewRepositoryOS implements BusinessValueOverviewRepository {

  private final OptimizeOpenSearchClient osClient;
  private final OptimizeIndexNameService indexNameService;

  public BusinessValueOverviewRepositoryOS(
      final OptimizeOpenSearchClient osClient, final OptimizeIndexNameService indexNameService) {
    this.osClient = osClient;
    this.indexNameService = indexNameService;
  }

  @Override
  public void bulkUpsert(
      final List<BusinessValueOverviewDto> rows, final boolean refreshImmediately) {
    if (rows == null || rows.isEmpty()) {
      return;
    }
    final String indexAlias =
        indexNameService.getOptimizeIndexAliasForIndex(BUSINESS_VALUE_OVERVIEW_INDEX_NAME);
    final List<BulkOperation> operations =
        rows.stream()
            .map(
                row ->
                    new BulkOperation.Builder()
                        .index(
                            new IndexOperation.Builder<BusinessValueOverviewDto>()
                                .index(indexAlias)
                                .id(
                                    BusinessValueOverviewRepository.documentId(
                                        row.getTenantId(),
                                        row.getProcessDefinitionKey(),
                                        row.getMetricRange()))
                                .document(row)
                                .build())
                        .build())
            .toList();
    osClient.doBulkRequest(
        refreshImmediately
            ? () -> new BulkRequest.Builder().refresh(Refresh.True)
            : BulkRequest.Builder::new,
        operations,
        BUSINESS_VALUE_OVERVIEW_INDEX_NAME,
        false);
  }

  @Override
  public Optional<BusinessValueOverviewDto> getByKey(
      final String tenantId, final String processDefinitionKey, final MetricRange metricRange) {
    final String documentId =
        BusinessValueOverviewRepository.documentId(tenantId, processDefinitionKey, metricRange);
    final GetRequest.Builder requestBuilder =
        new GetRequest.Builder()
            .index(
                indexNameService.getOptimizeIndexAliasForIndex(BUSINESS_VALUE_OVERVIEW_INDEX_NAME))
            .id(documentId);

    final String errorMessage =
        format("Could not read business-value overview row for id [%s].", documentId);
    final GetResponse<BusinessValueOverviewDto> response =
        osClient.get(requestBuilder, BusinessValueOverviewDto.class, errorMessage);
    return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
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
    final Query rangeTerm = term(BusinessValueOverviewIndex.METRIC_RANGE, metricRange.getId());
    final Query query =
        tenantIds == null
            ? rangeTerm
            : and(rangeTerm, stringTerms(BusinessValueOverviewIndex.TENANT_ID, tenantIds));
    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(
                indexNameService.getOptimizeIndexAliasForIndex(BUSINESS_VALUE_OVERVIEW_INDEX_NAME))
            .query(query)
            .size(LIST_FETCH_LIMIT);
    final List<BusinessValueOverviewDto> rows =
        osClient.searchValues(requestBuilder, BusinessValueOverviewDto.class);
    if (rows.size() >= LIST_FETCH_LIMIT) {
      throw new OptimizeRuntimeException(
          format(
              "business-value overview readByRange returned %d rows for range [%s], "
                  + "hitting the LIST_FETCH_LIMIT cap. Pagination must be introduced before "
                  + "the read path can rely on complete results.",
              rows.size(), metricRange.getId()));
    }
    return rows;
  }
}
