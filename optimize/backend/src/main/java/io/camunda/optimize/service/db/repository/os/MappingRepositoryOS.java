/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.stringTerms;
import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.db.DbEventProcessMappingDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil;
import io.camunda.optimize.service.db.repository.MappingRepository;
import io.camunda.optimize.service.db.schema.index.events.EventProcessMappingIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.Script;
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
    try {
      final UpdateResponse<Void> response =
          osClient.update(
              requestBuilder,
              format("There was a problem updating the event-based process [%s].", id));

      if (!Result.Updated.equals(response.result())) {
        String errorMessage =
            format("Could not update event-based process [%s] in Opensearch.", id);
        log.error(errorMessage);
        throw new OptimizeRuntimeException(errorMessage);
      }
    } catch (OpenSearchException e) {
      String errorMessage =
          String.format(
              "Was not able to update event-based process with id [%s]. Event-based process does not exist!",
              id);
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    } catch (Exception e) {
      final String errorMessage =
          String.format("There was a problem updating the event-based process [%s].", id);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
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
