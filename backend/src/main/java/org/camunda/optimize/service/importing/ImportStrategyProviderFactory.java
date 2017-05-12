package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.impl.DefinitionBasedImportStrategyProvider;
import org.camunda.optimize.service.importing.impl.ImportStrategyProvider;
import org.camunda.optimize.service.importing.impl.TotalQuantityBasedImportStrategyProvider;
import org.camunda.optimize.service.util.ConfigurationService;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * This factory decides which import strategy is used during
 * the engine workflow data import.
 */
public class ImportStrategyProviderFactory implements FactoryBean<ImportStrategyProvider> {

  private ImportStrategyProvider instance;

  @Autowired
  private DefinitionBasedImportStrategyProvider definitionBasedImportStrategy;

  @Autowired
  private TotalQuantityBasedImportStrategyProvider totalQuantityBasedImportStrategy;

  @Autowired
  private ConfigurationService configurationService;

  @Override
  public ImportStrategyProvider getObject() throws Exception {
    if (instance == null) {
      setNewImportStrategy();
    }
    return instance;
  }

  private void setNewImportStrategy() {
    if (configurationService.areProcessDefinitionsToImportDefined()) {
      instance = definitionBasedImportStrategy;
    } else {
      instance = totalQuantityBasedImportStrategy;
    }
  }

  @Override
  public Class<?> getObjectType() {
    return ImportStrategyProvider.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
