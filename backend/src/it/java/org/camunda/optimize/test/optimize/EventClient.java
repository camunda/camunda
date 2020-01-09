/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventTraceStateDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.elasticsearch.action.search.SearchResponse;

import java.util.List;
import java.util.Random;

import static org.camunda.optimize.service.es.reader.ElasticsearchHelper.mapHits;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_TRACE_STATE_INDEX_NAME;

@AllArgsConstructor
public class EventClient {

  private static final Random RANDOM = new Random();

  private final EmbeddedOptimizeExtension embeddedOptimizeExtension;
  private final ElasticSearchIntegrationTestExtension elasticSearchIntegrationTestExtension;

  public void ingestEventBatch(final List<EventDto> eventDtos) {
    embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestEventBatch(
        eventDtos,
        embeddedOptimizeExtension.getConfigurationService().getEventIngestionConfiguration().getApiSecret()
      )
      .execute();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  public void processEventTracesAndStates() {
    embeddedOptimizeExtension.getEventStateProcessingService().processUncountedEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  public List<EventTraceStateDto> getAllStoredEventTraceStates() {
    return getAllStoredDocumentsForIndexAsClass(EVENT_TRACE_STATE_INDEX_NAME, EventTraceStateDto.class);
  }

  public List<EventSequenceCountDto> getAllStoredEventSequenceCounts() {
    return getAllStoredDocumentsForIndexAsClass(EVENT_SEQUENCE_COUNT_INDEX_NAME, EventSequenceCountDto.class);
  }

  public List<EventDto> getAllStoredEvents() {
    return getAllStoredDocumentsForIndexAsClass(EVENT_INDEX_NAME, EventDto.class);
  }

  private <T> List<T> getAllStoredDocumentsForIndexAsClass(String indexName, Class<T> dtoClass) {
    SearchResponse response = elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(indexName);
    return mapHits(response.getHits(), dtoClass, embeddedOptimizeExtension.getObjectMapper());
  }

  public EventDto createEventDto() {
    return EventDto.builder()
      .id(IdGenerator.getNextId())
      .traceId(RandomStringUtils.randomAlphabetic(10))
      .timestamp(System.currentTimeMillis())
      .group(RandomStringUtils.randomAlphabetic(10))
      .source(RandomStringUtils.randomAlphabetic(10))
      .eventName(RandomStringUtils.randomAlphabetic(10))
      .data(
        ImmutableMap.of(
          RandomStringUtils.randomAlphabetic(5), RANDOM.nextInt(),
          RandomStringUtils.randomAlphabetic(5), RANDOM.nextBoolean(),
          RandomStringUtils.randomAlphabetic(5), RandomStringUtils.randomAlphabetic(5)
        )
      ).build();
  }

}
