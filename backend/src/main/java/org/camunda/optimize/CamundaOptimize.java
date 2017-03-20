package org.camunda.optimize;

import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.ImportServiceProvider;

/**
 * @author Askar Akhmerov
 */
public interface CamundaOptimize {

  ImportJobExecutor getImportJobExecutor();

  ImportServiceProvider getImportServiceProvider();

  void startImportScheduler();

  void disableImportScheduler();
}
