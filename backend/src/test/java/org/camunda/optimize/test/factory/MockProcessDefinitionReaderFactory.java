package org.camunda.optimize.test.factory;

import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

/**
 * Process definition reader factory used in unit tests in order to allow mocking
 * fetching data from elasticsearch.
 */
public class MockProcessDefinitionReaderFactory implements FactoryBean<ProcessDefinitionReader> {
  @Override
  public ProcessDefinitionReader getObject() throws Exception {
    return Mockito.mock(ProcessDefinitionReader.class);
  }

  @Override
  public Class<?> getObjectType() {
    return ProcessDefinitionReader.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
