/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.fetcher;

import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.importing.page.PositionBasedImportPage;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ZeebeImportConfiguration;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;

public abstract class AbstractZeebeRecordFetcher<T> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(AbstractZeebeRecordFetcher.class);
  protected final int partitionId;
  protected final ConfigurationService configurationService;
  private int dynamicBatchSize;
  private int consecutiveSuccessfulFetches;
  private int consecutiveEmptyPages;
  private Deque<Integer> batchSizeDeque;

  protected AbstractZeebeRecordFetcher(
      final int partitionId, final ConfigurationService configurationService) {
    this.partitionId = partitionId;
    this.configurationService = configurationService;
    initializeDynamicBatchSizing(configurationService);
    initializeDynamicFetching();
  }

  public List<T> getZeebeRecordsForPrefixAndPartitionFrom(
      final PositionBasedImportPage positionBasedImportPage) {
    final List<T> results;
    try {
      results = fetchZeebeRecordsForPrefixAndPartitionFrom(positionBasedImportPage);
    } catch (final Exception e) {
      if (isZeebeInstanceIndexNotFoundException(e)) {
        log.warn("No Zeebe index with alias {} found to read records from!", getIndexAlias());
        return Collections.emptyList();
      } else {
        if (e instanceof IOException) {
          dynamicallyReduceBatchSizeForNextAttempt();
        }
        final String errorMessage =
            String.format(
                "Was not able to retrieve zeebe records of type %s from partition %s",
                getBaseIndexName(), partitionId);
        log.error(errorMessage, e);
        throw new OptimizeRuntimeException(errorMessage, e);
      }
    }
    markFetchAsSuccessfulAndAdjustBatchSize();
    trackConsecutiveEmptyPages(results);
    return results;
  }

  protected abstract boolean isZeebeInstanceIndexNotFoundException(final Exception e);

  protected abstract List<T> fetchZeebeRecordsForPrefixAndPartitionFrom(
      final PositionBasedImportPage positionBasedImportPage) throws Exception;

  protected abstract String getBaseIndexName();

  protected abstract Class<T> getRecordDtoClass();

  protected String getSortField(final PositionBasedImportPage positionBasedImportPage) {
    return positionBasedImportPage.isHasSeenSequenceField()
        ? ZeebeRecordDto.Fields.sequence
        : ZeebeRecordDto.Fields.position;
  }

  protected String getIndexAlias() {
    return configurationService.getConfiguredZeebe().getName() + "-" + getBaseIndexName();
  }

  protected ZeebeImportConfiguration getZeebeImportConfig() {
    return configurationService.getConfiguredZeebe().getImportConfig();
  }

  private void initializeDynamicFetching() {
    // Dynamic fetching describes the mechanism where Optimize will dynamically choose to fetch data
    // based on the sequence or
    // the position of the records. By default, Optimize will use the sequence field when it knows
    // that this field exists on the
    // records. In some cases, the sequence query could not find the next page. In this scenario,
    // Optimize will use the position
    // query to get the next page
    consecutiveEmptyPages = 0;
  }

  private void initializeDynamicBatchSizing(final ConfigurationService configurationService) {
    // Dynamic batch sizing describes the mechanism where Optimize will reduce its batch size in
    // order to accommodate situations
    // where larger batches aren't possible. This could be when the payload is too large, for
    // example. Based on configured values,
    // Optimize will always aim to get back to the max configured batch size
    dynamicBatchSize = configurationService.getConfiguredZeebe().getMaxImportPageSize();
    consecutiveSuccessfulFetches = 0;
    batchSizeDeque = new ArrayDeque<>();
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
          partitionId);
    }
  }

  private void markFetchAsSuccessfulAndAdjustBatchSize() {
    final int configuredDefaultBatchSize =
        configurationService.getConfiguredZeebe().getMaxImportPageSize();
    // When the batch size has been reduced, we keep track of successful fetches up to a maximum
    // number of times
    if (dynamicBatchSize != configuredDefaultBatchSize
        && consecutiveSuccessfulFetches < getZeebeImportConfig().getDynamicBatchSuccessAttempts()) {
      consecutiveSuccessfulFetches++;
      // When we have reached the max number of consecutive successful fetches, we assume it is safe
      // to start increasing the
      // batch size again
      if (consecutiveSuccessfulFetches >= getZeebeImportConfig().getDynamicBatchSuccessAttempts()) {
        if (!batchSizeDeque.isEmpty()) {
          dynamicBatchSize = batchSizeDeque.pop();
        } else {
          log.debug(
              "Dynamic resizing complete, can now revert batch size back to default of {}",
              configuredDefaultBatchSize);
          dynamicBatchSize = configuredDefaultBatchSize;
        }
        log.info(
            "Reverting batch size back to {} for fetching of {} records from partition {}",
            dynamicBatchSize,
            getBaseIndexName(),
            partitionId);
        consecutiveSuccessfulFetches = 0;
      }
    }
  }

  private void trackConsecutiveEmptyPages(final List<T> results) {
    if (results.isEmpty()) {
      if (consecutiveEmptyPages < getZeebeImportConfig().getMaxEmptyPagesToImport()) {
        consecutiveEmptyPages++;
      } else {
        // If the max number of empty pages to track has been reached, it gets reset as the dynamic
        // querying would have taken
        // place
        consecutiveEmptyPages = 0;
      }
    } else {
      consecutiveEmptyPages = 0;
    }
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  public int getDynamicBatchSize() {
    return dynamicBatchSize;
  }

  public int getConsecutiveSuccessfulFetches() {
    return consecutiveSuccessfulFetches;
  }

  public int getConsecutiveEmptyPages() {
    return consecutiveEmptyPages;
  }

  public Deque<Integer> getBatchSizeDeque() {
    return batchSizeDeque;
  }
}
