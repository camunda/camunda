package org.camunda.optimize.upgrade.executor;

import org.camunda.optimize.upgrade.steps.DeleteFieldStep;
import org.elasticsearch.client.RestClient;

/**
 * @author Askar Akhmerov
 */
public class DeleteFieldExecutor
  extends AbstractReindexExecutor<DeleteFieldStep> {


  public DeleteFieldExecutor(RestClient restClient, String dateFormat) {
    super(restClient, dateFormat);
  }
}
