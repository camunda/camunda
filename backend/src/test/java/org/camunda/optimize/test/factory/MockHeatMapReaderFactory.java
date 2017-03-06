package org.camunda.optimize.test.factory;

import org.camunda.optimize.service.es.reader.HeatMapReader;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

/**
 * Heat map reader factory used in unit tests in order to allow mocking
 * fetching data from elasticsearch.
 */
public class MockHeatMapReaderFactory implements FactoryBean<HeatMapReader> {
  @Override
  public HeatMapReader getObject() throws Exception {
    return Mockito.mock(HeatMapReader.class);
  }

  @Override
  public Class<?> getObjectType() {
    return HeatMapReader.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
