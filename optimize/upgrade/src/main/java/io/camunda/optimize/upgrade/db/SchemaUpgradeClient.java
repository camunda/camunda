/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.db;

import static io.camunda.optimize.service.db.DatabaseConstants.DATABASE_TASK_DESCRIPTION_DOC_SUFFIX;
import static io.camunda.optimize.service.db.schema.OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.DatabaseQueryWrapper;
import io.camunda.optimize.service.db.repository.TaskRepository;
import io.camunda.optimize.service.db.schema.DatabaseMetadataService;
import io.camunda.optimize.service.db.schema.DatabaseSchemaManager;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;

public abstract class SchemaUpgradeClient<CLIENT extends DatabaseClient, BUILDER, ALIASES> {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(SchemaUpgradeClient.class);
  public DatabaseType databaseType;
  // expected suffix: hyphen and numbers at end of index name
  protected final Pattern indexSuffixPattern = Pattern.compile("-\\d+$");
  protected final DatabaseSchemaManager<CLIENT, BUILDER> schemaManager;
  protected final DatabaseMetadataService<CLIENT> metadataService;
  protected final CLIENT databaseClient;
  protected final TaskRepository taskRepository;

  public SchemaUpgradeClient(
      final DatabaseSchemaManager<CLIENT, BUILDER> schemaManager,
      final DatabaseMetadataService<CLIENT> metadataService,
      final DatabaseType databaseType,
      final CLIENT databaseClient,
      final TaskRepository taskRepository) {
    this.schemaManager = schemaManager;
    this.metadataService = metadataService;
    this.databaseType = databaseType;
    this.databaseClient = databaseClient;
    this.taskRepository = taskRepository;
  }

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

  public abstract boolean indexTemplateExists(final String indexTemplateName);

  public abstract void deleteTemplateIfExists(final String indexTemplateName);

  public abstract void addAliases(
      final Set<String> indexAliases, final String completeIndexName, final boolean isWriteAlias);

  public abstract void insertDataByIndexName(
      final IndexMappingCreator<BUILDER> indexMapping, final String data);

  public abstract void updateDataByIndexName(
      final IndexMappingCreator<BUILDER> indexMapping,
      final DatabaseQueryWrapper queryWrapper,
      final String updateScript,
      final Map<String, Object> parameters);

  public abstract void deleteDataByIndexName(
      final IndexMappingCreator<BUILDER> indexMapping, final DatabaseQueryWrapper queryWrapper);

  public void updateIndex(
      final IndexMappingCreator<BUILDER> indexMapping,
      final String mappingScript,
      final Map<String, Object> parameters,
      final Set<String> additionalReadAliases) {
    if (indexMapping.isCreateFromTemplate()) {
      migrateAllIndicesOfIndex(indexMapping, mappingScript, parameters, additionalReadAliases);
    } else {
      migrateSingleIndex(indexMapping, mappingScript, parameters, additionalReadAliases);
    }
  }

  public static String createReIndexRequestDescription(
      final List<String> sourceIndex, final String targetIndex) {
    return "reindex from " + sourceIndex + " to [" + targetIndex + "]";
  }

  public void deleteIndexIfExists(final String indexName) {
    if (indexExists(indexName)) {
      try {
        databaseClient.deleteIndexByRawIndexNames(indexName);
      } catch (final Exception e) {
        throw new UpgradeRuntimeException(
            String.format("Could not delete index [%s]!", indexName), e);
      }
    }
  }

  // Returns index names that are associated with the given aliasName
  public Set<String> getAliases(final String aliasName) {
    try {
      return databaseClient.getAllIndicesForAlias(aliasName);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  public void reindex(final String sourceIndex, final String targetIndex) {
    reindex(sourceIndex, targetIndex, null, Collections.emptyMap());
  }

  public Optional<String> getSchemaVersion() {
    return metadataService.getSchemaVersion(databaseClient);
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
    LOG.info(
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

  public boolean indexExists(final String indexName) {
    LOG.debug("Checking if index exists [{}].", indexName);
    return schemaManager.indexExists(databaseClient, indexName);
  }

  public void waitUntilTaskIsFinished(final String taskId, final String taskIdentifier) {
    try {
      taskRepository.waitUntilTaskIsFinished(taskId, taskIdentifier);
    } catch (final OptimizeRuntimeException e) {
      throw new UpgradeRuntimeException(e.getCause().getMessage(), e);
    }
  }

  protected boolean areDocCountsEqual(final String sourceIndex, final String targetIndex) {
    try {
      final long sourceIndexDocCount = databaseClient.countWithoutPrefix(sourceIndex);
      final long targetIndexDocCount = databaseClient.countWithoutPrefix(targetIndex);
      return sourceIndexDocCount == targetIndexDocCount;
    } catch (final Exception e) {
      final String errorMessage =
          String.format(
              "Could not compare doc counts of index [%s] and [%s].", sourceIndex, targetIndex);
      LOG.warn(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  protected boolean areTaskAndRequestDescriptionsEqual(
      final String taskDescription, final String requestDescription) {
    return getDescriptionStringWithoutSuffix(taskDescription)
        .equals(getDescriptionStringWithoutSuffix(requestDescription));
  }

  protected String getIndexAlias(final IndexMappingCreator<?> index) {
    return getIndexNameService().getOptimizeIndexAliasForIndex(index.getIndexName());
  }

  protected String getSourceIndexOrTemplateName(
      final IndexMappingCreator<?> index, final String indexAlias) {
    return getOptimizeIndexOrTemplateNameForAliasAndVersion(
        indexAlias, String.valueOf(index.getVersion() - 1));
  }

  protected String getDescriptionStringWithoutSuffix(final String descriptionString) {
    if (descriptionString.endsWith(DATABASE_TASK_DESCRIPTION_DOC_SUFFIX)) {
      return descriptionString.substring(
          0, descriptionString.length() - DATABASE_TASK_DESCRIPTION_DOC_SUFFIX.length());
    }
    return descriptionString;
  }

  protected void applyAdditionalReadOnlyAliasesToIndex(
      final Set<String> additionalReadAliases, final String indexName) {
    for (final String alias : additionalReadAliases) {
      addAlias(getIndexNameService().getOptimizeIndexAliasForIndex(alias), indexName, false);
    }
  }

  protected void validateStatusOfPendingTask(final String reindexTaskId)
      throws UpgradeRuntimeException, IOException {
    try {
      taskRepository.validateTaskResponse(taskRepository.getTaskResponse(reindexTaskId));
    } catch (final OptimizeRuntimeException ex) {
      throw new UpgradeRuntimeException(
          String.format(
              "Found pending task with id %s, but it is not in a completable state", reindexTaskId),
          ex);
    }
  }

  public DatabaseType getDatabaseType() {
    return databaseType;
  }

  private void migrateSingleIndex(
      final IndexMappingCreator<BUILDER> index,
      final String mappingScript,
      final Map<String, Object> parameters,
      final Set<String> additionalReadAliases) {
    final String indexAlias = getIndexAlias(index);
    final String sourceIndexName = getSourceIndexOrTemplateName(index, indexAlias);
    final String targetIndexName = getIndexNameService().getOptimizeIndexNameWithVersion(index);
    if (!indexExists(sourceIndexName)) {
      // if the expected source index is not available anymore there are only two possibilities:
      // 1. it never existed (unexpected edge-case)
      // 2. a previous upgrade run completed this step already
      // in both cases we can try to create/update the target index in a fail-safe way
      LOG.info(
          "Source index {} was not found, will just create/update the new index {}.",
          sourceIndexName,
          targetIndexName);
      createOrUpdateIndex(index);
    } else {
      // create new index and reindex data to it
      final ALIASES existingAliases = getAllAliasesForIndex(sourceIndexName);
      setAllAliasesToReadOnly(sourceIndexName, existingAliases);
      createOrUpdateIndex(index);
      reindex(sourceIndexName, targetIndexName, mappingScript, parameters);
      applyAliasesToIndex(targetIndexName, existingAliases);
      applyAdditionalReadOnlyAliasesToIndex(additionalReadAliases, targetIndexName);
      // in case of retries it might happen that the default write index flag is overwritten as the
      // source index
      // was already set to be a read-only index for all associated indices
      addAlias(indexAlias, targetIndexName, true);
      deleteIndexIfExists(sourceIndexName);
    }
  }

  /**
   * Historically, Optimize has used legacy templates to create External Variable indices. We use
   * the migration of these indices as an opportunity to consolidate all indices created from this
   * template into a single index. This would only be called if we want to migrate that index,
   * otherwise we have to handle the case where multiple indices can exist for that mapping
   */
  private void migrateAllIndicesOfIndex(
      final IndexMappingCreator<BUILDER> index,
      final String mappingScript,
      final Map<String, Object> parameters,
      final Set<String> additionalReadAliases) {
    final String indexAlias = getIndexAlias(index);
    final Set<String> allIndicesForAlias;
    try {
      allIndicesForAlias = databaseClient.getAllIndicesForAlias(indexAlias);
    } catch (final IOException e) {
      throw new UpgradeRuntimeException("Could not fetch", e);
    }
    final String targetIndexName = getIndexNameService().getOptimizeIndexNameWithVersion(index);
    createOrUpdateIndex(index);
    for (final String sourceIndexName : allIndicesForAlias) {
      if (!indexExists(sourceIndexName)) {
        // if the expected source index is not available anymore there are only two possibilities:
        // 1. it never existed (unexpected edge-case)
        // 2. a previous upgrade run completed this step already
        // in both cases, we take no action and assume we can continue
      } else {
        // create new index and reindex data to it
        final ALIASES existingAliases = getAllAliasesForIndex(sourceIndexName);
        setAllAliasesToReadOnly(sourceIndexName, existingAliases);
        reindex(sourceIndexName, targetIndexName, mappingScript, parameters);
        applyAliasesToIndex(targetIndexName, existingAliases);
        applyAdditionalReadOnlyAliasesToIndex(additionalReadAliases, targetIndexName);
        // in case of retries it might happen that the default write index flag is overwritten as
        // the source index was already set to be a read-only index for all associated indices
        addAlias(indexAlias, targetIndexName, true);
        deleteIndexIfExists(sourceIndexName);
      }
    }
  }

  protected abstract ALIASES getAllAliasesForIndex(final String indexName);

  protected abstract void setAllAliasesToReadOnly(final String indexName, final ALIASES aliases);

  protected abstract void applyAliasesToIndex(final String indexName, final ALIASES aliases);
}
