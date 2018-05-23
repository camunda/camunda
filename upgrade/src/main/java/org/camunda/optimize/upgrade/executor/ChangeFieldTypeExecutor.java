package org.camunda.optimize.upgrade.executor;

import org.camunda.optimize.upgrade.steps.ChangeFieldTypeStep;
import org.elasticsearch.client.RestClient;


public class ChangeFieldTypeExecutor
  extends AbstractReindexExecutor<ChangeFieldTypeStep> {


  public ChangeFieldTypeExecutor(RestClient restClient, String dateFormat) {
    super(restClient, dateFormat);
  }
}
