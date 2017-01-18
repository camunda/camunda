package org.camunda.optimize.service.es;

import org.elasticsearch.client.transport.TransportClient;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

/**
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
