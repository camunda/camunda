/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.COLLECTION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;

import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.client.dsl.RequestDSL;
import io.camunda.optimize.service.db.os.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.db.reader.CollectionReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class CollectionReaderOS implements CollectionReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(CollectionReaderOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  public CollectionReaderOS(
      final OptimizeOpenSearchClient osClient, final ConfigurationService configurationService) {
    this.osClient = osClient;
    this.configurationService = configurationService;
  }

  @Override
  public Optional<CollectionDefinitionDto> getCollection(final String collectionId) {
    LOG.debug("Fetching collection with id [{}]", collectionId);
    final GetRequest.Builder getRequest =
        new GetRequest.Builder().index(COLLECTION_INDEX_NAME).id(collectionId);
    final String errorMessage =
        String.format("Could not fetch collection with id [%s]", collectionId);
    final GetResponse<CollectionDefinitionDto> getResponse =
        osClient.get(getRequest, CollectionDefinitionDto.class, errorMessage);

    if (getResponse.found()) {
      if (Objects.isNull(getResponse.source())) {
        final String reason =
            "Could not deserialize collection information for collection " + collectionId;
        LOG.error(
            "Was not able to retrieve collection with id [{}] from OpenSearch. Reason: {}",
            collectionId,
            reason);
        throw new OptimizeRuntimeException(reason);
      } else {
        return Optional.of(getResponse.source());
      }
    }
    return Optional.empty();
  }

  @Override
  public List<CollectionDefinitionDto> getAllCollections() {
    LOG.debug("Fetching all available collections");

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(COLLECTION_INDEX_NAME)
            .query(QueryDSL.matchAll())
            .sort(
                new SortOptions.Builder()
                    .field(
                        new FieldSort.Builder()
                            .order(SortOrder.Asc)
                            .field(CollectionDefinitionDto.Fields.name.name())
                            .build())
                    .build())
            .size(LIST_FETCH_LIMIT)
            .scroll(
                RequestDSL.time(
                    String.valueOf(
                        configurationService
                            .getOpenSearchConfiguration()
                            .getScrollTimeoutInSeconds())));

    final OpenSearchDocumentOperations.AggregatedResult<Hit<CollectionDefinitionDto>> scrollResp;
    try {
      scrollResp = osClient.retrieveAllScrollResults(searchRequest, CollectionDefinitionDto.class);
    } catch (final IOException e) {
      final String errorMessage = "Was not able to retrieve collections!";
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp);
  }
}
