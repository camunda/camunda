package org.camunda.optimize.upgrade.executor;

import org.camunda.optimize.upgrade.UpgradeStepExecutor;
import org.camunda.optimize.upgrade.steps.CreateIndexStep;
import org.elasticsearch.client.RestClient;

/**
 * @author Askar Akhmerov
 */
public class CreateIndexExecutor
  extends AbstractRESTExecutor
  implements UpgradeStepExecutor<CreateIndexStep> {

  public CreateIndexExecutor(RestClient restClient, String dateFormat) {
    super(restClient, dateFormat);
  }

  @Override
  public void execute(CreateIndexStep step) throws Exception {
    createIndex(step.getIndexName(), step.getMapping());
  }
}
