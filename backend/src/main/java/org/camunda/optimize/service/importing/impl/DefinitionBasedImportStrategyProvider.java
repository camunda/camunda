package org.camunda.optimize.service.importing.impl;

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
