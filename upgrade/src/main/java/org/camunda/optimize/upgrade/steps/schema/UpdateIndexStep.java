/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.schema;

import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.elasticsearch.cluster.metadata.AliasMetadata;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameService.getOptimizeIndexNameForAliasAndVersion;

public class UpdateIndexStep implements UpgradeStep {
  private final IndexMappingCreator index;
  private final String mappingScript;
  private final Map<String, Object> parameters;
  // expected suffix: hyphen and numbers at end of index name
  private final Pattern indexSuffixPattern = Pattern.compile("-\\d+$");

  public UpdateIndexStep(final IndexMappingCreator index, final String mappingScript) {
    this.index = index;
    this.mappingScript = mappingScript;
    this.parameters = Collections.emptyMap();
  }

  public UpdateIndexStep(final IndexMappingCreator index, final String mappingScript,
                         final Map<String, Object> parameters) {
    this.index = index;
    this.mappingScript = mappingScript;
    this.parameters = parameters;
  }

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    String indexName = index.getIndexName();
    int targetVersion = index.getVersion();
    final OptimizeIndexNameService indexNameService = schemaUpgradeClient.getIndexNameService();
    final String indexAlias = indexNameService.getOptimizeIndexAliasForIndex(indexName);
    final String sourceVersionAsString = String.valueOf(targetVersion - 1);
    final String targetVersionAsString = String.valueOf(targetVersion);
    final String sourceIndexName = getOptimizeIndexNameForAliasAndVersion(
      indexAlias, sourceVersionAsString
    );
    final String targetIndexName = getOptimizeIndexNameForAliasAndVersion(
      indexAlias, targetVersionAsString
    );

    if (index.getCreateFromTemplate()) {
      // create new template & indices and reindex data to it
      schemaUpgradeClient.createOrUpdateTemplateWithoutAliases(index, indexAlias);
      final Map<String, Set<AliasMetadata>> indexAliasMap = schemaUpgradeClient.getAliasMap(indexAlias);
      for (String sourceIndex : indexAliasMap.keySet()) {
        String suffix;
        String sourceIndexNameWithSuffix;
        Matcher suffixMatcher = indexSuffixPattern.matcher(sourceIndex);

        if (suffixMatcher.find()) {
          // sourceIndex is already suffixed
          suffix = sourceIndex.substring(sourceIndex.lastIndexOf("-"));
          sourceIndexNameWithSuffix = sourceIndexName + suffix;
        } else {
          // sourceIndex is not yet suffixed, use default suffix
          suffix = index.getIndexNameInitialSuffix();
          sourceIndexNameWithSuffix = sourceIndexName;
        }
        final String targetIndexNameWithSuffix = targetIndexName + suffix;

        final Set<AliasMetadata> existingAliases = schemaUpgradeClient.getAllAliasesForIndex(sourceIndexNameWithSuffix);
        schemaUpgradeClient.setAllAliasesToReadOnly(sourceIndexNameWithSuffix, existingAliases);
        schemaUpgradeClient.createIndexFromTemplate(targetIndexNameWithSuffix);
        schemaUpgradeClient.reindex(sourceIndexNameWithSuffix, targetIndexNameWithSuffix, mappingScript, parameters);
        applyAliasesToIndex(schemaUpgradeClient, targetIndexNameWithSuffix, existingAliases);
        schemaUpgradeClient.deleteIndex(sourceIndexNameWithSuffix);
      }
    } else {
      // create new index and reindex data to it
      final Set<AliasMetadata> existingAliases = schemaUpgradeClient.getAllAliasesForIndex(sourceIndexName);
      schemaUpgradeClient.setAllAliasesToReadOnly(sourceIndexName, existingAliases);
      schemaUpgradeClient.createIndex(index);
      schemaUpgradeClient.reindex(sourceIndexName, targetIndexName, mappingScript, parameters);
      applyAliasesToIndex(schemaUpgradeClient, targetIndexName, existingAliases);
      schemaUpgradeClient.deleteIndex(sourceIndexName);
    }
  }

  private void applyAliasesToIndex(final SchemaUpgradeClient schemaUpgradeClient,
                                   final String indexName,
                                   final Set<AliasMetadata> aliases) {
    for (AliasMetadata alias : aliases) {
      schemaUpgradeClient.addAlias(
        alias.getAlias(),
        indexName,
        // defaulting to true if this flag is not set but only one index exists
        Optional.ofNullable(alias.writeIndex()).orElse(aliases.size() == 1)
      );
    }
  }
}
