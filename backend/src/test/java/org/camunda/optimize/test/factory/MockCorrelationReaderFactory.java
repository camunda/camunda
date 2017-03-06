package org.camunda.optimize.test.factory;

import org.camunda.optimize.service.es.reader.CorrelationReader;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

/**
 * Correlation reader factory used in unit tests in order to allow mocking
 * fetching data from elasticsearch.
 */
public class MockCorrelationReaderFactory implements FactoryBean<CorrelationReader> {
  @Override
  public CorrelationReader getObject() throws Exception {
    return Mockito.mock(CorrelationReader.class);
  }

  @Override
  public Class<?> getObjectType() {
    return CorrelationReader.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
