package org.camunda.optimize.upgrade.steps.schema;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.steps.UpgradeStep;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexNameForAliasAndVersion;

public class CreateIndexAliasForExistingIndexStep implements UpgradeStep {
  private final String typeName;
  private final String targetVersion;
  private final String customMapping;

  public CreateIndexAliasForExistingIndexStep(final String typeName,
                                              final String targetVersion) {
    this.targetVersion = targetVersion;
    this.typeName = typeName;
    this.customMapping = null;
  }

  public CreateIndexAliasForExistingIndexStep(String typeName, String targetVersion, String customMapping) {
    this.typeName = typeName;
    this.targetVersion = targetVersion;
    this.customMapping = customMapping;
  }

  @Override
  public void execute(final ESIndexAdjuster esIndexAdjuster) {
    final String sourceIndexAlias = getOptimizeIndexAliasForType(typeName);
    final String targetIndexAlias = getOptimizeIndexAliasForType(typeName);
    // when aliases are created there is no source index version yet
    final String sourceIndexName = getOptimizeIndexNameForAliasAndVersion(sourceIndexAlias, null);
    final String targetIndexName = getOptimizeIndexNameForAliasAndVersion(targetIndexAlias, targetVersion);

    String indexMappings = esIndexAdjuster.getIndexMappings(sourceIndexName);
    indexMappings = customMapping != null ? customMapping : indexMappings;

    // create new index and reindex data to it
    esIndexAdjuster.createIndex(targetIndexName, indexMappings);
    esIndexAdjuster.reindex(sourceIndexName, targetIndexName, typeName, typeName);

    // delete the old index and create the alias
    esIndexAdjuster.deleteIndex(sourceIndexName);
    esIndexAdjuster.addAlias(targetIndexName, targetIndexAlias);
  }

}
