package org.camunda.optimize.test.factory;

import org.camunda.optimize.service.es.reader.BranchAnalysisReader;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

/**
 * Branch analysis reader factory used in unit tests in order to allow mocking
 * fetching data from elasticsearch.
 */
public class MockBranchAnalysisReaderFactory implements FactoryBean<BranchAnalysisReader> {
  @Override
  public BranchAnalysisReader getObject() throws Exception {
    return Mockito.mock(BranchAnalysisReader.class);
  }

  @Override
  public Class<?> getObjectType() {
    return BranchAnalysisReader.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
