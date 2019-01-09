package org.camunda.optimize.upgrade.steps.schema;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.steps.UpgradeStep;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexNameForAliasAndVersion;

public class UpdateIndexStep implements UpgradeStep {
  private final String typeName;
  private final String sourceVersion;
  private final String targetVersion;
  private final String customMapping;

  public UpdateIndexStep(final String typeName,
                         final String sourceVersion,
                         final String targetVersion) {
    this(typeName, sourceVersion, targetVersion, null);
  }

  public UpdateIndexStep(final String typeName,
                         final String sourceVersion,
                         final String targetVersion,
                         final String customMapping) {
    this.typeName = typeName;
    this.sourceVersion = sourceVersion;
    this.targetVersion = targetVersion;
    this.customMapping = customMapping;
  }

  @Override
  public void execute(final ESIndexAdjuster esIndexAdjuster) {
    final String indexAlias = getOptimizeIndexAliasForType(typeName);
    final String sourceIndexName = getOptimizeIndexNameForAliasAndVersion(indexAlias, sourceVersion);
    final String targetIndexName = getOptimizeIndexNameForAliasAndVersion(indexAlias, targetVersion);

    String indexMappings = esIndexAdjuster.getIndexMappings(sourceIndexName);
    indexMappings = customMapping != null ? customMapping : indexMappings;

    // create new index and reindex data to it
    esIndexAdjuster.createIndex(targetIndexName, indexMappings);
    esIndexAdjuster.reindex(sourceIndexName, targetIndexName, typeName, typeName);
    esIndexAdjuster.addAlias(targetIndexName, indexAlias);
    esIndexAdjuster.deleteIndex(sourceIndexName);
  }

}
