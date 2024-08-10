/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.importing.event;
//
// import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
// import io.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
// import io.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
// import io.camunda.optimize.service.db.DatabaseConstants;
// import io.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex;
// import io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex;
// import java.time.OffsetDateTime;
// import java.util.List;
// import java.util.Locale;
// import org.junit.jupiter.api.BeforeEach;
//
// public abstract class AbstractEventTraceStateImportIT extends AbstractPlatformIT {
//
//   @BeforeEach
//   public void init() {
//     embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
//   }
//
//   protected <T> List<T> getAllStoredDocumentsForIndexAsClass(
//       final String indexName, final Class<T> dtoClass) {
//     return databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(indexName, dtoClass);
//   }
//
//   protected List<EventDto> getAllStoredExternalEvents() {
//     return databaseIntegrationTestExtension.getAllStoredExternalEvents();
//   }
//
//   protected List<EventTraceStateDto> getAllStoredExternalEventTraceStates() {
//     return getAllStoredDocumentsForIndexAsClass(
//         EventTraceStateIndex.constructIndexName(EXTERNAL_EVENTS_INDEX_SUFFIX),
//         EventTraceStateDto.class);
//   }
//
//   protected List<EventSequenceCountDto> getAllStoredExternalEventSequenceCounts() {
//     return getAllStoredDocumentsForIndexAsClass(
//         EventSequenceCountIndex.constructIndexName(EXTERNAL_EVENTS_INDEX_SUFFIX),
//         EventSequenceCountDto.class);
//   }
//
//   protected void processEventCountAndTraces() {
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//     embeddedOptimizeExtension.processEvents();
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//   }
//
//   protected Long getLastProcessedEntityTimestampFromElasticsearch(String definitionKey) {
//     final OffsetDateTime lastImportTimestampOfTimestampBasedImportIndex =
//         databaseIntegrationTestExtension.getLastImportTimestampOfTimestampBasedImportIndex(
//             // lowercase as the index names are automatically lowercased and thus the entry
// contains
//             // has a lowercase suffix
//             DatabaseConstants.EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX
//                 + definitionKey.toLowerCase(Locale.ENGLISH),
//             DatabaseConstants.ENGINE_ALIAS_OPTIMIZE);
//     return lastImportTimestampOfTimestampBasedImportIndex.toInstant().toEpochMilli();
//   }
// }
