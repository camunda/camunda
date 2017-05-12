package org.camunda.optimize.service.importing.impl;

public interface ImportStrategyProvider {

  /**
   * Provides a new instance of the import strategy.
   * The decision which strategy is return is made
   * in the {@link org.camunda.optimize.service.importing.ImportStrategyProviderFactory}.
   */
  ImportStrategy getImportStrategyInstance();
}
