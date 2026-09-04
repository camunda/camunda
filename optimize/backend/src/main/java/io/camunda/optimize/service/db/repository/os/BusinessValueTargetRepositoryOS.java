/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.DatabaseConstants.BUSINESS_VALUE_TARGET_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.stringTerms;
import static java.lang.String.format;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.BusinessValueTargetRepository;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.index.BusinessValueTargetIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class BusinessValueTargetRepositoryOS implements BusinessValueTargetRepository {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BusinessValueTargetRepositoryOS.class);

  private final OptimizeOpenSearchClient osClient;
  private final OptimizeIndexNameService indexNameService;

  public BusinessValueTargetRepositoryOS(
      final OptimizeOpenSearchClient osClient, final OptimizeIndexNameService indexNameService) {
    this.osClient = osClient;
    this.indexNameService = indexNameService;
  }

  @Override
  public void upsert(final BusinessValueTargetDto target) {
    final String documentId =
        BusinessValueTargetRepository.documentId(
            target.getTenantId(), target.getProcessDefinitionKey());

    final IndexRequest.Builder<BusinessValueTargetDto> request =
        new IndexRequest.Builder<BusinessValueTargetDto>()
            .index(indexNameService.getOptimizeIndexAliasForIndex(BUSINESS_VALUE_TARGET_INDEX_NAME))
            .id(documentId)
            .document(target)
            .refresh(Refresh.True);

    final IndexResponse response = osClient.index(request);
    if (response.result() != Result.Created && response.result() != Result.Updated) {
      final String message =
          format(
              "Could not upsert business-value target for id [%s]. Result: [%s].",
              documentId, response.result());
      LOG.error(message);
      throw new OptimizeRuntimeException(message);
    }
  }

  @Override
  public Optional<BusinessValueTargetDto> getByKey(
      final String tenantId, final String processDefinitionKey) {
    final String documentId =
        BusinessValueTargetRepository.documentId(tenantId, processDefinitionKey);
    final GetRequest.Builder requestBuilder =
        new GetRequest.Builder()
            .index(indexNameService.getOptimizeIndexAliasForIndex(BUSINESS_VALUE_TARGET_INDEX_NAME))
            .id(documentId);

    final String errorMessage =
        format("Could not read business-value target for id [%s].", documentId);
    final GetResponse<BusinessValueTargetDto> response =
        osClient.get(requestBuilder, BusinessValueTargetDto.class, errorMessage);
    return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
  }

  @Override
  public List<BusinessValueTargetDto> readByTenants(final Collection<String> tenantIds) {
    if (tenantIds != null && tenantIds.isEmpty()) {
      return List.of();
    }
    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(indexNameService.getOptimizeIndexAliasForIndex(BUSINESS_VALUE_TARGET_INDEX_NAME))
            .query(
                tenantIds == null
                    ? matchAll()
                    : stringTerms(BusinessValueTargetIndex.TENANT_ID, tenantIds))
            .size(LIST_FETCH_LIMIT);
    final List<BusinessValueTargetDto> targets =
        osClient.searchValues(requestBuilder, BusinessValueTargetDto.class);
    if (targets.size() >= LIST_FETCH_LIMIT) {
      throw new OptimizeRuntimeException(
          format(
              "business-value target readByTenants returned %d rows, hitting the "
                  + "LIST_FETCH_LIMIT cap. Pagination must be introduced before the read path can "
                  + "rely on complete results.",
              targets.size()));
    }
    return targets;
  }

  @Override
  public List<BusinessValueTargetDto> scanAll() {
    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(indexNameService.getOptimizeIndexAliasForIndex(BUSINESS_VALUE_TARGET_INDEX_NAME))
            .query(matchAll())
            .size(LIST_FETCH_LIMIT);

    return osClient.searchValues(requestBuilder, BusinessValueTargetDto.class);
  }
}
