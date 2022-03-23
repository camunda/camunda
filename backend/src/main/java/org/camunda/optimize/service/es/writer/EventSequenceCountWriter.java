/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Slf4j
public class EventSequenceCountWriter {

  private final String indexKey;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void updateEventSequenceCountsWithAdjustments(final List<EventSequenceCountDto> eventSequenceCountDtos) {
    log.debug("Making adjustments to [{}] event sequence counts in elasticsearch", eventSequenceCountDtos.size());
    eventSequenceCountDtos.forEach(EventSequenceCountDto::generateIdForEventSequenceCountDto);

    final BulkRequest bulkRequest = new BulkRequest();
    for (EventSequenceCountDto eventSequenceCountDto : eventSequenceCountDtos) {
      bulkRequest.add(createEventSequenceCountUpsertRequest(eventSequenceCountDto));
    }

    if (bulkRequest.numberOfActions() > 0) {
      try {
        final BulkResponse bulkResponse = esClient.bulk(bulkRequest);
        if (bulkResponse.hasFailures()) {
          final String errorMessage = String.format(
            "There were failures while writing event trace states. Received error message: %s",
            bulkResponse.buildFailureMessage()
          );
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        final String errorMessage = "There were errors while writing event sequence counts.";
        log.error(errorMessage, e);
        throw new OptimizeRuntimeException(errorMessage, e);
      }
    }
  }

  private UpdateRequest createEventSequenceCountUpsertRequest(final EventSequenceCountDto eventSequenceCountDto) {
    IndexRequest indexRequest = null;
    try {
      indexRequest = new IndexRequest(getIndexName()).id(eventSequenceCountDto.getId())
        .source(objectMapper.writeValueAsString(eventSequenceCountDto), XContentType.JSON);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    UpdateRequest updateRequest;
    updateRequest = new UpdateRequest().index(getIndexName()).id(eventSequenceCountDto.getId())
      .script(new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.count += params.adjustmentRequired",
        Collections.singletonMap("adjustmentRequired", eventSequenceCountDto.getCount())
      ))
      .upsert(indexRequest);
    return updateRequest;
  }

  private String getIndexName() {
    return new EventSequenceCountIndex(indexKey).getIndexName();
  }

}
