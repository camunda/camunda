/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.fetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.INDEX_NOT_FOUND_EXCEPTION_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractZeebeRecordFetcher<T extends ZeebeRecordDto> {

  @Getter
  protected final int partitionId;

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;
  private boolean hasSeenSequenceField;

  protected abstract String getBaseIndexName();

  protected abstract Class<T> getRecordDtoClass();

  public List<T> getZeebeRecordsForPrefixAndPartitionFrom(PositionBasedImportPage positionBasedImportPage) {
    setHaveSeenSequenceField(positionBasedImportPage);
    SearchSourceBuilder searchSourceBuilder =
      new SearchSourceBuilder()
        .query(getRecordQuery(positionBasedImportPage))
        .size(configurationService.getConfiguredZeebe().getMaxImportPageSize())
        .sort(getSortField(), SortOrder.ASC);
    final SearchRequest searchRequest = new SearchRequest(getIndexAlias())
      .source(searchSourceBuilder)
      .routing(String.valueOf(partitionId))
      .requestCache(false);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.searchWithoutPrefixing(searchRequest);
    } catch (IOException | ElasticsearchStatusException e) {
      final String errorMessage =
        String.format("Was not able to retrieve zeebe records of type: %s", getBaseIndexName());
      if (isZeebeInstanceIndexNotFoundException(e)) {
        log.warn("No Zeebe index of type {} found to read records from!", getIndexAlias());
        return Collections.emptyList();
      } else {
        log.error(errorMessage, e);
        throw new OptimizeRuntimeException(errorMessage, e);
      }
    }
    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), getRecordDtoClass(), objectMapper);
  }

  private BoolQueryBuilder getRecordQuery(final PositionBasedImportPage positionBasedImportPage) {
    return hasSeenSequenceField
      ? boolQuery()
      .must(rangeQuery(ZeebeRecordDto.Fields.sequence)
              .gt(positionBasedImportPage.getSequence())
              .lte(positionBasedImportPage.getSequence() + configurationService.getConfiguredZeebe().getMaxImportPageSize()))
      : boolQuery()
      .must(termQuery(ZeebeRecordDto.Fields.partitionId, partitionId))
      .must(rangeQuery(ZeebeRecordDto.Fields.position).gt(positionBasedImportPage.getPosition()));
  }

  private String getSortField() {
    return hasSeenSequenceField ? ZeebeRecordDto.Fields.sequence : ZeebeRecordDto.Fields.position;
  }

  private void setHaveSeenSequenceField(final PositionBasedImportPage positionBasedImportPage) {
    if (!hasSeenSequenceField && positionBasedImportPage.getSequence() != 0L) {
      log.info("First Zeebe record with sequence field has been imported. Zeebe records will now be fetched based on sequence.");
      hasSeenSequenceField = true;
    }
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

}
