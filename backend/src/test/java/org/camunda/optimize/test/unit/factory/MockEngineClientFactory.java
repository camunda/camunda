package org.camunda.optimize.test.unit.factory;

import org.camunda.optimize.rest.engine.EngineClientFactory;
import org.camunda.optimize.service.util.Factory;
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
public class MockEngineClientFactory extends EngineClientFactory {

  @Override
  protected Client newClient(String engineAlias) {
    return Mockito.mock(Client.class);
  }
}
