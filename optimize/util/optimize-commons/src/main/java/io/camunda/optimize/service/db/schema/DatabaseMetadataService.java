/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.MetadataDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.metadata.Version;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public abstract class DatabaseMetadataService<CLIENT extends DatabaseClient> {

  protected static final String ERROR_MESSAGE_REQUEST =
      "Could not write Optimize metadata (version and installationID) to database.";
  protected static final String ERROR_MESSAGE_READING_METADATA_DOC =
      "Failed retrieving the Optimize metadata document from database!";
  protected static final String CURRENT_OPTIMIZE_VERSION = Version.VERSION;
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(DatabaseMetadataService.class);

  protected final ObjectMapper objectMapper;

  public DatabaseMetadataService(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public abstract Optional<MetadataDto> readMetadata(final CLIENT dbClient);

  public void initMetadataIfMissing(final CLIENT dbClient) {
    final String newInstallationId = UUID.randomUUID().toString();

    upsertMetadataWithScript(
        dbClient,
        CURRENT_OPTIMIZE_VERSION,
        newInstallationId,
        createInitInstallationIdScriptIfMissing(newInstallationId));
  }

  public void validateMetadata(final CLIENT dbClient) {
    readMetadata(dbClient)
        .ifPresent(
            metadataDto -> {
              if (!CURRENT_OPTIMIZE_VERSION.equals(metadataDto.getSchemaVersion())) {
                final String errorMessage =
                    String.format(
                        "The database Optimize schema version [%s] doesn't match the current Optimize version [%s]."
                            + " Please make sure to run the Upgrade first.",
                        metadataDto.getSchemaVersion(), CURRENT_OPTIMIZE_VERSION);
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
        createUpdateMetadataScript(newInstallationId, schemaVersion));
  }

  protected abstract void upsertMetadataWithScript(
      final CLIENT dbClient,
      final String schemaVersion,
      final String newInstallationId,
      final ScriptData updateScript);

  protected ScriptData createUpdateMetadataScript(
      final String newInstallationId, final String newSchemaVersion) {
    return generateUpdateScript(newInstallationId, newSchemaVersion);
  }

  protected ScriptData createInitInstallationIdScriptIfMissing(final String newInstallationId) {
    return generateUpdateScript(newInstallationId);
  }

  protected ScriptData generateUpdateScript(
      final String newInstallationId, final String newSchemaVersion) {
    final Map<String, Object> params = new HashMap<>();
    params.put("newInstallationId", newInstallationId);
    String scriptString =
        "if (ctx._source."
            + MetadataDto.Fields.installationId.name()
            + " == null) {\n"
            + "    ctx._source."
            + MetadataDto.Fields.installationId.name()
            + " = params.newInstallationId;\n"
            + "}\n";
    if (!StringUtils.isBlank(newSchemaVersion)) {
      params.put("newSchemaVersion", newSchemaVersion);
      scriptString +=
          "ctx._source."
              + MetadataDto.Fields.schemaVersion.name()
              + " = params.newSchemaVersion;\n";
    }
    return new ScriptData(params, scriptString);
  }

  private ScriptData generateUpdateScript(final String newInstallationId) {
    return generateUpdateScript(newInstallationId, null);
  }
}
