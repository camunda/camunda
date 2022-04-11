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
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.SettingsIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.SettingsIndex.LAST_MODIFIED;
import static org.camunda.optimize.service.es.schema.index.SettingsIndex.LAST_MODIFIER;
import static org.camunda.optimize.service.es.schema.index.SettingsIndex.METADATA_TELEMETRY_ENABLED;
import static org.camunda.optimize.service.es.schema.index.SettingsIndex.SHARING_ENABLED;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SETTINGS_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Slf4j
@Component
public class SettingsWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void upsertSettings(final SettingsResponseDto settingsDto) {
    log.debug("Writing settings to ES");

    try {
      final UpdateRequest request = createSettingsUpsert(settingsDto);
      esClient.update(request);
    } catch (IOException e) {
      final String errorMessage = "There were errors while writing settings.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  private UpdateRequest createSettingsUpsert(final SettingsResponseDto settingsDto)
    throws JsonProcessingException {
    Set<String> fieldsToUpdate = new HashSet<>();

    if(settingsDto.getMetadataTelemetryEnabled().isPresent()) {
      fieldsToUpdate.addAll(Set.of(LAST_MODIFIER, METADATA_TELEMETRY_ENABLED));
    }
    if(settingsDto.getSharingEnabled().isPresent()) {
      fieldsToUpdate.add(SHARING_ENABLED);
    }
    if (!fieldsToUpdate.isEmpty()) {
      // This always gets updated
      fieldsToUpdate.add(LAST_MODIFIED);
    } else {
      throw new BadRequestException("No settings can be updated, as no values are present!");
    }

    final Script updateScript = ElasticsearchWriterUtil.createFieldUpdateScript(
      fieldsToUpdate,
      settingsDto,
      objectMapper
    );

    return new UpdateRequest()
      .index(SETTINGS_INDEX_NAME)
      .id(SettingsIndex.ID)
      .upsert(objectMapper.writeValueAsString(settingsDto), XContentType.JSON)
      .script(updateScript)
      .setRefreshPolicy(IMMEDIATE)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
  }
}
