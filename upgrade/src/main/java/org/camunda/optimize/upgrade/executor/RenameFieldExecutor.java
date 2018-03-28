package org.camunda.optimize.upgrade.executor;

import org.camunda.optimize.upgrade.steps.RenameFieldStep;
import org.elasticsearch.client.RestClient;

/**
 * @author Askar Akhmerov
 */
public class RenameFieldExecutor
  extends AbstractReindexExecutor<RenameFieldStep> {

  public RenameFieldExecutor(RestClient restClient, String dateFormat) {
    super(restClient, dateFormat);
  }
}
