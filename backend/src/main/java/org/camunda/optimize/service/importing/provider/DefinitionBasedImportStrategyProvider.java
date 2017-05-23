package org.camunda.optimize.service.importing.provider;

import org.camunda.optimize.service.importing.ImportStrategy;
import org.camunda.optimize.service.importing.ImportStrategyProvider;
import org.camunda.optimize.service.importing.impl.DefinitionBasedImportStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class DefinitionBasedImportStrategyProvider implements ImportStrategyProvider {

  @Autowired
  private ApplicationContext applicationContext;

  @Override
  public ImportStrategy getImportStrategyInstance() {
    return (DefinitionBasedImportStrategy) applicationContext.getBean("definitionBasedImportStrategy");
  }
}
