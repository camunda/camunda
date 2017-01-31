package org.camunda.optimize.test.factory;

import org.elasticsearch.client.transport.TransportClient;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

/**
 * Transport client factory used in unit tests in order to allow mocking
 * requests towards elasticsearch
 *
 * @author Askar Akhmerov
 */
public class MockTransportClientFactory implements FactoryBean<TransportClient> {
  @Override
  public TransportClient getObject() throws Exception {
    return Mockito.mock(TransportClient.class);
  }

  @Override
  public Class<?> getObjectType() {
    return TransportClient.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
