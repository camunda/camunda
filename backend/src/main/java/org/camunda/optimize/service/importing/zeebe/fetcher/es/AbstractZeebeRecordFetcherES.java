/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.fetcher.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.zeebe.db.AbstractZeebeRecordFetcher;
import org.camunda.optimize.service.importing.page.PositionBasedImportPage;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ZeebeImportConfiguration;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.context.annotation.Conditional;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static org.camunda.optimize.service.db.DatabaseConstants.INDEX_NOT_FOUND_EXCEPTION_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Slf4j
@Data
@Conditional(ElasticSearchCondition.class)
public abstract class AbstractZeebeRecordFetcherES<T extends ZeebeRecordDto> implements AbstractZeebeRecordFetcher<T> {

  protected final int partitionId;

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  private int dynamicBatchSize;
  private int consecutiveSuccessfulFetches;
  private int consecutiveEmptyPages;
  private Deque<Integer> batchSizeDeque;

  protected AbstractZeebeRecordFetcherES(final int partitionId,
                                         final OptimizeElasticsearchClient esClient,
                                         final ObjectMapper objectMapper,
                                         final ConfigurationService configurationService) {
    this.partitionId = partitionId;
    this.esClient = esClient;
    this.objectMapper = objectMapper;
    this.configurationService = configurationService;
    initializeDynamicBatchSizing(configurationService);
    initializeDynamicFetching();
  }

  protected abstract String getBaseIndexName();

  protected abstract Class<T> getRecordDtoClass();

  @Override
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
        String.format("Was not able to retrieve zeebe records of type %s from partition %s", getBaseIndexName(), partitionId);
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
    markFetchAsSuccessfulAndAdjustBatchSize();
    trackConsecutiveEmptyPages(results);
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

  private void markFetchAsSuccessfulAndAdjustBatchSize() {
    final int configuredDefaultBatchSize = configurationService.getConfiguredZeebe().getMaxImportPageSize();
    // When the batch size has been reduced, we keep track of successful fetches up to a maximum number of times
    if (dynamicBatchSize != configuredDefaultBatchSize
      && consecutiveSuccessfulFetches < getZeebeImportConfig().getDynamicBatchSuccessAttempts()) {
      consecutiveSuccessfulFetches++;
      // When we have reached the max number of consecutive successful fetches, we assume it is safe to start increasing the
      // batch size again
      if (consecutiveSuccessfulFetches >= getZeebeImportConfig().getDynamicBatchSuccessAttempts()) {
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

  private void trackConsecutiveEmptyPages(final List<T> results) {
    if (results.isEmpty()) {
      if (consecutiveEmptyPages < getZeebeImportConfig().getMaxEmptyPagesToImport()) {
        consecutiveEmptyPages++;
      } else {
        // If the max number of empty pages to track has been reached, it gets reset as the dynamic querying would have taken
        // place
        consecutiveEmptyPages = 0;
      }
    } else {
      consecutiveEmptyPages = 0;
    }
  }

  private BoolQueryBuilder getRecordQuery(final PositionBasedImportPage positionBasedImportPage) {
    // We use the position query if no record with sequences have been imported yet, or if we know that there is data to be
    // imported that Optimize is not catching in its sequence query. This can happen in the event that the next page of
    // records no longer exist and the next record to import will have a sequence greater than the max range of the sequence query
    if (!positionBasedImportPage.isHasSeenSequenceField() || nextSequenceRecordIsBeyondSequenceQuery(positionBasedImportPage)) {
      return buildPositionQuery(positionBasedImportPage);
    } else {
      return buildSequenceQuery(positionBasedImportPage);
    }
  }

  private boolean nextSequenceRecordIsBeyondSequenceQuery(final PositionBasedImportPage positionBasedImportPage) {
    // We only check for new data beyond the upper sequence range if the max configured number of empty pages has been reached
    if (consecutiveEmptyPages < getZeebeImportConfig().getMaxEmptyPagesToImport()) {
      return false;
    }
    final CountRequest countRequest = new CountRequest(getIndexAlias())
      .query(buildPositionQuery(positionBasedImportPage))
      .routing(String.valueOf(partitionId));
    try {
      log.info("Using the position query to see if there are new records in the {} index on partition {}",
               getBaseIndexName(), partitionId
      );
      final long numberOfRecordsFound = esClient.countWithoutPrefix(countRequest);
      if (numberOfRecordsFound > 0) {
        log.info(
          "Found {} records in index {} on partition {} that can't be imported by the current sequence query. Will revert to " +
            "position query for the next fetch attempt",
          numberOfRecordsFound,
          getBaseIndexName(),
          partitionId
        );
        return true;
      } else {
        log.info("There are no newer records to process, so empty pages of records are currently expected");
      }
    } catch (Exception e) {
      if (isZeebeInstanceIndexNotFoundException(e)) {
        log.warn("No Zeebe index of type {} found to count records from!", getIndexAlias());
      } else {
        log.warn("There was an error when looking for records to import beyond the boundaries of the sequence request" + e);
      }
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
    return false;
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

  private BoolQueryBuilder buildPositionQuery(final PositionBasedImportPage positionBasedImportPage) {
    log.trace("using position query for records of {} on partition {}", getBaseIndexName(), getPartitionId());
    return boolQuery()
      .must(termQuery(ZeebeRecordDto.Fields.partitionId, partitionId))
      .must(rangeQuery(ZeebeRecordDto.Fields.position).gt(positionBasedImportPage.getPosition()));
  }

  private BoolQueryBuilder buildSequenceQuery(final PositionBasedImportPage positionBasedImportPage) {
    log.trace("using sequence query for records of {} on partition {}", getBaseIndexName(), getPartitionId());
    return boolQuery()
      .must(rangeQuery(ZeebeRecordDto.Fields.sequence)
              .gt(positionBasedImportPage.getSequence())
              .lte(positionBasedImportPage.getSequence() + dynamicBatchSize));
  }

  private ZeebeImportConfiguration getZeebeImportConfig() {
    return configurationService.getConfiguredZeebe().getImportConfig();
  }

  private void initializeDynamicFetching() {
    // Dynamic fetching describes the mechanism where Optimize will dynamically choose to fetch data based on the sequence or
    // the position of the records. By default, Optimize will use the sequence field when it knows that this field exists on the
    // records. In some cases, the sequence query could not find the next page. In this scenario, Optimize will use the position
    // query to get the next page
    this.consecutiveEmptyPages = 0;
  }

  private void initializeDynamicBatchSizing(final ConfigurationService configurationService) {
    // Dynamic batch sizing describes the mechanism where Optimize will reduce its batch size in order to accommodate situations
    // where larger batches aren't possible. This could be when the payload is too large, for example. Based on configured values,
    // Optimize will always aim to get back to the max configured batch size
    this.dynamicBatchSize = configurationService.getConfiguredZeebe().getMaxImportPageSize();
    this.consecutiveSuccessfulFetches = 0;
    this.batchSizeDeque = new ArrayDeque<>();
  }

}
