/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.SETTINGS_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.SettingsIndex.LAST_MODIFIED;
import static io.camunda.optimize.service.db.schema.index.SettingsIndex.SHARING_ENABLED;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.SettingsDto;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.schema.index.SettingsIndex;
import io.camunda.optimize.service.db.writer.SettingsWriter;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.HashSet;
import java.util.Set;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class SettingWriterOS implements SettingsWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(SettingWriterOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final ObjectMapper objectMapper;

  public SettingWriterOS(final OptimizeOpenSearchClient osClient, final ObjectMapper objectMapper) {
    this.osClient = osClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public void upsertSettings(final SettingsDto settingsDto) {
    LOG.debug("Writing settings to OpenSearch");
    final UpdateRequest.Builder<SettingsDto, Void> request = createSettingsUpsert(settingsDto);

    final String errorMessage = "There were errors while writing settings to OpenSearch.";
    osClient.upsert(request, SettingsDto.class, e -> errorMessage);
    LOG.debug("Finished updating Optimize Settings");
  }

  private UpdateRequest.Builder<SettingsDto, Void> createSettingsUpsert(
      final SettingsDto settingsDto) {
    final Set<String> fieldsToUpdate = new HashSet<>();
    if (settingsDto.getSharingEnabled().isPresent()) {
      fieldsToUpdate.add(SHARING_ENABLED);
    }
    if (!fieldsToUpdate.isEmpty()) {
      // This always gets updated
      fieldsToUpdate.add(LAST_MODIFIED);
    } else {
      throw new BadRequestException("No settings can be updated, as no values are present!");
    }

    final Script updateScript =
        OpenSearchWriterUtil.createFieldUpdateScript(fieldsToUpdate, settingsDto, objectMapper);

    return new UpdateRequest.Builder<SettingsDto, Void>()
        .index(SETTINGS_INDEX_NAME)
        .id(SettingsIndex.ID)
        .upsert(settingsDto)
        .script(updateScript)
        .refresh(Refresh.True)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
  }
}
