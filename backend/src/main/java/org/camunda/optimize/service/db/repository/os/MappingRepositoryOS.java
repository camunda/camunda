/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.os;

import static java.lang.String.format;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.stringTerms;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.DbEventProcessMappingDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil;
import org.camunda.optimize.service.db.repository.MappingRepository;
import org.camunda.optimize.service.db.schema.index.events.EventProcessMappingIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(OpenSearchCondition.class)
public class MappingRepositoryOS implements MappingRepository {
  private final OptimizeOpenSearchClient osClient;
  private final ObjectMapper objectMapper;

  @Override
  public IdResponseDto createEventProcessMapping(
      final EventProcessMappingDto eventProcessMappingDto) {
    final String id = eventProcessMappingDto.getId();
    final IndexRequest.Builder<DbEventProcessMappingDto> requestBuilder =
        new IndexRequest.Builder<DbEventProcessMappingDto>()
            .index(EVENT_PROCESS_MAPPING_INDEX_NAME)
            .id(id)
            .document(DbEventProcessMappingDto.fromEventProcessMappingDto(eventProcessMappingDto))
            .refresh(Refresh.True);

    final IndexResponse response = osClient.index(requestBuilder);

    if (!Result.Created.equals(response.result())) {
      final String errorMessage = format("Could not write event-based process [%s].", id);
      throw new OptimizeRuntimeException(errorMessage);
    }
    return new IdResponseDto(id);
  }

  @Override
  public void updateEventProcessMappingWithScript(
      final EventProcessMappingDto eventProcessMappingDto, final Set<String> fieldsToUpdate) {
    final String id = eventProcessMappingDto.getId();
    log.debug("Updating event-based process [{}] in Opensearch.", id);
    eventProcessMappingDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    final Script updateScript =
        OpenSearchWriterUtil.createFieldUpdateScript(
            fieldsToUpdate,
            DbEventProcessMappingDto.fromEventProcessMappingDto(eventProcessMappingDto),
            objectMapper);

    final UpdateRequest.Builder<Void, Void> requestBuilder =
        new UpdateRequest.Builder<Void, Void>()
            .index(EVENT_PROCESS_MAPPING_INDEX_NAME)
            .id(id)
            .script(updateScript)
            .refresh(Refresh.True)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    final UpdateResponse<Void> response =
        osClient.update(
            requestBuilder,
            format("There was a problem updating the event-based process [%s].", id));

    if (!Result.Updated.equals(response.result())) {
      String errorMessage = format("Could not update event-based process [%s] in Opensearch.", id);
      log.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  @Override
  public boolean deleteEventProcessMapping(final String eventProcessMappingId) {
    log.debug("Deleting event-based process with id [{}].", eventProcessMappingId);
    final DeleteRequest.Builder requestBuilder =
        new DeleteRequest.Builder()
            .index(EVENT_PROCESS_MAPPING_INDEX_NAME)
            .id(eventProcessMappingId)
            .refresh(Refresh.True);

    return osClient
        .delete(
            requestBuilder,
            format("Could not delete event-based process with id [%s]. ", eventProcessMappingId))
        .result()
        .equals(Result.Deleted);
  }

  @Override
  public void deleteEventProcessMappings(final List<String> eventProcessMappingIds) {
    log.debug("Deleting event process mapping ids: " + eventProcessMappingIds);
    osClient.deleteByQueryTask(
        "event process mapping ids" + eventProcessMappingIds,
        stringTerms(EventProcessMappingIndex.ID, eventProcessMappingIds),
        true,
        EVENT_PROCESS_MAPPING_INDEX_NAME);
  }
}
