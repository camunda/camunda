package org.camunda.optimize.test.factory;

import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

/**
 * This factory would instantiate a JAX-RS client and put a mockito
 * spy on it, so that you can override behavior of client in some unit test
 * if you need it.
 *
 * @author Askar Akhmerov
 */
public class MockEngineClientFactory implements FactoryBean<Client> {
  private Client instance;

  @Override
  public Client getObject() throws Exception {
    if (instance == null) {
      instance = Mockito.mock(Client.class);
    }
    return instance;
  }

  @Override
  public Class<?> getObjectType() {
    return Client.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
