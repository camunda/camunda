/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.schema;

import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.elasticsearch.cluster.metadata.AliasMetaData;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameService.getOptimizeIndexNameForAliasAndVersion;

public class UpdateIndexStep implements UpgradeStep {
  private final IndexMappingCreator index;
  private final String mappingScript;
  // expected suffix: hyphen and numbers at end of index name
  private final Pattern indexSuffixPattern = Pattern.compile("-\\d+$");

  public UpdateIndexStep(final IndexMappingCreator index, final String mappingScript) {
    this.index = index;
    this.mappingScript = mappingScript;
  }

  @Override
  public void execute(final ESIndexAdjuster esIndexAdjuster) {
    String indexName = index.getIndexName();
    int targetVersion = index.getVersion();
    final OptimizeIndexNameService indexNameService = esIndexAdjuster.getIndexNameService();
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
      esIndexAdjuster.createOrUpdateTemplateWithoutAliases(index, indexAlias);
      final Map<String, Set<AliasMetaData>> indexAliasMap = esIndexAdjuster.getAliasMap(indexAlias);
      for (String sourceIndex : indexAliasMap.keySet()) {
        String suffix = "";
        String sourceIndexNameWithSuffix;
        Matcher suffixMatcher = indexSuffixPattern.matcher(sourceIndex);

        if (suffixMatcher.find()) {
          // sourceIndex is already suffixed
          suffix = sourceIndex.substring(sourceIndex.lastIndexOf("-"));
          sourceIndexNameWithSuffix = sourceIndexName + suffix;
        } else {
          // sourceIndex is not yet suffixed, use default suffix
          suffix = index.getIndexNameSuffix();
          sourceIndexNameWithSuffix = sourceIndexName;
        }
        final String targetIndexNameWithSuffix = targetIndexName + suffix;

        esIndexAdjuster.createIndexFromTemplate(targetIndexNameWithSuffix);
        esIndexAdjuster.reindex(sourceIndexNameWithSuffix, targetIndexNameWithSuffix, mappingScript);
        esIndexAdjuster.setAllAliasesToReadOnly(sourceIndexNameWithSuffix);
        for (AliasMetaData alias : indexAliasMap.get(sourceIndex)) {
          esIndexAdjuster.addAlias(alias.getAlias(), targetIndexNameWithSuffix, alias.writeIndex());
        }
        esIndexAdjuster.deleteIndex(sourceIndexNameWithSuffix);
      }
    } else {
      // create new index and reindex data to it
      esIndexAdjuster.createIndex(index);
      esIndexAdjuster.reindex(sourceIndexName, targetIndexName, mappingScript);
      esIndexAdjuster.setAllAliasesToReadOnly(sourceIndexName);
      esIndexAdjuster.addAlias(index);
      esIndexAdjuster.deleteIndex(sourceIndexName);
    }
  }
}
