package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.service.util.ImportJobExecutor;

public interface EngineImportMediator {

  void importNextPage();

  long getBackoffTimeInMs();

  void resetBackoff();

  boolean canImport();

  ImportJobExecutor getImportJobExecutor();
}