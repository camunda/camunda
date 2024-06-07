/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import static org.camunda.optimize.service.db.DatabaseConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import org.camunda.optimize.service.db.reader.CollectionReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class CollectionReaderOS implements CollectionReader {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  @Override
  public Optional<CollectionDefinitionDto> getCollection(String collectionId) {
    log.debug("Fetching collection with id [{}]", collectionId);
    GetRequest.Builder getRequest =
        new GetRequest.Builder().index(COLLECTION_INDEX_NAME).id(collectionId);
    final String errorMessage =
        String.format("Could not fetch collection with id [%s]", collectionId);
    GetResponse<CollectionDefinitionDto> getResponse =
        osClient.get(getRequest, CollectionDefinitionDto.class, errorMessage);

    if (getResponse.found()) {
      if (Objects.isNull(getResponse.source())) {
        String reason =
            "Could not deserialize collection information for collection " + collectionId;
        log.error(
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
    log.debug("Fetching all available collections");

    SearchRequest.Builder searchRequest =
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

    OpenSearchDocumentOperations.AggregatedResult<Hit<CollectionDefinitionDto>> scrollResp;
    try {
      scrollResp = osClient.retrieveAllScrollResults(searchRequest, CollectionDefinitionDto.class);
    } catch (IOException e) {
      final String errorMessage = "Was not able to retrieve collections!";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp);
  }
}
