package org.camunda.optimize;

import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;

/**
 * @author Askar Akhmerov
 */
public interface CamundaOptimize {

  ImportJobExecutor getImportJobExecutor();

  ImportServiceProvider getImportServiceProvider();

  void startImportSchedulers();

  void disableImportSchedulers();
}
