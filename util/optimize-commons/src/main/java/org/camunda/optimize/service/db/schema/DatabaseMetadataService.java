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
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.OptimizeProfile;
import org.springframework.core.env.Environment;
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
  public final Environment environment;

  public abstract Optional<MetadataDto> readMetadata(final CLIENT dbClient);

  public void initMetadataIfMissing(final CLIENT dbClient) {
    final String newInstallationId = UUID.randomUUID().toString();
    final Optional<OptimizeProfile> currentOptimizeProfile = getCurrentOptimizeProfile();

    upsertMetadataWithScript(
      dbClient,
      CURRENT_OPTIMIZE_VERSION,
      newInstallationId,
      currentOptimizeProfile
        .map(profile -> createInitInstallationIdAndProfileScriptIfMissing(newInstallationId, profile))
        .orElseGet(() -> createInitInstallationIdAndProfileScriptIfMissing(newInstallationId))
    );
  }

  public void initMetadataV3IfMissing(final CLIENT dbClient) {
    final String newInstallationId = UUID.randomUUID().toString();

    upsertMetadataWithScript(
      dbClient,
      CURRENT_OPTIMIZE_VERSION,
      newInstallationId,
      generateUpdateScriptV3(newInstallationId, null)
    );
  }

  public void validateMetadata(final CLIENT dbClient) {
    validateSchemaVersionCompatibility(dbClient);
    validateConfiguredOptimizeProfile(dbClient);
  }

  public Optional<String> getSchemaVersion(final CLIENT dbClient) {
    return readMetadata(dbClient).map(MetadataDto::getSchemaVersion);
  }

  public Optional<OptimizeProfile> getPersistedOptimizeProfile(final CLIENT dbClient) {
    return readMetadata(dbClient).map(MetadataDto::getOptimizeProfile);
  }

  public void upsertMetadata(final CLIENT dbClient, final String schemaVersion) {
    final String newInstallationId = UUID.randomUUID().toString();
    final Optional<OptimizeProfile> newOptimizeProfile = getCurrentOptimizeProfile();
    upsertMetadataWithScript(
      dbClient,
      schemaVersion,
      newInstallationId,
      newOptimizeProfile
        .map(profile -> createUpdateMetadataScript(newInstallationId, schemaVersion, profile))
        .orElseGet(() -> createUpdateMetadataScript(newInstallationId, schemaVersion))
    );
  }

  public void upsertMetadataV3(final CLIENT dbClient, final String schemaVersion) {
    final String newInstallationId = UUID.randomUUID().toString();
    upsertMetadataWithScript(
      dbClient,
      schemaVersion,
      newInstallationId,
      generateUpdateScriptV3(newInstallationId, schemaVersion)
    );
  }

  protected abstract void upsertMetadataWithScript(final CLIENT dbClient,
                                                   final String schemaVersion,
                                                   final String newInstallationId,
                                                   final OptimizeProfile optimizeProfile,
                                                   final ScriptData updateScript);

  protected abstract void upsertMetadataWithScript(final CLIENT dbClient,
                                                   final String schemaVersion,
                                                   final String newInstallationId,
                                                   final ScriptData updateScript);

  protected ScriptData createUpdateMetadataScript(final String newInstallationId, final String newSchemaVersion,
                                                  final OptimizeProfile newOptimizeProfile) {
    return generateUpdateScript(newInstallationId, newSchemaVersion, newOptimizeProfile);
  }

  protected ScriptData createUpdateMetadataScript(final String newInstallationId, final String newSchemaVersion) {
    return generateUpdateScript(newInstallationId, newSchemaVersion, null);
  }

  protected ScriptData createInitInstallationIdAndProfileScriptIfMissing(final String newInstallationId,
                                                                         final OptimizeProfile currentOptimizeProfile) {
    return generateUpdateScript(newInstallationId, currentOptimizeProfile);
  }

  protected ScriptData createInitInstallationIdAndProfileScriptIfMissing(final String newInstallationId) {
    return generateUpdateScript(newInstallationId, null);
  }

  protected ScriptData generateUpdateScript(String newInstallationId, String newSchemaVersion,
                                            OptimizeProfile newOptimizeProfile) {
    final Map<String, Object> params = new HashMap<>();
    params.put("newInstallationId", newInstallationId);
    String scriptString =
      "if (ctx._source." + MetadataDto.Fields.installationId.name() + " == null) {\n" +
        "    ctx._source." + MetadataDto.Fields.installationId.name() + " = params.newInstallationId;\n" +
        "}\n";
    if (StringUtils.isNotBlank(newSchemaVersion)) {
      params.put("newSchemaVersion", newSchemaVersion);
      scriptString += "ctx._source." + MetadataDto.Fields.schemaVersion.name() + " = params.newSchemaVersion;\n";
    }
    if (newOptimizeProfile != null) {
      params.put("newOptimizeProfile", newOptimizeProfile.getId());
      scriptString += "ctx._source." + MetadataDto.Fields.optimizeProfile.name() + " = params.newOptimizeProfile;\n";
    }

    return new ScriptData(params, scriptString);
  }

  private ScriptData generateUpdateScriptV3(String newInstallationId, String newSchemaVersion) {
    return generateUpdateScript(newInstallationId, newSchemaVersion, null);
  }

  private ScriptData generateUpdateScript(String newInstallationId, OptimizeProfile newOptimizeProfile) {
    return generateUpdateScript(newInstallationId, null, newOptimizeProfile);
  }

  private void validateSchemaVersionCompatibility(final CLIENT dbClient) {
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

  private void validateConfiguredOptimizeProfile(final CLIENT dbClient) {
    getCurrentOptimizeProfile().ifPresentOrElse(
      currentOptimizeProfile -> getPersistedOptimizeProfile((dbClient))
        .filter(persistedOptProfile -> !persistedOptProfile.equals(currentOptimizeProfile))
        .ifPresent(persistedOptProfile -> {
          final String errorMessage = String.format(
            "The mode Optimize has saved in the database, [%s], does not match the current running mode, [%s]." +
              " Please make sure Optimize is running in the correct mode",
            persistedOptProfile,
            currentOptimizeProfile
          );
          throw new OptimizeConfigurationException(errorMessage);
        }),
      () -> log.debug("No configured profile could be detected, skipping validation")
    );
  }

  private Optional<OptimizeProfile> getCurrentOptimizeProfile() {
    return Optional.ofNullable(environment).map(env -> ConfigurationService.getOptimizeProfile(environment));
  }

}
