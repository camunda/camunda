package org.camunda.optimize.test.unit.factory;

import org.camunda.optimize.service.status.ImportProgressReporter;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

public class MockImportProgressReporterFactory implements FactoryBean<ImportProgressReporter> {

  @Override
  public ImportProgressReporter getObject() throws Exception {
    return Mockito.mock(ImportProgressReporter.class);
  }

  @Override
  public Class<?> getObjectType() {
    return ImportProgressReporter.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
