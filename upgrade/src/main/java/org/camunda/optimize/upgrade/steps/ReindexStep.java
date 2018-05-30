package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;

public abstract class ReindexStep implements UpgradeStep {

  protected void transformCompleteMapping(ESIndexAdjuster ESIndexAdjuster) {
    String tempIndexName = ESIndexAdjuster.getTempIndexName(getInitialIndexName());

    String enhancedMapping = ESIndexAdjuster.preProcess(getMappingAndSettings());
    ESIndexAdjuster.createIndex(tempIndexName, enhancedMapping);
    ESIndexAdjuster.reindex(getInitialIndexName(), tempIndexName, getMappingScript());
    ESIndexAdjuster.deleteIndex(getInitialIndexName());
    ESIndexAdjuster.createIndex(getFinalIndexName(), enhancedMapping);
    ESIndexAdjuster.reindex(tempIndexName, getFinalIndexName());
    ESIndexAdjuster.deleteIndex(tempIndexName);
  }

  /**
   * Return the index name before the upgrade has been executed.
   */
  protected abstract String getInitialIndexName();

  /**
   * Return the elasticsearch mapping structure and index settings
   * after the index has been upgraded.
   *
   * Info: mapping in elasticsearch would be the equivalent of
   * a schema in a classical SQL database.
   */
  protected abstract String getMappingAndSettings();

  /**
   * Returns a painless script (Elasticsearch script language) to map
   * data from the old mapping to the new one.
   */
  protected abstract String getMappingScript();

  /**
   * Return the index name after the upgrade has been executed.
   */
  protected abstract String getFinalIndexName();
}
