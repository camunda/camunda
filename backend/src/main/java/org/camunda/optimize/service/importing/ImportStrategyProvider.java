package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.provider.ImportStrategyProviderFactory;

public interface ImportStrategyProvider {

  /**
   * Provides a new instance of the import strategy.
   * The decision which strategy is return is made
   * in the {@link ImportStrategyProviderFactory}.
   */
  ImportStrategy getImportStrategyInstance();
}
