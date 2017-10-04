package org.camunda.optimize.test.it.factory;

import org.camunda.optimize.rest.engine.EngineClientFactory;
import org.mockito.Mockito;

import javax.ws.rs.client.Client;

public class SpyEngineClientFactory extends EngineClientFactory {

  protected Client newInstance(String engineAlias) {
    return Mockito.spy(super.newInstance(engineAlias));
  }
}