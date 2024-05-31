/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import static org.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex.EVENT_TRACE;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.writer.EventTraceStateWriter;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;

@AllArgsConstructor
@Slf4j
public class EventTraceStateWriterOS implements EventTraceStateWriter {

  private final String indexKey;
  private final OptimizeOpenSearchClient osClient;
  private final ObjectMapper objectMapper;

  @Override
  public void upsertEventTraceStates(final List<EventTraceStateDto> eventTraceStateDtos) {
    log.debug("Writing [{}] event trace states to OpenSearch", eventTraceStateDtos.size());

    final List<BulkOperation> bulkOperations =
        eventTraceStateDtos.stream()
            .map(
                eventTraceStateDto ->
                    new BulkOperation.Builder()
                        .update(createEventSequenceCountUpsertRequest(eventTraceStateDto))
                        .build())
            .toList();

    if (!bulkOperations.isEmpty()) {
      osClient.doBulkRequest(
          BulkRequest.Builder::new, bulkOperations, getIndexName(indexKey), false);
    }
  }

  private UpdateOperation<EventTraceStateDto> createEventSequenceCountUpsertRequest(
      final EventTraceStateDto eventTraceStateDto) {
    return new UpdateOperation.Builder<EventTraceStateDto>()
        .index(getPrefixedIndexName(indexKey))
        .id(eventTraceStateDto.getTraceId())
        .upsert(eventTraceStateDto)
        .script(createUpdateScript(eventTraceStateDto))
        .scriptedUpsert(true)
        .build();
  }

  private Script createUpdateScript(final EventTraceStateDto eventTraceStateDto) {
    final Map<String, Object> params = new HashMap<>();
    params.put(EVENT_TRACE, eventTraceStateDto.getEventTrace());
    return QueryDSL.script(updateScript(), params);
  }

  private String getPrefixedIndexName(String indexKey) {
    return osClient.getIndexNameService().getOptimizeIndexAliasForIndex(getIndexName(indexKey));
  }
}
