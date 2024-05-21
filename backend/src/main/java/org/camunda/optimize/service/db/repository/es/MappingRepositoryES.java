/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.es;

import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.DbEventProcessMappingDto;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.db.repository.MappingRepository;
import org.camunda.optimize.service.db.schema.index.events.EventProcessMappingIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.script.Script;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class MappingRepositoryES implements MappingRepository {
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  @Override
  public IdResponseDto createEventProcessMapping(
      final EventProcessMappingDto eventProcessMappingDto) {
    final String id = eventProcessMappingDto.getId();
    IndexResponse indexResponse;
    try {
      final IndexRequest request =
          new IndexRequest(EVENT_PROCESS_MAPPING_INDEX_NAME)
              .id(id)
              .source(
                  objectMapper.writeValueAsString(
                      DbEventProcessMappingDto.fromEventProcessMappingDto(eventProcessMappingDto)),
                  XContentType.JSON)
              .setRefreshPolicy(IMMEDIATE);
      indexResponse = esClient.index(request);
    } catch (IOException e) {
      final String errorMessage =
          String.format("There was a problem while writing the event-based process [%s].", id);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
      final String errorMessage = String.format("Could not write event-based process [%s].", id);
      throw new OptimizeRuntimeException(errorMessage);
    }
    return new IdResponseDto(id);
  }

  @Override
  public void updateEventProcessMappingWithScript(
      final EventProcessMappingDto eventProcessMappingDto, final Set<String> fieldsToUpdate) {
    final String id = eventProcessMappingDto.getId();
    log.debug("Updating event-based process [{}] in Elasticsearch.", id);
    eventProcessMappingDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    try {
      final Script updateScript =
          ElasticsearchWriterUtil.createFieldUpdateScript(
              fieldsToUpdate,
              DbEventProcessMappingDto.fromEventProcessMappingDto(eventProcessMappingDto),
              objectMapper);

      final UpdateRequest request =
          new UpdateRequest()
              .index(EVENT_PROCESS_MAPPING_INDEX_NAME)
              .id(id)
              .script(updateScript)
              .setRefreshPolicy(IMMEDIATE)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      final UpdateResponse updateResponse = esClient.update(request);
      if (!updateResponse.getResult().equals(IndexResponse.Result.UPDATED)) {
        String errorMessage =
            String.format("Could not update event-based process [%s] to Elasticsearch.", id);
        log.error(errorMessage);
        throw new OptimizeRuntimeException(errorMessage);
      }
    } catch (IOException e) {
      final String errorMessage =
          String.format("There was a problem updating the event-based process [%s].", id);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage =
          String.format(
              "Was not able to update event-based process with id [%s]. Event-based process does not exist!",
              id);
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  @Override
  public void deleteEventProcessMappings(final List<String> eventProcessMappingIds) {
    log.debug("Deleting event process mapping ids: " + eventProcessMappingIds);

    try {
      ElasticsearchWriterUtil.tryDeleteByQueryRequest(
          esClient,
          boolQuery().must(termsQuery(EventProcessMappingIndex.ID, eventProcessMappingIds)),
          "event process mapping ids" + eventProcessMappingIds,
          true,
          EVENT_PROCESS_MAPPING_INDEX_NAME);
    } catch (OptimizeRuntimeException e) {
      String reason = "Could not delete event process mappings due to an unexpected error.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }
}
