package org.camunda.optimize.test.factory.service;

import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

public class MockProcessDefinitionXmlImportServiceFactory implements FactoryBean<ProcessDefinitionXmlImportService> {

  @Override
  public ProcessDefinitionXmlImportService getObject() throws Exception {
    return Mockito.mock(ProcessDefinitionXmlImportService.class);
  }

  @Override
  public Class<?> getObjectType() {
    return ProcessDefinitionXmlImportService.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
