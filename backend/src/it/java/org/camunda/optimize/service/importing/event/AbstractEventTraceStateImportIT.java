/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.jupiter.api.BeforeEach;

import java.time.OffsetDateTime;
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

  protected List<EventDto> getAllStoredExternalEvents() {
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

  protected Long getLastProcessedEntityTimestampFromElasticsearch(String definitionKey) {
    final OffsetDateTime lastImportTimestampOfTimestampBasedImportIndex =
      elasticSearchIntegrationTestExtension.getLastImportTimestampOfTimestampBasedImportIndex(
      // lowercase as the index names are automatically lowercased and thus the entry contains has a lowercase suffix
      ElasticsearchConstants.EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX + definitionKey.toLowerCase(),
      ElasticsearchConstants.ENGINE_ALIAS_OPTIMIZE
    );
    return lastImportTimestampOfTimestampBasedImportIndex.toInstant().toEpochMilli();
  }

}
