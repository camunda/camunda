package org.camunda.optimize.upgrade.executor;

import org.camunda.optimize.upgrade.steps.RenameIndexStep;
import org.elasticsearch.client.RestClient;


public class RenameIndexExecutor
  extends AbstractReindexExecutor<RenameIndexStep> {

  public RenameIndexExecutor(RestClient restClient, String dateFormat) {
    super(restClient, dateFormat);
  }
}
