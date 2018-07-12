package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.elasticsearch.index.query.QueryBuilder;


public class UpdateDataStep implements UpgradeStep {
  private final String typeName;
  private final QueryBuilder query;
  private final String updateScript;

  public UpdateDataStep(String typeName, QueryBuilder query, String updateScript) {
    this.typeName = typeName;
    this.query = query;
    this.updateScript = updateScript;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    ESIndexAdjuster.updateData(typeName, query, updateScript);
  }

}
