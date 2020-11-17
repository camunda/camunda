/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.service.es.EsBulkByScrollTaskActionProgressReporter;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

@AllArgsConstructor
@Slf4j
@Component
public class ExternalEventWriter {
  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter dateTimeFormatter;
  private final ObjectMapper objectMapper;

  public void upsertEvents(final List<EventDto> eventDtos) {
    log.debug("Writing [{}] events to elasticsearch", eventDtos.size());

    final BulkRequest bulkRequest = new BulkRequest();
    final Long ingestionTimestamp = LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli();
    for (EventDto eventDto : eventDtos) {
      eventDto.setIngestionTimestamp(ingestionTimestamp);
      bulkRequest.add(createEventUpsert(eventDto));
    }

    if (bulkRequest.numberOfActions() > 0) {
      try {
        final BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        if (bulkResponse.hasFailures()) {
          final String errorMessage = String.format(
            "There were failures while writing events. Received error message: %s",
            bulkResponse.buildFailureMessage()
          );
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        final String errorMessage = "There were errors while writing events.";
        log.error(errorMessage, e);
        throw new OptimizeRuntimeException(errorMessage, e);
      }
    }
  }

  public void deleteEventsOlderThan(final OffsetDateTime timestamp) {
    final String deletedItemName = "external events";
    final String deletedItemIdentifier = String.format("%s with timestamp older than %s", deletedItemName, timestamp);

    log.info("Deleting {} with timestamp older than {}", deletedItemName, timestamp);
    final EsBulkByScrollTaskActionProgressReporter progressReporter = new EsBulkByScrollTaskActionProgressReporter(
      getClass().getName(), esClient, DeleteByQueryAction.NAME
    );
    try {
      progressReporter.start();
      final BoolQueryBuilder filterQuery = boolQuery()
        .filter(rangeQuery(EventIndex.TIMESTAMP).lt(dateTimeFormatter.format(timestamp)));

      ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        filterQuery,
        deletedItemName,
        deletedItemIdentifier,
        false,
        // use wildcarded index name to catch all indices that exist after potential rollover
        esClient.getIndexNameService()
          .getOptimizeIndexNameWithVersionWithWildcardSuffix(new EventIndex())
      );
    } finally {
      progressReporter.stop();
    }
  }

  private UpdateRequest createEventUpsert(final EventDto eventDto) {
    return new UpdateRequest()
      .index(ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_NAME)
      .id(IdGenerator.getNextId())
      .doc(objectMapper.convertValue(eventDto, Map.class))
      .docAsUpsert(true);
  }
}
