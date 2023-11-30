/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event;

import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import org.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.jupiter.api.BeforeEach;

import java.time.OffsetDateTime;
import java.util.List;

import static org.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil.mapHits;
import static org.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

public abstract class AbstractEventTraceStateImportIT extends AbstractPlatformIT {

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
      EventTraceStateIndex.constructIndexName(EXTERNAL_EVENTS_INDEX_SUFFIX),
      EventTraceStateDto.class
    );
  }

  protected List<EventSequenceCountDto> getAllStoredExternalEventSequenceCounts() {
    return getAllStoredDocumentsForIndexAsClass(
      EventSequenceCountIndex.constructIndexName(EXTERNAL_EVENTS_INDEX_SUFFIX),
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
        DatabaseConstants.EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX + definitionKey.toLowerCase(),
        DatabaseConstants.ENGINE_ALIAS_OPTIMIZE
    );
    return lastImportTimestampOfTimestampBasedImportIndex.toInstant().toEpochMilli();
  }

}
