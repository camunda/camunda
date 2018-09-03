package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;

public abstract class ReindexStep implements UpgradeStep {

  protected void transformCompleteMapping(ESIndexAdjuster esIndexAdjuster) {
    String tempTypeName = esIndexAdjuster.getTempTypeName(getInitialTypeName());

    String indexMappings = esIndexAdjuster.getIndexMappings(getInitialTypeName());
    indexMappings = adjustIndexMappings(indexMappings);

    esIndexAdjuster.createIndex(tempTypeName, indexMappings);
    esIndexAdjuster.reindex(
      getInitialTypeName(),
      tempTypeName,
      getInitialTypeName(),
      getFinalTypeName(),
      getMappingScript()
    );
    esIndexAdjuster.deleteIndex(getInitialTypeName());
    esIndexAdjuster.createIndex(getFinalTypeName(), indexMappings);
    esIndexAdjuster.reindex(tempTypeName, getFinalTypeName(), getFinalTypeName(), getFinalTypeName());
    esIndexAdjuster.deleteIndex(tempTypeName);
  }

  /**
   * Return the type name before the upgrade has been executed.
   */
  protected abstract String getInitialTypeName();

  /**
   * Uses the the old mapping to perform the adjustments
   * that are defined in the step and returns the new mapping.
   * <p>
   * Context: each step that needs to reindex the data has
   * to perform some adjustments on the mapping/schema of
   * a specific index.
   */
  protected abstract String adjustIndexMappings(String oldMapping);

  /**
   * Returns a painless script (Elasticsearch script language) to map
   * data from the old mapping to the new one.
   */
  protected abstract String getMappingScript();

  /**
   * Return the type name after the upgrade has been executed.
   */
  protected abstract String getFinalTypeName();
}
