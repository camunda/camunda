package org.camunda.optimize.service.engine.importing.service.mediator;

public interface EngineImportMediator {

  void importNextPage();

  long getBackoffTimeInMs();

  void resetBackoff();

  boolean canImport();
}
