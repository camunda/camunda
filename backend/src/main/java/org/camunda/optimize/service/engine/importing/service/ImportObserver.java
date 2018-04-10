package org.camunda.optimize.service.engine.importing.service;

public interface ImportObserver {

  void importInProgress(String engineAlias);
  void importIsIdle(String engineAlias);
}
