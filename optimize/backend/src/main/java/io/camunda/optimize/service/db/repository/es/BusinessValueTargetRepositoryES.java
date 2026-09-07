/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.BUSINESS_VALUE_TARGET_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.repository.BusinessValueTargetRepository;
import io.camunda.optimize.service.db.schema.index.BusinessValueTargetIndex;
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
public class BusinessValueTargetRepositoryES implements BusinessValueTargetRepository {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BusinessValueTargetRepositoryES.class);

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public BusinessValueTargetRepositoryES(
      final OptimizeElasticsearchClient esClient, final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public void upsert(final BusinessValueTargetDto target) {
    final String documentId =
        BusinessValueTargetRepository.documentId(
            target.getTenantId(), target.getProcessDefinitionKey());
    try {
      final IndexResponse response =
          esClient.index(
              OptimizeIndexRequestBuilderES.of(
                  b ->
                      b.optimizeIndex(esClient, BUSINESS_VALUE_TARGET_INDEX_NAME)
                          .id(documentId)
                          .document(target)
                          .refresh(Refresh.True)));
      if (response.result() != Result.Created && response.result() != Result.Updated) {
        final String message =
            String.format(
                "Could not upsert business-value target for id [%s]. Result: [%s].",
                documentId, response.result());
        LOG.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (final IOException e) {
      final String errorMessage =
          String.format("Could not upsert business-value target for id [%s].", documentId);
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public Optional<BusinessValueTargetDto> getByKey(
      final String tenantId, final String processDefinitionKey) {
    final String documentId =
        BusinessValueTargetRepository.documentId(tenantId, processDefinitionKey);
    try {
      final var response =
          esClient.get(
              OptimizeGetRequestBuilderES.of(
                  g -> g.optimizeIndex(esClient, BUSINESS_VALUE_TARGET_INDEX_NAME).id(documentId)),
              BusinessValueTargetDto.class);
      return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
    } catch (final IOException e) {
      final String errorMessage =
          String.format("Could not read business-value target for id [%s].", documentId);
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public List<BusinessValueTargetDto> readByTenants(final Collection<String> tenantIds) {
    if (tenantIds != null && tenantIds.isEmpty()) {
      return List.of();
    }
    final List<FieldValue> tenantFieldValues =
        tenantIds == null ? null : tenantIds.stream().map(FieldValue::of).toList();
    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, BUSINESS_VALUE_TARGET_INDEX_NAME)
                    .query(
                        q ->
                            tenantFieldValues == null
                                ? q.matchAll(m -> m)
                                : q.terms(
                                    t ->
                                        t.field(BusinessValueTargetIndex.TENANT_ID)
                                            .terms(tt -> tt.value(tenantFieldValues))))
                    .size(LIST_FETCH_LIMIT));
    final SearchResponse<BusinessValueTargetDto> response;
    try {
      response = esClient.search(searchRequest, BusinessValueTargetDto.class);
    } catch (final IOException e) {
      final String errorMessage = "Could not read business-value targets for the given tenants.";
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    final List<BusinessValueTargetDto> targets =
        ElasticsearchReaderUtil.mapHits(
            response.hits(), BusinessValueTargetDto.class, objectMapper);
    if (targets.size() >= LIST_FETCH_LIMIT) {
      throw new OptimizeRuntimeException(
          String.format(
              "business-value target readByTenants returned %d rows, hitting the "
                  + "LIST_FETCH_LIMIT cap. Pagination must be introduced before the read path can "
                  + "rely on complete results.",
              targets.size()));
    }
    return targets;
  }

  @Override
  public List<BusinessValueTargetDto> scanAll() {
    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, BUSINESS_VALUE_TARGET_INDEX_NAME)
                    .query(q -> q.matchAll(m -> m))
                    .size(LIST_FETCH_LIMIT));
    final SearchResponse<BusinessValueTargetDto> response;
    try {
      response = esClient.search(searchRequest, BusinessValueTargetDto.class);
    } catch (final IOException e) {
      final String errorMessage = "Could not scan business-value targets.";
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    return ElasticsearchReaderUtil.mapHits(
        response.hits(), BusinessValueTargetDto.class, objectMapper);
  }
}
