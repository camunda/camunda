package org.camunda.optimize.test.unit.factory;

import org.camunda.optimize.rest.engine.EngineClientFactory;
import org.mockito.Mockito;

import javax.ws.rs.client.Client;

/**
 * This factory would instantiate a JAX-RS client and put a mockito
 * spy on it, so that you can override behavior of client in some unit test
 * if you need it.
 *
 * @author Askar Akhmerov
 */
public class MockEngineClientFactory extends EngineClientFactory {

  @Override
  protected Client newInstance(String engineAlias) {
    return Mockito.mock(Client.class);
  }
}
