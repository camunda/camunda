/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.db;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.DatabaseQueryWrapper;
import io.camunda.optimize.service.db.schema.DatabaseMetadataService;
import io.camunda.optimize.service.db.schema.DatabaseSchemaManager;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class SchemaUpgradeClient<CLIENT extends DatabaseClient, BUILDER> {
  // expected suffix: hyphen and numbers at end of index name
  protected final Pattern indexSuffixPattern = Pattern.compile("-\\d+$");
  protected final DatabaseSchemaManager<CLIENT, BUILDER> schemaManager;
  protected final DatabaseMetadataService<CLIENT> metadataService;
  protected final CLIENT databaseClient;
  @Getter public DatabaseType databaseType;

  public SchemaUpgradeClient(
      final DatabaseSchemaManager<CLIENT, BUILDER> schemaManager,
      final DatabaseMetadataService<CLIENT> metadataService,
      final DatabaseType databaseType,
      final CLIENT databaseClient) {
    this.schemaManager = schemaManager;
    this.metadataService = metadataService;
    this.databaseType = databaseType;
    this.databaseClient = databaseClient;
  }

  public abstract void reindex(final String sourceIndex, final String targetIndex);

  public abstract List<UpgradeStepLogEntryDto> getAppliedUpdateStepsForTargetVersion(
      final String targetOptimizeVersion);

  public abstract void reindex(
      final IndexMappingCreator<BUILDER> sourceIndex,
      final IndexMappingCreator<BUILDER> targetIndex,
      final DatabaseQueryWrapper queryWrapper,
      final String mappingScript);

  public abstract void reindex(
      final String sourceIndex,
      final String targetIndex,
      final String mappingScript,
      final Map<String, Object> parameters);

  public abstract <T> void upsert(final String index, final String id, final T documentDto);

  public abstract <T> Optional<T> getDocumentByIdAs(
      final String index, final String id, final Class<T> resultType);

  public abstract boolean indexExists(final String indexName);

  public void deleteIndexIfExists(final String indexName) {
    databaseClient.deleteIndex(indexName);
  }

  public abstract boolean indexTemplateExists(final String indexTemplateName);

  public abstract void deleteTemplateIfExists(final String indexTemplateName);

  public abstract void createIndexFromTemplate(final String indexNameWithSuffix);

  public abstract void addAliases(
      final Set<String> indexAliases, final String completeIndexName, final boolean isWriteAlias);

  // Returns index names that are associated with the given aliasName
  @SneakyThrows
  public Set<String> getAliases(final String aliasName) {
    return databaseClient.getAllIndicesForAlias(aliasName);
  }

  public abstract void insertDataByIndexName(
      final IndexMappingCreator<BUILDER> indexMapping, final String data);

  public abstract void updateDataByIndexName(
      final IndexMappingCreator<BUILDER> indexMapping,
      final DatabaseQueryWrapper queryWrapper,
      final String updateScript,
      final Map<String, Object> parameters);

  public abstract void deleteDataByIndexName(
      final IndexMappingCreator<BUILDER> indexMapping, final DatabaseQueryWrapper queryWrapper);

  public Optional<String> getSchemaVersion() {
    return metadataService.getSchemaVersion(databaseClient);
  }

  public void createOrUpdateTemplateWithoutAliases(
      final IndexMappingCreator<BUILDER> mappingCreator) {
    schemaManager.createOrUpdateTemplateWithoutAliases(databaseClient, mappingCreator);
  }

  public void createOrUpdateIndex(final IndexMappingCreator<BUILDER> indexMapping) {
    schemaManager.createOrUpdateOptimizeIndex(databaseClient, indexMapping);
  }

  public void createOrUpdateIndex(
      final IndexMappingCreator<BUILDER> indexMapping, final Set<String> readOnlyAliases) {
    schemaManager.createOrUpdateOptimizeIndex(databaseClient, indexMapping, readOnlyAliases);
  }

  public void initializeSchema() {
    schemaManager.initializeSchema(databaseClient);
  }

  public OptimizeIndexNameService getIndexNameService() {
    return databaseClient.getIndexNameService();
  }

  public void updateOptimizeVersion(final UpgradePlan upgradePlan) {
    log.info(
        "Updating Optimize data structure version tag from {} to {}.",
        upgradePlan.getFromVersion().toString(),
        upgradePlan.getToVersion().toString());
    metadataService.upsertMetadata(databaseClient, upgradePlan.getToVersion().toString());
  }

  public void addAlias(
      final String indexAlias, final String completeIndexName, final boolean isWriteAlias) {
    addAliases(Collections.singleton(indexAlias), completeIndexName, isWriteAlias);
  }

  public void updateIndexDynamicSettingsAndMappings(
      final IndexMappingCreator<BUILDER> indexMapping) {
    schemaManager.updateDynamicSettingsAndMappings(databaseClient, indexMapping);
  }

  public abstract void updateIndex(
      final IndexMappingCreator<BUILDER> indexMapping,
      final String mappingScript,
      final Map<String, Object> parameters,
      final Set<String> additionalReadAliases);
}
