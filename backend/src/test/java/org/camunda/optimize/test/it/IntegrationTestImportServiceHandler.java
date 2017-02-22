package org.camunda.optimize.test.it;

import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.ImportServiceHandler;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Import service handler used in integration test. This is
 * necessary as we want the {@link ImportJobExecutor} to finish all import jobs
 * before we can continue to check the results of the import in the tests.
 *
 * @author Johannes Heinemann
 */
public class IntegrationTestImportServiceHandler extends ImportServiceHandler {

  @Autowired
  private ImportJobExecutor importJobExecutor;

  @Override
  public void executeProcessEngineImport() {
    importJobExecutor.startExecutingImportJobs();
    super.executeProcessEngineImport();
    importJobExecutor.stopExecutingImportJobs();
  }

}
