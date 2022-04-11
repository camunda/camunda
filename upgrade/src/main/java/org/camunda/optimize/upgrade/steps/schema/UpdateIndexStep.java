/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.steps.schema;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.UpgradeStepType;
import org.elasticsearch.cluster.metadata.AliasMetadata;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion;

@EqualsAndHashCode(callSuper = true)
@Slf4j
public class UpdateIndexStep extends UpgradeStep {
  private final String mappingScript;
  private final Map<String, Object> parameters;
  // expected suffix: hyphen and numbers at end of index name
  private final Pattern indexSuffixPattern = Pattern.compile("-\\d+$");
  private final Set<String> additionalReadAliases;

  public UpdateIndexStep(final IndexMappingCreator index) {
    this(index, null, Collections.emptyMap(), Collections.emptySet());
  }

  public UpdateIndexStep(final IndexMappingCreator index, final Set<String> additionalReadAliases) {
    this(index, null, Collections.emptyMap(), additionalReadAliases);
  }

  public UpdateIndexStep(final IndexMappingCreator index, final String mappingScript) {
    this(index, mappingScript, Collections.emptyMap(), Collections.emptySet());
  }

  public UpdateIndexStep(final IndexMappingCreator index,
                         final String mappingScript,
                         final Map<String, Object> parameters,
                         final Set<String> additionalReadAliases) {
    super(index);
    this.mappingScript = mappingScript;
    this.parameters = parameters;
    this.additionalReadAliases = additionalReadAliases;
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.SCHEMA_UPDATE_INDEX;
  }

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    if (index.isCreateFromTemplate()) {
      updateIndexTemplateAndAssociatedIndexes(schemaUpgradeClient);
    } else {
      migrateSingleIndex(schemaUpgradeClient);
    }
  }

  private void updateIndexTemplateAndAssociatedIndexes(final SchemaUpgradeClient schemaUpgradeClient) {
    final String indexAlias = getIndexAlias(schemaUpgradeClient);
    final String sourceTemplateName = getSourceIndexOrTemplateName(indexAlias);
    // create new template & indices and reindex data to it
    schemaUpgradeClient.createOrUpdateTemplateWithoutAliases(index);
    final Map<String, Set<AliasMetadata>> indexAliasMap = schemaUpgradeClient.getAliasMap(indexAlias);
    // this ensures the migration happens in a consistent order
    final List<String> sortedIndices = indexAliasMap.keySet().stream()
      // we are only interested in indices based on the source template
      // in resumed update scenarios this could also contain indices based on the targetTemplateName already
      // which we don't need to care about
      .filter(indexName -> indexName.contains(sourceTemplateName))
      .sorted().collect(Collectors.toList());
    for (final String sourceIndex : sortedIndices) {
      final String suffix;
      final Matcher suffixMatcher = indexSuffixPattern.matcher(sourceIndex);
      if (suffixMatcher.find()) {
        // sourceIndex is already suffixed
        suffix = sourceIndex.substring(sourceIndex.lastIndexOf("-"));
      } else {
        // sourceIndex is not yet suffixed, use default suffix
        suffix = index.getIndexNameInitialSuffix();
      }
      final String targetIndexName = getTargetIndexOrTemplateName(schemaUpgradeClient) + suffix;

      final Set<AliasMetadata> existingAliases = schemaUpgradeClient.getAllAliasesForIndex(sourceIndex);
      schemaUpgradeClient.setAllAliasesToReadOnly(sourceIndex, existingAliases);
      schemaUpgradeClient.createIndexFromTemplate(targetIndexName);
      schemaUpgradeClient.reindex(sourceIndex, targetIndexName, mappingScript, parameters);
      applyAliasesToIndex(schemaUpgradeClient, targetIndexName, existingAliases);
      applyAdditionalReadOnlyAliasesToIndex(schemaUpgradeClient, targetIndexName);
      // for rolled over indices only the last one is eligible as writeIndex
      if (sortedIndices.indexOf(sourceIndex) == sortedIndices.size() - 1) {
        // in case of retries it might happen that the default write index flag is overwritten as the source index
        // was already set to be a read-only index for all associated indices
        schemaUpgradeClient.addAlias(indexAlias, targetIndexName, true);
      }
      schemaUpgradeClient.deleteIndexIfExists(sourceIndex);
      schemaUpgradeClient.deleteTemplateIfExists(sourceTemplateName);
    }
  }

  private void migrateSingleIndex(final SchemaUpgradeClient schemaUpgradeClient) {
    final String indexAlias = getIndexAlias(schemaUpgradeClient);
    final String sourceIndexName = getSourceIndexOrTemplateName(indexAlias);
    final String targetIndexName = schemaUpgradeClient.getIndexNameService().getOptimizeIndexNameWithVersion(index);
    if (!schemaUpgradeClient.indexExists(sourceIndexName)) {
      // if the expected source index is not available anymore there are only two possibilities:
      // 1. it never existed (unexpected edge-case)
      // 2. a previous upgrade run completed this step already
      // in both cases we can try to create/update the target index in a fail-safe way
      log.info(
        "Source index {} was not found, will just create/update the new index {}.", sourceIndexName, targetIndexName
      );
      schemaUpgradeClient.createOrUpdateIndex(index);
    } else {
      // create new index and reindex data to it
      final Set<AliasMetadata> existingAliases = schemaUpgradeClient.getAllAliasesForIndex(sourceIndexName);
      schemaUpgradeClient.setAllAliasesToReadOnly(sourceIndexName, existingAliases);
      schemaUpgradeClient.createOrUpdateIndex(index);
      schemaUpgradeClient.reindex(sourceIndexName, targetIndexName, mappingScript, parameters);
      applyAliasesToIndex(schemaUpgradeClient, targetIndexName, existingAliases);
      applyAdditionalReadOnlyAliasesToIndex(schemaUpgradeClient, targetIndexName);
      // in case of retries it might happen that the default write index flag is overwritten as the source index
      // was already set to be a read-only index for all associated indices
      schemaUpgradeClient.addAlias(indexAlias, targetIndexName, true);
      schemaUpgradeClient.deleteIndexIfExists(sourceIndexName);
    }
  }

  private String getIndexAlias(final SchemaUpgradeClient schemaUpgradeClient) {
    return schemaUpgradeClient.getIndexNameService().getOptimizeIndexAliasForIndex(index.getIndexName());
  }

  private String getSourceIndexOrTemplateName(final String indexAlias) {
    return getOptimizeIndexOrTemplateNameForAliasAndVersion(indexAlias, String.valueOf(index.getVersion() - 1));
  }

  private String getTargetIndexOrTemplateName(final SchemaUpgradeClient schemaUpgradeClient) {
    return schemaUpgradeClient.getIndexNameService().getOptimizeIndexTemplateNameWithVersion(index);
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

  private void applyAdditionalReadOnlyAliasesToIndex(final SchemaUpgradeClient schemaUpgradeClient,
                                                     final String indexName) {
    for (String alias : additionalReadAliases) {
      schemaUpgradeClient.addAlias(
        schemaUpgradeClient.getIndexNameService().getOptimizeIndexAliasForIndex(alias),
        indexName,
        false
      );
    }
  }
}
