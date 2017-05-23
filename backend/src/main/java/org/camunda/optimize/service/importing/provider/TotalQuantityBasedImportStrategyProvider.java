package org.camunda.optimize.service.importing.provider;

import org.camunda.optimize.service.importing.ImportStrategy;
import org.camunda.optimize.service.importing.ImportStrategyProvider;
import org.camunda.optimize.service.importing.strategy.TotalQuantityBasedImportStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class TotalQuantityBasedImportStrategyProvider implements ImportStrategyProvider {

  @Autowired
  private ApplicationContext applicationContext;

  @Override
  public ImportStrategy getImportStrategyInstance() {
    return (TotalQuantityBasedImportStrategy) applicationContext.getBean("totalQuantityBasedImportStrategy");
  }
}
