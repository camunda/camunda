/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.metadata.Version;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
@Slf4j
public abstract class DatabaseMetadataService<CLIENT extends DatabaseClient> {
  protected static final String ERROR_MESSAGE_REQUEST =
    "Could not write Optimize metadata (version and installationID) to database.";
  protected static final String ERROR_MESSAGE_READING_METADATA_DOC =
    "Failed retrieving the Optimize metadata document from database!";
  protected static final String CURRENT_OPTIMIZE_VERSION = Version.VERSION;

  protected final ObjectMapper objectMapper;

  public abstract Optional<MetadataDto> readMetadata(final CLIENT dbClient);

  public void initMetadataIfMissing(final CLIENT dbClient) {
    final String newInstallationId = UUID.randomUUID().toString();
    upsertMetadataWithScript(
      dbClient,
      CURRENT_OPTIMIZE_VERSION,
      newInstallationId,
      createInitInstallationIdScriptIfMissing(newInstallationId)
    );
  }

  public void validateSchemaVersionCompatibility(final CLIENT dbClient) {
    readMetadata(dbClient).ifPresent(metadataDto -> {
      if (!CURRENT_OPTIMIZE_VERSION.equals(metadataDto.getSchemaVersion())) {
        final String errorMessage = String.format(
          "The database Optimize schema version [%s] doesn't match the current Optimize version [%s]."
            + " Please make sure to run the Upgrade first.",
          metadataDto.getSchemaVersion(),
          CURRENT_OPTIMIZE_VERSION
        );
        throw new OptimizeRuntimeException(errorMessage);
      }
    });
  }

  public Optional<String> getSchemaVersion(final CLIENT dbClient) {
    return readMetadata(dbClient).map(MetadataDto::getSchemaVersion);
  }

  public void upsertMetadata(final CLIENT dbClient, final String schemaVersion) {
    final String newInstallationId = UUID.randomUUID().toString();
    upsertMetadataWithScript(
      dbClient,
      schemaVersion,
      newInstallationId,
      createUpdateMetadataScript(newInstallationId, schemaVersion)
    );
  }

  protected abstract void upsertMetadataWithScript(final CLIENT dbClient,
                                                   final String schemaVersion,
                                                   final String newInstallationId,
                                                   final ScriptData updateScript);

  protected ScriptData createUpdateMetadataScript(final String newInstallationId, final String newSchemaVersion) {
    return generateUpdateScript(newInstallationId, newSchemaVersion);
  }

  protected ScriptData createInitInstallationIdScriptIfMissing(final String newInstallationId) {
    return generateUpdateScript(newInstallationId);
  }

  protected ScriptData generateUpdateScript(String newInstallationId, String newSchemaVersion) {
    final Map<String, Object> params = new HashMap<>();
    params.put("newInstallationId", newInstallationId);
    String scriptString =
      "if (ctx._source." + MetadataDto.Fields.installationId.name() + " == null) {\n" +
        "    ctx._source." + MetadataDto.Fields.installationId.name() + " = params.newInstallationId;\n" +
        "}\n";
    if (!StringUtils.isBlank(newSchemaVersion)) {
      params.put("newSchemaVersion", newSchemaVersion);
      scriptString += "ctx._source." + MetadataDto.Fields.schemaVersion.name() + " = params.newSchemaVersion;\n";
    }

    return new ScriptData(params, scriptString);
  }

  protected ScriptData generateUpdateScript(String newInstallationId) {
    return generateUpdateScript(newInstallationId, null);
  }

  protected record ScriptData(Map<String, Object> params, String scriptString) {}

}
