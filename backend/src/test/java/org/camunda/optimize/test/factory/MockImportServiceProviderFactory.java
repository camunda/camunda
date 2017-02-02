package org.camunda.optimize.test.factory;

import org.camunda.optimize.service.importing.ImportServiceProvider;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

public class MockImportServiceProviderFactory implements FactoryBean<ImportServiceProvider> {

  @Override
  public ImportServiceProvider getObject() throws Exception {
    return Mockito.mock(ImportServiceProvider.class);
  }

  @Override
  public Class<?> getObjectType() {
    return ImportServiceProvider.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
