package org.camunda.optimize.test.factory;

import org.camunda.optimize.service.es.reader.DurationHeatMapReader;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

/**
 * Heat map reader factory used in unit tests in order to allow mocking
 * fetching data from elasticsearch.
 */
public class MockDurationHeatMapReaderFactory implements FactoryBean<DurationHeatMapReader> {
  @Override
  public DurationHeatMapReader getObject() throws Exception {
    return Mockito.mock(DurationHeatMapReader.class);
  }

  @Override
  public Class<?> getObjectType() {
    return DurationHeatMapReader.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
