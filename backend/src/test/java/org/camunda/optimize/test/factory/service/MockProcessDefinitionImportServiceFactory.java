package org.camunda.optimize.test.factory.service;

import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

public class MockProcessDefinitionImportServiceFactory implements FactoryBean<ProcessDefinitionImportService> {

  @Override
  public ProcessDefinitionImportService getObject() throws Exception {
    return Mockito.mock(ProcessDefinitionImportService.class);
  }

  @Override
  public Class<?> getObjectType() {
    return ProcessDefinitionImportService.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
