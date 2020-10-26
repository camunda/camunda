/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.event.process.EventResponseDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil.mapHits;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

public abstract class AbstractEventTraceStateImportIT extends AbstractIT {

  @BeforeEach
  public void init() {
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
  }

  protected <T> List<T> getAllStoredDocumentsForIndexAsClass(final String indexName, final Class<T> dtoClass) {
    SearchResponse response = elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(indexName);
    return mapHits(response.getHits(), dtoClass, elasticSearchIntegrationTestExtension.getObjectMapper());
  }

  protected List<EventResponseDto> getAllStoredExternalEvents() {
    return elasticSearchIntegrationTestExtension.getAllStoredExternalEvents();
  }

  protected List<EventTraceStateDto> getAllStoredExternalEventTraceStates() {
    return getAllStoredDocumentsForIndexAsClass(
      new EventTraceStateIndex(EXTERNAL_EVENTS_INDEX_SUFFIX).getIndexName(),
      EventTraceStateDto.class
    );
  }

  protected List<EventSequenceCountDto> getAllStoredExternalEventSequenceCounts() {
    return getAllStoredDocumentsForIndexAsClass(
      new EventSequenceCountIndex(EXTERNAL_EVENTS_INDEX_SUFFIX).getIndexName(),
      EventSequenceCountDto.class
    );
  }

  protected void processEventCountAndTraces() {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.processEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  @SneakyThrows
  protected Long getLastProcessedEntityTimestampFromElasticsearch(String definitionKey) {
    return elasticSearchIntegrationTestExtension
      .getLastProcessedEventTimestampForEventIndexSuffix(definitionKey)
      .toInstant()
      .toEpochMilli();
  }

}
