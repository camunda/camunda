package org.camunda.optimize.test.unit.factory;

import org.camunda.optimize.service.es.ElasticSearchSchemaInitializer;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

/**
 * Schema initializer factory used in unit tests in order to allow mocking
 * the elastic search schema creation
 *
 * @author Johannes Heinemann
 */
public class MockSchemaInitializerFactory implements FactoryBean<ElasticSearchSchemaInitializer> {
  @Override
  public ElasticSearchSchemaInitializer getObject() throws Exception {
    return Mockito.mock(ElasticSearchSchemaInitializer.class);
  }

  @Override
  public Class<?> getObjectType() {
    return ElasticSearchSchemaInitializer.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
