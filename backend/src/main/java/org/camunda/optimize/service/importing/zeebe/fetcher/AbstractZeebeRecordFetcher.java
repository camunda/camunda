/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.fetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.page.PositionBasedImportPage;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.INDEX_NOT_FOUND_EXCEPTION_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Slf4j
@Data
public abstract class AbstractZeebeRecordFetcher<T extends ZeebeRecordDto> {

  public static final int MAX_SUCCESSFUL_FETCHES_TRACKED = 10;

  protected final int partitionId;

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  private int dynamicBatchSize;
  private int consecutiveSuccessfulFetches;
  private Deque<Integer> batchSizeDeque;

  protected AbstractZeebeRecordFetcher(final int partitionId,
                                       final OptimizeElasticsearchClient esClient,
                                       final ObjectMapper objectMapper,
                                       final ConfigurationService configurationService) {
    this.partitionId = partitionId;
    this.esClient = esClient;
    this.objectMapper = objectMapper;
    this.configurationService = configurationService;
    initializeDynamicBatchSizing(configurationService);
  }

  protected abstract String getBaseIndexName();

  protected abstract Class<T> getRecordDtoClass();

  public List<T> getZeebeRecordsForPrefixAndPartitionFrom(PositionBasedImportPage positionBasedImportPage) {
    SearchSourceBuilder searchSourceBuilder =
      new SearchSourceBuilder()
        .query(getRecordQuery(positionBasedImportPage))
        .size(dynamicBatchSize)
        .sort(getSortField(positionBasedImportPage), SortOrder.ASC);
    final SearchRequest searchRequest = new SearchRequest(getIndexAlias())
      .source(searchSourceBuilder)
      .routing(String.valueOf(partitionId))
      .requestCache(false);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.searchWithoutPrefixing(searchRequest);
      if (searchResponse.getFailedShards() > 0
        || (searchResponse.getTotalShards() > (searchResponse.getFailedShards() + searchResponse.getSuccessfulShards()))) {
        throw new OptimizeRuntimeException("Not all shards could be searched successfully");
      }
    } catch (IOException | ElasticsearchStatusException e) {
      final String errorMessage =
        String.format("Was not able to retrieve zeebe records of type: %s", getBaseIndexName());
      if (isZeebeInstanceIndexNotFoundException(e)) {
        log.warn("No Zeebe index of type {} found to read records from!", getIndexAlias());
        return Collections.emptyList();
      } else {
        if (e instanceof IOException) {
          dynamicallyReduceBatchSizeForNextAttempt();
        }
        log.error(errorMessage, e);
        throw new OptimizeRuntimeException(errorMessage, e);
      }
    }
    final List<T> results = ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), getRecordDtoClass(), objectMapper);
    markFetchAsSuccessful();
    return results;
  }

  private void dynamicallyReduceBatchSizeForNextAttempt() {
    if (dynamicBatchSize > 1) {
      final int newBatchSize = dynamicBatchSize / 2;
      // We cache the attempted batch sizes for reuse when we dynamically increase the size again
      if (!batchSizeDeque.contains(newBatchSize)) {
        batchSizeDeque.push(newBatchSize);
      }
      dynamicBatchSize = newBatchSize;
      log.info(
        "Dynamically reducing import page size to {} for next fetch attempt for type {} from partition {}",
        dynamicBatchSize,
        getBaseIndexName(),
        partitionId
      );
    }
  }

  private void markFetchAsSuccessful() {
    final int configuredDefaultBatchSize = configurationService.getConfiguredZeebe().getMaxImportPageSize();
    // When the batch size has been reduced, we keep track of successful fetches up to a maximum number of times
    if (dynamicBatchSize != configuredDefaultBatchSize && consecutiveSuccessfulFetches < MAX_SUCCESSFUL_FETCHES_TRACKED) {
      consecutiveSuccessfulFetches++;
      // When we have reached the max number of consecutive successful fetches, we assume it is safe to start increasing the
      // batch size again
      if (consecutiveSuccessfulFetches >= MAX_SUCCESSFUL_FETCHES_TRACKED) {
        if (!batchSizeDeque.isEmpty()) {
          dynamicBatchSize = batchSizeDeque.pop();
        } else {
          log.debug("Dynamic resizing complete, can now revert batch size back to default of {}", configuredDefaultBatchSize);
          dynamicBatchSize = configuredDefaultBatchSize;
        }
        log.info(
          "Reverting batch size back to {} for fetching of {} records from partition {}",
          dynamicBatchSize,
          getBaseIndexName(),
          partitionId
        );
        consecutiveSuccessfulFetches = 0;
      }
    }
  }

  private BoolQueryBuilder getRecordQuery(final PositionBasedImportPage positionBasedImportPage) {
    return positionBasedImportPage.isHasSeenSequenceField()
      ? boolQuery()
      .must(rangeQuery(ZeebeRecordDto.Fields.sequence)
              .gt(positionBasedImportPage.getSequence())
              .lte(positionBasedImportPage.getSequence() + dynamicBatchSize))
      : boolQuery()
      .must(termQuery(ZeebeRecordDto.Fields.partitionId, partitionId))
      .must(rangeQuery(ZeebeRecordDto.Fields.position).gt(positionBasedImportPage.getPosition()));
  }

  private String getSortField(final PositionBasedImportPage positionBasedImportPage) {
    return positionBasedImportPage.isHasSeenSequenceField() ? ZeebeRecordDto.Fields.sequence : ZeebeRecordDto.Fields.position;
  }

  private String getIndexAlias() {
    return configurationService.getConfiguredZeebe().getName() + "-" + getBaseIndexName();
  }

  private boolean isZeebeInstanceIndexNotFoundException(final Exception e) {
    if (e instanceof ElasticsearchStatusException) {
      return Arrays.stream(e.getSuppressed())
        .map(Throwable::getMessage)
        .anyMatch(msg -> msg.contains(INDEX_NOT_FOUND_EXCEPTION_TYPE));
    }
    return false;
  }

  private void initializeDynamicBatchSizing(final ConfigurationService configurationService) {
    this.dynamicBatchSize = configurationService.getConfiguredZeebe().getMaxImportPageSize();
    this.consecutiveSuccessfulFetches = 0;
    this.batchSizeDeque = new ArrayDeque<>();
  }

}
