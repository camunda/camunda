/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.writer.EventSequenceCountWriter;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;

@Conditional(OpenSearchCondition.class)
public class EventSequenceCountWriterOS implements EventSequenceCountWriter {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(EventSequenceCountWriterOS.class);
  private final String indexKey;
  private final OptimizeOpenSearchClient osClient;
  private final ObjectMapper objectMapper;

  public EventSequenceCountWriterOS(
      final String indexKey,
      final OptimizeOpenSearchClient osClient,
      final ObjectMapper objectMapper) {
    this.indexKey = indexKey;
    this.osClient = osClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public void updateEventSequenceCountsWithAdjustments(
      final List<EventSequenceCountDto> eventSequenceCountDtos) {
    log.debug(
        "Making adjustments to [{}] event sequence counts in opensearch",
        eventSequenceCountDtos.size());
    eventSequenceCountDtos.forEach(EventSequenceCountDto::generateIdForEventSequenceCountDto);

    final List<BulkOperation> bulkOperations =
        eventSequenceCountDtos.stream()
            .map(
                eventSequenceCountDto ->
                    new BulkOperation.Builder()
                        .update(createEventSequenceCountUpsertRequest(eventSequenceCountDto))
                        .build())
            .toList();

    if (!bulkOperations.isEmpty()) {
      osClient.doBulkRequest(
          BulkRequest.Builder::new, bulkOperations, getPrefixedIndexName(indexKey), false);
    }
  }

  private UpdateOperation<EventSequenceCountDto> createEventSequenceCountUpsertRequest(
      final EventSequenceCountDto eventSequenceCountDto) {
    return new UpdateOperation.Builder<EventSequenceCountDto>()
        .index(getPrefixedIndexName(indexKey))
        .id(eventSequenceCountDto.getId())
        .document(eventSequenceCountDto)
        .docAsUpsert(true)
        .build();
  }

  private String getPrefixedIndexName(final String indexKey) {
    return osClient.getIndexNameService().getOptimizeIndexAliasForIndex(getIndexName(indexKey));
  }
}
