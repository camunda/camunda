package org.camunda.optimize.test.factory;

import org.camunda.optimize.service.importing.provider.DefinitionBasedImportStrategyProvider;
import org.camunda.optimize.service.importing.ImportStrategyProvider;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

public class MockImportStrategyProviderFactory implements FactoryBean<ImportStrategyProvider> {

  @Autowired
  private DefinitionBasedImportStrategyProvider importStrategyProvider;

  @Override
  public ImportStrategyProvider getObject() throws Exception {
    return importStrategyProvider;
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
