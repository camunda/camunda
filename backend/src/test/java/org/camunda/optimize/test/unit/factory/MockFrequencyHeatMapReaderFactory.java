package org.camunda.optimize.test.unit.factory;

import org.camunda.optimize.service.es.reader.FrequencyHeatMapReader;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

/**
 * Heat map reader factory used in unit tests in order to allow mocking
 * fetching data from elasticsearch.
 */
public class MockFrequencyHeatMapReaderFactory implements FactoryBean<FrequencyHeatMapReader> {
  @Override
  public FrequencyHeatMapReader getObject() throws Exception {
    return Mockito.mock(FrequencyHeatMapReader.class);
  }

  @Override
  public Class<?> getObjectType() {
    return FrequencyHeatMapReader.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
