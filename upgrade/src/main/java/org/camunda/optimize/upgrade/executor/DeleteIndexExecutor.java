package org.camunda.optimize.upgrade.executor;

import org.camunda.optimize.upgrade.UpgradeStepExecutor;
import org.camunda.optimize.upgrade.steps.DeleteIndexStep;
import org.elasticsearch.client.RestClient;

/**
 * @author Askar Akhmerov
 */
public class DeleteIndexExecutor
  extends AbstractRESTExecutor
  implements UpgradeStepExecutor<DeleteIndexStep> {

  public DeleteIndexExecutor(RestClient restClient) {
    super(restClient);
  }

  @Override
  public void execute(DeleteIndexStep step) throws Exception {
    deleteIndex(step.getIndexName());
  }
}
