/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.BadRequestException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.service.db.writer.SettingsWriter;
import org.camunda.optimize.service.es.schema.index.SettingsIndex;
import org.camunda.optimize.service.os.OptimizeOpensearchClient;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.SettingsIndex.*;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SETTINGS_INDEX_NAME;

@AllArgsConstructor
@Slf4j
@Component
@Conditional(OpenSearchCondition.class)
public class SettingWriterOS implements SettingsWriter {

  private final OptimizeOpensearchClient osClient;
  private final ObjectMapper objectMapper;

  @Override
  public void upsertSettings(final SettingsResponseDto settingsDto) {
//        log.debug("Writing settings to OS");
//        final UpdateRequest.Builder<Void, SettingsResponseDto> request = createSettingsUpsert(settingsDto);
//
//        osClient.update(request, e -> {
//            final String errorMessage = "There were errors while writing settings to OS.";
//            log.error(errorMessage, e);
//            return "There were errors while writing settings to OS." + e.getMessage();
//        });
    throw new NotImplementedException();
  }

  private UpdateRequest.Builder<Void, SettingsResponseDto> createSettingsUpsert(final SettingsResponseDto settingsDto) {
    Set<String> fieldsToUpdate = new HashSet<>();

    if (settingsDto.getMetadataTelemetryEnabled().isPresent()) {
      fieldsToUpdate.addAll(Set.of(LAST_MODIFIER, METADATA_TELEMETRY_ENABLED));
    }
    if (settingsDto.getSharingEnabled().isPresent()) {
      fieldsToUpdate.add(SHARING_ENABLED);
    }
    if (!fieldsToUpdate.isEmpty()) {
      //todo why if there is not updated values for example
      // This always gets updated
      fieldsToUpdate.add(LAST_MODIFIED);
    } else {
      throw new BadRequestException("No settings can be updated, as no values are present!");
    }

    final Script updateScript = OpensearchWriterUtil.createFieldUpdateScript(
      fieldsToUpdate,
      settingsDto,
      objectMapper
    );

    return new UpdateRequest.Builder<Void, SettingsResponseDto>()
      .index(SETTINGS_INDEX_NAME)
      .id(SettingsIndex.ID)
      .doc(settingsDto)
      //todo commented because the first arg is Void
      // (This is a way to indicate that no full document is associated with the update operation.)
      //.upsert(objectMapper.writeValueAsString(settingsDto))
      .script(updateScript)
      .refresh(Refresh.True)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

  }
}
